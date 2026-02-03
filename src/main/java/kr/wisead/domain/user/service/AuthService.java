package kr.wisead.domain.user.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.email.service.EmailAuthService;
import kr.wisead.domain.payment.entity.UserServiceRate;
import kr.wisead.domain.payment.service.StandardRateService;
import kr.wisead.domain.payment.service.WalletService;
import kr.wisead.domain.user.dto.LoginRequest;
import kr.wisead.domain.user.dto.LoginResponse;
import kr.wisead.domain.user.dto.SignUpRequest;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.UserMapper;
import kr.wisead.mapper.primary.UserServiceRateMapper;
import kr.wisead.security.jwt.JwtTokenProvider;
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

  /** 로그인 - 이메일 인증 필요 여부: 오늘 로그인한 적이 없으면 이메일 인증 필요 - 이메일 인증 필요시 서버가 자동으로 이메일 발송 */
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

    // 5. 이메일 인증 처리
    String emailCode = request.getEmailCode();

    if (!StringUtils.hasText(emailCode)) {
      // 5-1. 이메일 코드가 없는 경우: 이메일 인증 필요 여부 판단
      if (!isLoggedInToday(user)) {
        // 오늘 로그인한 적이 없으면 이메일 인증 필요
        String email = decryptField(user.getEmail());
        log.info("[이메일 인증 필요] userId={}, email={}", user.getUserId(), email);

        if (!StringUtils.hasText(email)) {
          throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "등록된 이메일이 없습니다. 관리자에게 문의하세요.");
        }

        // 이미 인증 코드가 발송되었는지 확인
        var verificationStatus = emailAuthService.getVerificationStatus(email);
        if (!verificationStatus.codeSent()) {
          // 인증 코드가 발송되지 않은 경우에만 발송
          log.info("[인증 코드 발송 시도] userId={}, email={}", user.getUserId(), email);
          try {
            emailAuthService.sendVerificationCode(email);
            log.info("[인증 코드 발송 성공] userId={}, email={}", user.getUserId(), email);
          } catch (BusinessException e) {
            log.warn(
                "[인증 코드 발송 실패] userId={}, email={}, 사유={}",
                user.getUserId(),
                email,
                e.getMessage());
            throw e;
          }
        } else {
          log.info("[인증 코드 이미 발송됨 (재사용)] userId={}, email={}", user.getUserId(), email);
        }

        // 이메일 인증 필요 응답 반환
        return LoginResponse.builder().emailRequired(true).maskedEmail(maskEmail(email)).build();
      }
      // 오늘 이미 로그인한 경우: 이메일 인증 불필요, 바로 로그인 성공
      log.info("[이메일 인증 스킵] userId={}, 오늘 이미 로그인함", user.getUserId());
    } else {
      // 5-2. 이메일 코드가 있는 경우: 코드 검증
      String email = decryptField(user.getEmail());
      if (!emailAuthService.verifyCode(email, emailCode)) {
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드가 일치하지 않습니다.");
      }
      log.info("[이메일 인증 성공] userId={}", user.getUserId());
    }

    // 6. 로그인 성공 처리
    userMapper.updateLastLogin(request.getUserId());

    // 7. JWT 토큰 생성 (사용자 이름 포함)
    Authentication authentication = createAuthentication(user);
    String accessToken = jwtTokenProvider.createAccessToken(authentication, user.getPerson());
    String refreshToken = jwtTokenProvider.createRefreshToken(authentication, user.getPerson());

    log.info("[로그인 성공] userId={}", user.getUserId());

    return LoginResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken)
        .expiresIn(jwtTokenProvider.getAccessTokenValidityInSeconds())
        .emailRequired(false)
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

  /** 오늘 로그인한 적이 있는지 확인 */
  private boolean isLoggedInToday(User user) {
    if (user.getLastLogin() == null) {
      return false;
    }
    LocalDate lastLoginDate = user.getLastLogin().toLocalDate();
    LocalDate today = LocalDate.now();
    return lastLoginDate.equals(today);
  }

  /** 이메일 마스킹 (예: abc***@example.com) */
  private String maskEmail(String email) {
    if (email == null || !email.contains("@")) {
      return "***";
    }
    int atIndex = email.indexOf("@");
    if (atIndex <= 3) {
      return email.charAt(0) + "***" + email.substring(atIndex);
    }
    return email.substring(0, 3) + "***" + email.substring(atIndex);
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

    // 6. 이메일 인증 코드 재발송 (기존 코드 무효화 후 새 코드 발송)
    emailAuthService.resendVerificationCode(email);
    log.info("이메일 인증 코드 재발송: userId={}, email={}", user.getUserId(), maskEmail(email));

    return LoginResponse.builder().emailRequired(true).maskedEmail(maskEmail(email)).build();
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
    // 4-1. 담당자 암호화 처리 -> 비밀번호 찾기에서 담당자 암호화 처리가 들어가기에 회원가입시에도 있어야 함.
    String encryptedPerson = null;
    try {
      String person = request.getPerson();
      encryptedPerson = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(person));
    } catch (Exception e) {
      log.error("담당자 암호화 실패: {}", e.getMessage());
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "담당자 암호화에 실패했습니다.");
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
