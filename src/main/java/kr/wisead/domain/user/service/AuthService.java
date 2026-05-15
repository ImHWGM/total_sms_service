package kr.wisead.domain.user.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CommonUtils;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.common.util.MemberUtil;
import kr.wisead.domain.email.service.EmailAuthService;
import kr.wisead.domain.payment.entity.UserServiceRate;
import kr.wisead.domain.payment.service.StandardRateService;
import kr.wisead.domain.payment.service.WalletService;
import kr.wisead.domain.sms.service.SmsAuthService;
import kr.wisead.domain.user.dto.LoginRequest;
import kr.wisead.domain.user.dto.LoginResponse;
import kr.wisead.domain.user.dto.SignUpRequest;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.UserMapper;
import kr.wisead.mapper.primary.UserServiceRateMapper;
import kr.wisead.security.jwt.JwtTokenProvider;
import kr.wisead.security.sessionkey.SessionKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 인증 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserMapper userMapper;
  private final WalletService walletService;
  private final UserServiceRateMapper userServiceRateMapper;
  private final StandardRateService standardRateService;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final EmailAuthService emailAuthService;
  private final SmsAuthService smsAuthService;
  private final SessionKeyService sessionKeyService;

  /**
   * 로그인 - 채널(EMAIL/SMS) 분기 + 휴면 스킵 정책 적용.
   *
   * <p>plan v5 §4 Phase C-1-b / Phase D.
   *
   * <ul>
   *   <li>{@code defaultTwoFactorMethod} 가 "SMS" 면 SmsAuthService 로 OTP 발송, 아니면 EMAIL (기본).
   *   <li>{@code isLoggedInToday()} 가 true 면 OTP 스킵 — EMAIL/SMS 공통 적용 (#v3-2).
   *   <li>OTP 발송 시 sessionKey 발급 (Phase D) — 응답 {@code sessionKey} 필드에 포함.
   *   <li>OTP 코드 제출 시 request.sessionKey 로 validate → 검증 성공 시 invalidate (재사용 방지).
   * </ul>
   */
  @Transactional
  public LoginResponse login(LoginRequest request) {
    log.info("[로그인 시도] userId={}", request.getUserId());

    // 1. 사용자 조회
    User user =
        userMapper
            .findByUserId(request.getUserId())
            .orElseThrow(
                () -> new BusinessException(ErrorCode.LOGIN_FAILED, "아이디 또는 비밀번호가 일치하지 않습니다."));

    // 2. 계정 잠금 확인
    if (user.isLocked()) {
      throw new BusinessException(
          ErrorCode.ACCOUNT_LOCKED, "로그인 실패 횟수 초과로 계정이 잠겼습니다. 관리자에게 문의하세요.");
    }

    // 3. 계정 상태 확인 (Java 21 Switch Expression)
    if (!user.isActive()) {
      String message =
          switch (user.getStatus()) {
            case "미승인" -> "승인 대기 중인 계정입니다.";
            case "탈퇴" -> "탈퇴된 계정입니다.";
            default -> "비활성화된 계정입니다.";
          };
      throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, message);
    }

    // 4. 비밀번호 확인
    if (!passwordEncoder.matches(request.getUserPass(), user.getUserPass())) {
      userMapper.increaseLoginFailureCnt(request.getUserId());
      throw new BusinessException(ErrorCode.LOGIN_FAILED, "아이디 또는 비밀번호가 일치하지 않습니다.");
    }

    // 5. OTP 코드 검증 흐름 (1차 인증 완료 후 채널 OTP)
    String emailCode = request.getEmailCode();
    String channel = resolveChannel(user);

    if (StringUtils.hasText(emailCode)) {
      // 5-A. OTP 코드 제출: sessionKey 유효성 검증 후 OTP 검증 (Phase D)
      sessionKeyService.validate(request.getSessionKey());
      verifyOtpInternal(user.getSeq(), emailCode, channel);
      sessionKeyService.invalidate(request.getSessionKey()); // 검증 성공 → sessionKey 폐기 (재사용 방지)
      log.info("[OTP 인증 성공] userId={}, channel={}", user.getUserId(), channel);
    } else {
      // 5-B. OTP 코드 미제출: 휴면 스킵 또는 OTP 발송 분기
      if (isLoggedInToday(user)) {
        // 휴면 스킵 (EMAIL/SMS 공통, #v3-2): JWT 즉시 발급으로 진행
        log.info("[OTP 스킵] userId={}, 오늘 이미 로그인함, channel={}", user.getUserId(), channel);
      } else {
        // OTP 발송 필요: 채널 분기 + sessionKey 발급 (Phase D)
        String sessionKey = sessionKeyService.issue(user.getSeq());
        return sendOtpForLogin(user, channel, sessionKey);
      }
    }

    // 6. 로그인 성공 처리
    userMapper.updateLastLogin(request.getUserId());

    // 7. JWT 토큰 생성 (사용자 이름 포함)
    Authentication authentication = createAuthentication(user);
    String accessToken = jwtTokenProvider.createAccessToken(authentication, user.getPerson());
    String refreshToken = jwtTokenProvider.createRefreshToken(authentication, user.getPerson());

    log.info("[로그인 성공] userId={}, channel={}", user.getUserId(), channel);

    return LoginResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(jwtTokenProvider.getAccessTokenValidityInSeconds())
        .user(
            LoginResponse.UserInfo.builder()
                .seq(user.getSeq())
                .userId(user.getUserId())
                .corpName(user.getCorpName())
                .person(decryptField(user.getPerson()))
                .email(decryptField(user.getEmail()))
                .userLevel(user.getUserLevel())
                .status(user.getStatus())
                .build())
        .build();
  }

  /**
   * 로그인 OTP 채널 즉시 전환 (사용자가 "이메일로 받기" / "휴대폰으로 받기" 클릭).
   *
   * <p>plan v5 §4 Phase C-1-c / Phase D. 채널 변경 시 반대 채널의 OTP 는 무효화하고 (cross-channel cleanup), 새 채널의
   * OTP 를 즉시 발송한다. sessionKey 로 userId 를 검증하고 switchCount 를 증가시킨다.
   */
  @Transactional
  public LoginResponse switchChannel(String sessionKey, String targetChannel) {
    Integer userId = sessionKeyService.validate(sessionKey);
    sessionKeyService.recordChannelSwitch(sessionKey);

    User user =
        userMapper
            .findBySeq(userId)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

    // 기존 OTP 모두 무효화 (cross-channel cleanup)
    emailAuthService.invalidate(userId);
    smsAuthService.invalidate(userId);

    if ("SMS".equals(targetChannel)) {
      if (!StringUtils.hasText(user.getLoginPhone())) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "등록된 휴대폰 번호가 없습니다.");
      }
      String loginPhone = decryptField(user.getLoginPhone());
      smsAuthService.sendVerificationCode(userId, loginPhone);
      log.info("[채널 전환 → SMS] userId={}, phone={}", userId, CommonUtils.maskingPhone(loginPhone));
      return LoginResponse.builder()
          .sessionKey(sessionKey)
          .channel("SMS")
          .maskedPhone(CommonUtils.maskingPhone(loginPhone))
          .availableChannels(getAvailableChannels(user))
          .build();
    } else if ("EMAIL".equals(targetChannel)) {
      String email = decryptField(user.getEmail());
      if (!StringUtils.hasText(email)) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "등록된 이메일이 없습니다.");
      }
      emailAuthService.sendVerificationCode(userId, email);
      log.info("[채널 전환 → EMAIL] userId={}, email={}", userId, CommonUtils.maskingEmailShort(email));
      return LoginResponse.builder()
          .sessionKey(sessionKey)
          .channel("EMAIL")
          .maskedEmail(CommonUtils.maskingEmailShort(email))
          .availableChannels(getAvailableChannels(user))
          .build();
    } else {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "지원하지 않는 채널입니다.");
    }
  }

  /**
   * 현재 채널의 OTP 발송 (sendVerificationCode 중복 방지 포함) + sessionKey 응답 포함 (Phase D).
   *
   * <p>plan v5 §4 Phase C-1-b / Phase D 보조 메서드.
   */
  private LoginResponse sendOtpForLogin(User user, String channel, String sessionKey) {
    if ("SMS".equals(channel)) {
      if (!StringUtils.hasText(user.getLoginPhone())) {
        throw new BusinessException(
            ErrorCode.INVALID_INPUT_VALUE, "SMS 인증 채널이 설정되어 있으나 휴대폰 번호가 등록되지 않았습니다.");
      }
      String loginPhone = decryptField(user.getLoginPhone());
      var status = smsAuthService.getVerificationStatus(user.getSeq());
      if (!status.codeSent()) {
        log.info(
            "[SMS OTP 발송 시도] userId={}, phone={}",
            user.getUserId(),
            CommonUtils.maskingPhone(loginPhone));
        smsAuthService.sendVerificationCode(user.getSeq(), loginPhone);
        log.info(
            "[SMS OTP 발송 성공] userId={}, phone={}",
            user.getUserId(),
            CommonUtils.maskingPhone(loginPhone));
      } else {
        log.info(
            "[SMS OTP 이미 발송됨 (재사용)] userId={}, phone={}",
            user.getUserId(),
            CommonUtils.maskingPhone(loginPhone));
      }
      return LoginResponse.builder()
          .sessionKey(sessionKey)
          .channel("SMS")
          .maskedPhone(CommonUtils.maskingPhone(loginPhone))
          .availableChannels(getAvailableChannels(user))
          .build();
    } else {
      // EMAIL (기본)
      String email = decryptField(user.getEmail());
      if (!StringUtils.hasText(email)) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "등록된 이메일이 없습니다. 관리자에게 문의하세요.");
      }
      var status = emailAuthService.getVerificationStatus(user.getSeq());
      if (!status.codeSent()) {
        log.info(
            "[EMAIL OTP 발송 시도] userId={}, email={}",
            user.getUserId(),
            CommonUtils.maskingEmailShort(email));
        try {
          emailAuthService.sendVerificationCode(user.getSeq(), email);
          log.info(
              "[EMAIL OTP 발송 성공] userId={}, email={}",
              user.getUserId(),
              CommonUtils.maskingEmailShort(email));
        } catch (BusinessException e) {
          log.warn(
              "[EMAIL OTP 발송 실패] userId={}, email={}, 사유={}",
              user.getUserId(),
              CommonUtils.maskingEmailShort(email),
              e.getMessage());
          throw e;
        }
      } else {
        log.info(
            "[EMAIL OTP 이미 발송됨 (재사용)] userId={}, email={}",
            user.getUserId(),
            CommonUtils.maskingEmailShort(email));
      }
      return LoginResponse.builder()
          .sessionKey(sessionKey)
          .channel("EMAIL")
          .maskedEmail(CommonUtils.maskingEmailShort(email))
          .availableChannels(getAvailableChannels(user))
          .build();
    }
  }

  /**
   * OTP 코드 검증 (채널 분기).
   *
   * <p>plan v5 §4 Phase C-1-d. login() 내부에서 사용하는 비공개 헬퍼.
   */
  private void verifyOtpInternal(Integer userId, String code, String channel) {
    if ("SMS".equals(channel)) {
      smsAuthService.verifyCode(userId, code);
    } else {
      if (!emailAuthService.verifyCode(userId, code)) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드가 일치하지 않습니다.");
      }
    }
  }

  /** 사용자의 현재 OTP 채널 결정 (default_two_factor_method 기반, 미설정 시 EMAIL). */
  private String resolveChannel(User user) {
    return "SMS".equals(user.getDefaultTwoFactorMethod()) ? "SMS" : "EMAIL";
  }

  /** 사용자가 선택 가능한 채널 목록 (EMAIL 은 항상, SMS 는 login_phone 등록 시). */
  private List<String> getAvailableChannels(User user) {
    List<String> channels = new ArrayList<>();
    channels.add("EMAIL");
    if (StringUtils.hasText(user.getLoginPhone())) {
      channels.add("SMS");
    }
    return channels;
  }

  /** 오늘 로그인한 적이 있는지 확인 */
  private boolean isLoggedInToday(User user) {
    if (user.getLastLogin() == null) {
      return false;
    }
    LocalDate lastLoginDate = user.getLastLogin().toLocalDate();
    LocalDate today = LocalDate.now();
    return lastLoginDate.equals(today);
  }

  /** 로그인 이메일 인증 코드 재발송 - ID/PW 검증 후 이메일 인증 코드 재발송 */
  @Transactional(readOnly = true)
  public LoginResponse resendLoginEmailCode(LoginRequest request) {
    // 1. 사용자 조회
    User user =
        userMapper
            .findByUserId(request.getUserId())
            .orElseThrow(
                () -> new BusinessException(ErrorCode.LOGIN_FAILED, "아이디 또는 비밀번호가 일치하지 않습니다."));

    // 2. 계정 잠금 확인
    if (user.isLocked()) {
      throw new BusinessException(
          ErrorCode.ACCOUNT_LOCKED, "로그인 실패 횟수 초과로 계정이 잠겼습니다. 관리자에게 문의하세요.");
    }

    // 3. 계정 상태 확인
    if (!user.isActive()) {
      throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "비활성화된 계정입니다.");
    }

    // 4. 비밀번호 확인
    if (!passwordEncoder.matches(request.getUserPass(), user.getUserPass())) {
      throw new BusinessException(ErrorCode.LOGIN_FAILED, "아이디 또는 비밀번호가 일치하지 않습니다.");
    }

    // 5. 이메일 확인
    String email = decryptField(user.getEmail());
    if (!StringUtils.hasText(email)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "등록된 이메일이 없습니다. 관리자에게 문의하세요.");
    }

    // 6. 이메일 인증 코드 재발송 (기존 코드 무효화 후 새 코드 발송, Phase B-0-5: key=user.seq)
    emailAuthService.resendVerificationCode(user.getSeq(), email);
    log.info(
        "이메일 인증 코드 재발송: userId={}, email={}",
        user.getUserId(),
        CommonUtils.maskingEmailShort(email));

    return LoginResponse.builder()
        .channel("EMAIL")
        .maskedEmail(CommonUtils.maskingEmailShort(email))
        .availableChannels(getAvailableChannels(user))
        .build();
  }

  /** 회원가입 */
  @Transactional
  public void signUp(SignUpRequest request) {
    // 1. 비밀번호 확인
    if (!request.isPasswordMatched()) {
      throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
    }

    // 2. 아이디 중복 확인
    if (userMapper.existsByUserId(request.getUserId())) {
      throw new BusinessException(ErrorCode.DUPLICATE_EMAIL, "이미 사용 중인 아이디입니다.");
    }

    // 3. 이메일 중복 확인
    if (userMapper.existsByEmail(request.getEmail())) {
      throw new BusinessException(ErrorCode.DUPLICATE_EMAIL, "이미 등록된 이메일입니다.");
    }

    // 4-1. 연락처 암호화 처리
    String encryptedPhone = null;
    try {
      String phone = request.getPhone().replace("-", "");
      encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(phone));
    } catch (Exception e) {
      log.error("연락처 암호화 실패: {}", e.getMessage());
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "연락처 암호화에 실패했습니다.");
    }
    // 4-2. 담당자 암호화 처리 -> 비밀번호 찾기에서 담당자 암호화 처리가 들어가기에 회원가입시에도 있어야 함.
    String encryptedPerson = null;
    try {
      String person = request.getPerson();
      encryptedPerson = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(person));
    } catch (Exception e) {
      log.error("담당자 암호화 실패: {}", e.getMessage());
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "담당자 암호화에 실패했습니다.");
    }
    // 4-2. 고유한 상점코드 생성
    String storeCode = null;
    int attempts = 0;
    while (attempts < 5) {
      String tempCode = MemberUtil.generateStoreCode();
      if (!userMapper.existsByStoreCode(tempCode)) {
        storeCode = tempCode;
        break;
      }
      attempts++;
      log.warn("상점코드 중복 발생 (시도 {}회): {}", attempts, tempCode);
    }

    // 5. 사용자 생성
    User user =
        User.builder()
            .userId(request.getUserId())
            .userPass(passwordEncoder.encode(request.getUserPass()))
            .corpName(request.getCorpName())
            .corpAddr(request.getCorpAddr())
            .bizNum(request.getBizNum())
            .bizTel(request.getBizTel())
            //            .person(request.getPerson())
            .person(encryptedPerson)
            .phone(encryptedPhone)
            .email(request.getEmail())
            .userLevel(1) // 일반 회원
            .useYn("Y")
            .status("미승인") // 가입 후 관리자 승인 필요
            .regId(request.getUserId())
            .storeCode(storeCode) // 스토어코드 추가
            .loginPhone(null) // SMS 2FA 미등록 상태로 가입
            .defaultTwoFactorMethod("EMAIL") // 기본 2FA: EMAIL
            .build();

    userMapper.insert(user);

    // 6. 지갑 초기화 (INSERT 후 user.seq에 자동 생성된 키가 주입됨)
    Integer userSeq = user.getSeq();
    walletService.initializeWallet(userSeq);

    // 7. 사용자별 서비스 단가 초기화 (standard_rate 기준, VAT 포함)
    LocalDate today = LocalDate.now();

    BigDecimal surveyRate = standardRateService.getStandardRateWithVat("survey");
    BigDecimal smsRate = standardRateService.getStandardRateWithVat("msg_sms");
    BigDecimal lmsRate = standardRateService.getStandardRateWithVat("msg_lms");
    BigDecimal mmsRate = standardRateService.getStandardRateWithVat("msg_mms");
    BigDecimal qrRate = standardRateService.getStandardRateWithVat("qr_code");

    userServiceRateMapper.insert(UserServiceRate.create(userSeq, "survey", surveyRate, today));
    userServiceRateMapper.insert(UserServiceRate.create(userSeq, "msg_sms", smsRate, today));
    userServiceRateMapper.insert(UserServiceRate.create(userSeq, "msg_lms", lmsRate, today));
    userServiceRateMapper.insert(UserServiceRate.create(userSeq, "msg_mms", mmsRate, today));
    userServiceRateMapper.insert(UserServiceRate.create(userSeq, "qr_code", qrRate, today));

    log.info(
        "회원가입 완료: userSeq={}, 설문단가={}, SMS단가={}, LMS단가={}, MMS단가={}, QR단가={}",
        userSeq,
        surveyRate,
        smsRate,
        lmsRate,
        mmsRate,
        qrRate);
  }

  /** 토큰 갱신 */
  public LoginResponse refreshToken(String refreshToken) {
    // 1. Refresh Token 유효성 검증
    if (!jwtTokenProvider.validateToken(refreshToken)) {
      throw new BusinessException(ErrorCode.INVALID_TOKEN, "유효하지 않은 Refresh Token입니다.");
    }

    // 2. 토큰에서 사용자 정보 추출
    String userId = jwtTokenProvider.getUserId(refreshToken);

    // 3. 사용자 조회
    User user =
        userMapper
            .findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

    // 4. 계정 상태 확인
    if (!user.isActive()) {
      throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
    }

    // 5. 새 토큰 발급 (사용자 이름 포함)
    Authentication authentication = createAuthentication(user);
    String newAccessToken = jwtTokenProvider.createAccessToken(authentication, user.getPerson());
    String newRefreshToken = jwtTokenProvider.createRefreshToken(authentication, user.getPerson());

    return LoginResponse.builder()
        .accessToken(newAccessToken)
        .refreshToken(newRefreshToken)
        .expiresIn(jwtTokenProvider.getAccessTokenValidityInSeconds())
        .user(
            LoginResponse.UserInfo.builder()
                .seq(user.getSeq())
                .userId(user.getUserId())
                .corpName(user.getCorpName())
                .person(decryptField(user.getPerson()))
                .email(decryptField(user.getEmail()))
                .userLevel(user.getUserLevel())
                .status(user.getStatus())
                .build())
        .build();
  }

  /** 아이디 중복 확인 */
  public boolean checkUserIdDuplicate(String userId) {
    return userMapper.existsByUserId(userId);
  }

  /** 이메일 중복 확인 */
  public boolean checkEmailDuplicate(String email) {
    return userMapper.existsByEmail(email);
  }

  /** Authentication 객체 생성 - JWT subject로 seq 사용 */
  private Authentication createAuthentication(User user) {
    List<SimpleGrantedAuthority> authorities =
        Collections.singletonList(
            new SimpleGrantedAuthority(user.isAdmin() ? "ROLE_ADMIN" : "ROLE_USER"));
    return new UsernamePasswordAuthenticationToken(
        String.valueOf(user.getSeq()), null, authorities);
  }

  /** 암호화된 필드 복호화 (AES256 + Base64). 복호화 실패 시 원본 값 반환 */
  private String decryptField(String encryptedValue) {
    if (encryptedValue == null || encryptedValue.isEmpty()) {
      return encryptedValue;
    }
    try {
      return CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(encryptedValue));
    } catch (Exception e) {
      log.debug("필드 복호화 실패, 원본 반환: {}", e.getMessage());
      return encryptedValue;
    }
  }
}
