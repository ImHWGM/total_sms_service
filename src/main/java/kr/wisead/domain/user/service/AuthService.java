package kr.wisead.domain.user.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CommonUtils;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.common.util.MemberUtil;
import kr.wisead.domain.audit.service.AuditEventService;
import kr.wisead.domain.audit.service.AuditEventService.UnlockType;
import kr.wisead.domain.email.service.EmailAuthService;
import kr.wisead.domain.payment.entity.UserServiceRate;
import kr.wisead.domain.payment.service.StandardRateService;
import kr.wisead.domain.payment.service.WalletService;
import kr.wisead.domain.sms.service.SmsAuthService;
import kr.wisead.domain.user.dto.LoginFailureResponse;
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
  private final AuditEventService auditEventService;

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

    // 2. 계정 잠금 확인 (lockedUntil 기반 — PR1 AC27)
    if (user.isLocked()) {
      throw buildAccountLockedException(user.getLockedUntil());
    }

    // 3. 계정 상태 확인 (lifecycleStatus 우선, 한글 status legacy fallback)
    //    DORMANT는 별도 분기: ACCOUNT_LOCKED(423 의미) + 복관 안내 힌트 (PR3)
    if (user.isDormant()) {
      throw new BusinessException(
          ErrorCode.ACCOUNT_LOCKED,
          "휴면 계정입니다. 이메일 인증을 통해 복관하세요.",
          Map.of("status", "DORMANT", "recoveryRequired", true));
    }
    if (!user.isActive()) {
      throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, resolveInactiveMessage(user));
    }

    // 4. 비밀번호 확인
    if (!passwordEncoder.matches(request.getUserPass(), user.getUserPass())) {
      // 원자적 실패 카운트 증가 (seq 기반, AC27)
      userMapper.increaseLoginFailureCnt(user.getSeq());
      // 재조회: 5회차에 LOCKED_UNTIL 이 설정됐는지 확인
      User reloaded =
          userMapper
              .findBySeq(user.getSeq())
              .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
      if (reloaded.isLocked()) {
        throw buildAccountLockedException(reloaded.getLockedUntil());
      }
      int failureCnt = reloaded.getLoginFailureCnt() != null ? reloaded.getLoginFailureCnt() : 1;
      throw new BusinessException(
          ErrorCode.LOGIN_FAILED,
          "아이디 또는 비밀번호가 일치하지 않습니다.",
          LoginFailureResponse.forFailure(failureCnt));
    }

    // 5. OTP 코드 검증 흐름 (1차 인증 완료 후 채널 OTP)
    String emailCode = request.getEmailCode();
    String channel = resolveChannel(user);

    if (StringUtils.hasText(emailCode)) {
      // 5-A. OTP 코드 제출: sessionKey 검증 → 활성 채널 우선 사용 → OTP 검증 → sessionKey 폐기
      String sessionKey = request.getSessionKey();
      sessionKeyService.validate(sessionKey);
      // 채널 전환 이력이 있으면 user.defaultTwoFactorMethod 대신 활성 채널로 검증 (cross-channel 방지)
      String activeChannel = sessionKeyService.getActiveChannel(sessionKey);
      String verifyChannel = activeChannel != null ? activeChannel : channel;
      verifyOtpInternal(user.getSeq(), emailCode, verifyChannel);
      sessionKeyService.invalidate(sessionKey);
      log.info("[OTP 인증 성공] userId={}, channel={}", user.getUserId(), verifyChannel);
      channel = verifyChannel;
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

    // 6. 로그인 성공 처리 — 실패 횟수 초기화 + 잠금 해제 (원자적) + 마지막 로그인 갱신
    userMapper.resetLoginAndUnlock(user.getSeq());
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
      sessionKeyService.setActiveChannel(sessionKey, "SMS");
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
      sessionKeyService.setActiveChannel(sessionKey, "EMAIL");
      log.info("[채널 전환 → EMAIL] userId={}, email={}", userId, CommonUtils.maskingEmailShort(email));
      return LoginResponse.builder()
          .sessionKey(sessionKey)
          .channel("EMAIL")
          .maskedEmail(CommonUtils.maskingEmailShort(email))
          .availableChannels(getAvailableChannels(user))
          .build();
    }
    throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "지원하지 않는 채널입니다.");
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
      String masked = CommonUtils.maskingPhone(loginPhone);
      if (!smsAuthService.getVerificationStatus(user.getSeq()).codeSent()) {
        log.info("[SMS OTP 발송] userId={}, phone={}", user.getUserId(), masked);
        smsAuthService.sendVerificationCode(user.getSeq(), loginPhone);
      } else {
        log.info("[SMS OTP 재사용] userId={}, phone={}", user.getUserId(), masked);
      }
      return LoginResponse.builder()
          .sessionKey(sessionKey)
          .channel("SMS")
          .maskedPhone(masked)
          .availableChannels(getAvailableChannels(user))
          .build();
    } else {
      // EMAIL (기본)
      String email = decryptField(user.getEmail());
      if (!StringUtils.hasText(email)) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "등록된 이메일이 없습니다. 관리자에게 문의하세요.");
      }
      String masked = CommonUtils.maskingEmailShort(email);
      if (!emailAuthService.getVerificationStatus(user.getSeq()).codeSent()) {
        log.info("[EMAIL OTP 발송] userId={}, email={}", user.getUserId(), masked);
        emailAuthService.sendVerificationCode(user.getSeq(), email);
      } else {
        log.info("[EMAIL OTP 재사용] userId={}, email={}", user.getUserId(), masked);
      }
      return LoginResponse.builder()
          .sessionKey(sessionKey)
          .channel("EMAIL")
          .maskedEmail(masked)
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
    if (StringUtils.hasText(user.getLoginPhone())) {
      return List.of("EMAIL", "SMS");
    }
    return List.of("EMAIL");
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

    // 2. 계정 잠금 확인 (login() 과 동일한 LoginFailureResponse payload — FE 일관성)
    if (user.isLocked()) {
      throw buildAccountLockedException(user.getLockedUntil());
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

    // 8. 감사 로그 기록 (AC1-revised: SIGNUP → audit_event)
    // ip/userAgent 는 SignUpRequest 에 없어 null 전달 (컨트롤러 레이어에서 HttpServletRequest 로 보강 가능)
    auditEventService.recordSignup(user, null, null, "WEB");
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

  /**
   * 비활성 계정 오류 메시지 결정.
   *
   * <p>lifecycleStatus 가 non-null 이면 enum 기반 메시지 우선. null 이면 legacy 한글 STATUS 값으로 fallback
   * (PR4 cleanup 전까지 보존).
   */
  private String resolveInactiveMessage(User user) {
    if (user.getLifecycleStatus() != null) {
      return switch (user.getLifecycleStatus()) {
        case PENDING_APPROVAL -> "승인 대기 중인 계정입니다.";
        case WITHDRAWN -> "탈퇴된 계정입니다.";
        default -> "비활성화된 계정입니다.";
      };
    }
    // legacy fallback (PR4 cleanup 전까지)
    return switch (user.getStatus()) {
      case "미승인" -> "승인 대기 중인 계정입니다.";
      case "탈퇴" -> "탈퇴된 계정입니다.";
      default -> "비활성화된 계정입니다.";
    };
  }

  /** 계정 잠금 예외 생성 (lockedUntil 기반 LoginFailureResponse payload 포함). */
  private BusinessException buildAccountLockedException(LocalDateTime lockedUntil) {
    long remainingSecs = Duration.between(LocalDateTime.now(), lockedUntil).toSeconds();
    Instant lockedUntilInstant = lockedUntil.atZone(ZoneOffset.UTC).toInstant();
    return new BusinessException(
        ErrorCode.ACCOUNT_LOCKED,
        "로그인 실패 횟수 초과로 계정이 잠겼습니다.",
        LoginFailureResponse.forLocked(lockedUntilInstant, (int) Math.max(0, remainingSecs)));
  }

  // ==================== 잠금 해제 (PR1) ====================

  /**
   * 이메일로 OTP 발송 요청 (잠금 해제용).
   *
   * <p>계정이 잠긴 사용자가 이메일 인증을 통해 즉시 잠금을 해제할 수 있도록 OTP를 발송한다.
   *
   * @param email 사용자 이메일
   */
  @Transactional(readOnly = true)
  public void requestUnlockOtp(String email) {
    // 계정 열거(account enumeration) 방지: 미존재/비잠금 계정은 조용히 무시하고
    // 컨트롤러는 항상 동일한 일반 성공 응답을 반환한다. 실제 결과는 서버 로그로만 남긴다.
    userMapper
        .findByEmail(email)
        .filter(User::isLocked)
        .ifPresentOrElse(
            user -> {
              String decryptedEmail = decryptField(user.getEmail());
              emailAuthService.sendVerificationCode(
                  user.getSeq(), decryptedEmail, EmailAuthService.PURPOSE_UNLOCK);
              log.info(
                  "[잠금해제 OTP 발송] userSeq={}, email={}",
                  user.getSeq(),
                  CommonUtils.maskingEmailShort(decryptedEmail));
            },
            () -> log.info("[잠금해제 OTP 요청 무시] 미존재 또는 비잠금 계정 - 응답 일반화"));
  }

  /**
   * 이메일 OTP 검증 후 즉시 잠금 해제.
   *
   * @param userSeq 사용자 seq
   * @param otp 사용자가 입력한 OTP
   */
  @Transactional
  public void unlockByEmailOtp(int userSeq, String otp) {
    User user =
        userMapper
            .findBySeq(userSeq)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

    emailAuthService.verifyCode(userSeq, otp, EmailAuthService.PURPOSE_UNLOCK);

    userMapper.resetLoginAndUnlock(userSeq);
    auditEventService.recordUnlock(user, UnlockType.OTP, userSeq);
    log.info("[잠금해제 완료 (OTP)] userSeq={}", userSeq);
  }

  /**
   * 이메일 + OTP 로 잠금 해제 (컨트롤러 진입점 — 이메일로 userSeq 를 내부 조회).
   *
   * @param email 사용자 이메일 (암호화된 값 or 평문)
   * @param otp 입력된 OTP
   */
  @Transactional
  public void unlockByEmail(String email, String otp) {
    User user =
        userMapper
            .findByEmail(email)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "등록된 이메일이 없습니다."));

    unlockByEmailOtp(user.getSeq(), otp);
  }

  /**
   * 관리자 강제 잠금 해제.
   *
   * <p>기존 unlockAccount(userId) 보강: seq 기반 atomic reset + AuditEvent 기록.
   *
   * @param userSeq 대상 사용자 seq
   * @param adminSeq 요청한 관리자 seq
   */
  @Transactional
  public void unlockAccount(int userSeq, int adminSeq) {
    User user =
        userMapper
            .findBySeq(userSeq)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

    userMapper.resetLoginAndUnlock(userSeq);
    auditEventService.recordUnlock(user, UnlockType.ADMIN, adminSeq);
    log.info("[잠금해제 완료 (관리자)] userSeq={}, adminSeq={}", userSeq, adminSeq);
  }

  // ==================== 휴면 복관 (PR3) ====================

  /**
   * 휴면 계정 복관 OTP 발송 요청.
   *
   * <p>이메일로 사용자를 조회하고, 휴면 상태인 경우에만 OTP를 발송한다.
   *
   * @param email 사용자 이메일
   */
  @Transactional(readOnly = true)
  public void requestDormantRecovery(String email) {
    // 계정 열거(account enumeration) 방지: 미존재/비휴면 계정은 조용히 무시하고
    // 컨트롤러는 항상 동일한 일반 성공 응답을 반환한다. 실제 결과는 서버 로그로만 남긴다.
    userMapper
        .findByEmail(email)
        .filter(User::isDormant)
        .ifPresentOrElse(
            user -> {
              String decryptedEmail = decryptField(user.getEmail());
              emailAuthService.sendVerificationCode(
                  user.getSeq(), decryptedEmail, EmailAuthService.PURPOSE_DORMANT_RECOVERY);
              log.info(
                  "[휴면 복관 OTP 발송] userSeq={}, email={}",
                  user.getSeq(),
                  CommonUtils.maskingEmailShort(decryptedEmail));
            },
            () -> log.info("[휴면 복관 OTP 요청 무시] 미존재 또는 비휴면 계정 - 응답 일반화"));
  }

  /**
   * 이메일 OTP 검증 후 휴면 복관 처리.
   *
   * <p>OTP purpose=DORMANT_RECOVERY 검증 → LIFECYCLE_STATUS=ACTIVE, DORMANT_AT=NULL,
   * DORMANT_NOTIFIED_AT=NULL → AuditEvent RECOVERY 기록.
   *
   * @param email 사용자 이메일 (컨트롤러에서 userSeq 대신 이메일로 진입)
   * @param otp 사용자가 입력한 OTP
   */
  @Transactional
  public void recoverDormant(String email, String otp) {
    User user =
        userMapper
            .findByEmail(email)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "등록된 이메일이 없습니다."));

    if (!user.isDormant()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "휴면 상태인 계정이 아닙니다.");
    }

    emailAuthService.verifyCode(user.getSeq(), otp, EmailAuthService.PURPOSE_DORMANT_RECOVERY);

    userMapper.recoverDormant(user.getSeq());
    auditEventService.recordRecovery(user, null, null);
    log.info("[휴면 복관 완료] userSeq={}", user.getSeq());
  }
}
