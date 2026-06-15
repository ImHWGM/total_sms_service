package kr.wisead.domain.email.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CommonUtils;
import kr.wisead.domain.email.dto.EmailVerificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 로그인/2FA 도메인 이메일 인증 서비스 (key=userId).
 *
 * <p>이미 사용자 ID(seq)가 식별된 흐름(로그인 이메일 2FA 등)에서만 사용한다. 저장소 키는 {@code user.seq(Integer)}이며, 같은 이메일을
 * 사용하는 다른 사용자/회원가입과는 도메인이 격리된다. email 인자는 *발송용*으로만 받고 인증 키에는 사용하지 않는다.
 *
 * <p>회원가입처럼 사용자 식별자가 없는 흐름은 {@link PreSignupEmailAuthService}(key=email)를 사용한다.
 *
 * <p>plan §4 Phase B-0-3 (#v3-1 CRITICAL 해소).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAuthService {

  private final EmailService emailService;

  /** 인증 코드 저장소 (userId(seq) -> 인증정보). */
  private final Map<Integer, VerificationInfo> verificationStore = new ConcurrentHashMap<>();

  /** 인증 코드 유효 시간 (5분). */
  private static final int EXPIRATION_MINUTES = 5;

  /** 재발송 제한 시간 (1분). */
  private static final int RESEND_LIMIT_SECONDS = 60;

  /** 최대 시도 횟수. */
  private static final int MAX_ATTEMPTS = 5;

  // ── OTP 용도 상수 (AC28: purpose 분리로 cross-purpose replay 방지) ──────────
  /** 로그인 2단계 인증용 OTP */
  public static final String PURPOSE_LOGIN_2FA = "LOGIN_2FA";

  /** 계정 잠금 해제용 OTP */
  public static final String PURPOSE_UNLOCK = "UNLOCK";

  /** 휴면 계정 복구용 OTP */
  public static final String PURPOSE_DORMANT_RECOVERY = "DORMANT_RECOVERY";

  /**
   * 인증 코드 발송 (purpose 지정).
   *
   * @param userId 사용자 seq (인증 저장소 key)
   * @param email 발송 대상 이메일 (발송용; 저장소 key가 아님)
   * @param purpose OTP 용도 (PURPOSE_LOGIN_2FA / PURPOSE_UNLOCK / PURPOSE_DORMANT_RECOVERY)
   */
  public boolean sendVerificationCode(Integer userId, String email, String purpose) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 식별자가 필요합니다.");
    }
    if (!isValidEmail(email)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 이메일 형식입니다.");
    }

    VerificationInfo existingInfo = verificationStore.get(userId);
    if (existingInfo != null && !existingInfo.canResend()) {
      long remainingSeconds = existingInfo.getRemainingResendSeconds();
      throw new BusinessException(
          ErrorCode.INVALID_INPUT_VALUE, String.format("재발송은 %d초 후에 가능합니다.", remainingSeconds));
    }

    String code = emailService.createVerificationCode();
    String resolvedPurpose = (purpose != null) ? purpose : PURPOSE_LOGIN_2FA;

    VerificationInfo info = new VerificationInfo(code, LocalDateTime.now(), resolvedPurpose);
    verificationStore.put(userId, info);

    try {
      emailService.sendVerificationEmail(email, code);
      log.info(
          "인증 코드 발송 완료: userId={}, email={}, purpose={}",
          userId,
          CommonUtils.maskingEmailShort(email),
          resolvedPurpose);
      return true;
    } catch (Exception e) {
      log.error(
          "인증 코드 발송 실패: userId={}, email={}", userId, CommonUtils.maskingEmailShort(email), e);
      verificationStore.remove(userId);
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "인증 코드 발송에 실패했습니다.");
    }
  }

  /**
   * 인증 코드 발송 (기존 2-arg 시그니처 — 기본 purpose = LOGIN_2FA).
   *
   * @deprecated purpose 를 명시하는 3-arg 오버로드 사용 권장.
   */
  @Deprecated(since = "PR1", forRemoval = false)
  public boolean sendVerificationCode(Integer userId, String email) {
    return sendVerificationCode(userId, email, PURPOSE_LOGIN_2FA);
  }

  /**
   * 인증 코드 검증 (purpose 검증 포함).
   *
   * @param userId 사용자 seq (인증 저장소 key)
   * @param code 입력 코드
   * @param expectedPurpose 기대 용도 (null 이면 검증 skip — 기존 호환)
   */
  public boolean verifyCode(Integer userId, String code, String expectedPurpose) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 식별자가 필요합니다.");
    }
    VerificationInfo info = verificationStore.get(userId);

    if (info == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드를 먼저 발송해주세요.");
    }

    // AC28: purpose 불일치 시 즉시 거부 (cross-purpose replay 방지)
    if (expectedPurpose != null && !expectedPurpose.equals(info.getPurpose())) {
      log.warn(
          "OTP purpose 불일치: userId={}, expected={}, actual={}",
          userId,
          expectedPurpose,
          info.getPurpose());
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "OTP 용도가 일치하지 않습니다.");
    }

    if (info.isExpired()) {
      verificationStore.remove(userId);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드가 만료되었습니다. 다시 발송해주세요.");
    }

    if (info.getAttempts() >= MAX_ATTEMPTS) {
      verificationStore.remove(userId);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 시도 횟수를 초과했습니다. 다시 발송해주세요.");
    }

    info.incrementAttempts();
    if (!info.getCode().equals(code)) {
      log.warn("인증 코드 불일치: userId={}, attempts={}", userId, info.getAttempts());
      throw new BusinessException(
          ErrorCode.INVALID_INPUT_VALUE,
          String.format("인증 코드가 일치하지 않습니다. (남은 시도: %d회)", MAX_ATTEMPTS - info.getAttempts()));
    }

    verificationStore.remove(userId);
    log.info("이메일 인증 성공: userId={}", userId);
    return true;
  }

  /**
   * 인증 코드 검증 (기존 2-arg 시그니처 — purpose 검증 skip).
   *
   * @deprecated purpose 를 명시하는 3-arg 오버로드 사용 권장.
   */
  @Deprecated(since = "PR1", forRemoval = false)
  public boolean verifyCode(Integer userId, String code) {
    return verifyCode(userId, code, null);
  }

  /**
   * 인증 상태 확인.
   *
   * @param userId 사용자 seq
   */
  public EmailVerificationStatus getVerificationStatus(Integer userId) {
    if (userId == null) {
      return new EmailVerificationStatus(false, 0, 0, 0);
    }
    VerificationInfo info = verificationStore.get(userId);
    if (info == null) {
      return new EmailVerificationStatus(false, 0, 0, 0);
    }

    long remainingSeconds = info.getRemainingSeconds();
    long remainingResendSeconds = info.getRemainingResendSeconds();
    int remainingAttempts = MAX_ATTEMPTS - info.getAttempts();

    return new EmailVerificationStatus(
        true, remainingSeconds, remainingResendSeconds, remainingAttempts);
  }

  /**
   * 인증 코드 재발송 (기존 코드 무효화).
   *
   * @param userId 사용자 seq (저장소 key)
   * @param email 발송 대상 이메일 (발송용)
   */
  public boolean resendVerificationCode(Integer userId, String email) {
    if (userId != null) {
      verificationStore.remove(userId);
    }
    return sendVerificationCode(userId, email);
  }

  /**
   * 사용자에 묶인 인증 정보 강제 무효화 (OtpStoreCoordinator 등 외부 협력자용).
   *
   * <p>도메인 격리를 위해 신규로 추가된 메서드. plan §4 Phase B-0-3.
   */
  public void invalidate(Integer userId) {
    if (userId == null) {
      return;
    }
    verificationStore.remove(userId);
  }

  /** 만료된 인증 정보 정리 (5분마다 실행). */
  @Scheduled(fixedRate = 300000)
  public void cleanupExpiredCodes() {
    int before = verificationStore.size();
    verificationStore.entrySet().removeIf(entry -> entry.getValue().isExpired());
    int after = verificationStore.size();
    if (before != after) {
      log.debug("만료된 인증 코드 정리: {} -> {}", before, after);
    }
  }

  // ==================== Private Methods ====================

  /**
   * 이메일 형식 검증.
   *
   * <p>참고: 같은 정규식 패턴이 {@code OtherTypeValidator.EMAIL_PATTERN}에 verbatim 복제되어 있다 (단일 SoT는 거기에 있음).
   */
  private boolean isValidEmail(String email) {
    if (email == null || email.isBlank()) {
      return false;
    }
    return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
  }

  // ==================== Inner Classes ====================

  /** 인증 정보 (userId 키 도메인 전용). */
  private static class VerificationInfo {
    private final String code;
    private final LocalDateTime createdAt;
    private int attempts;

    /** OTP 용도 (AC28: cross-purpose replay 방지). */
    private final String purpose;

    VerificationInfo(String code, LocalDateTime createdAt, String purpose) {
      this.code = code;
      this.createdAt = createdAt;
      this.attempts = 0;
      this.purpose = purpose;
    }

    String getCode() {
      return code;
    }

    String getPurpose() {
      return purpose;
    }

    int getAttempts() {
      return attempts;
    }

    void incrementAttempts() {
      this.attempts++;
    }

    boolean isExpired() {
      return LocalDateTime.now().isAfter(createdAt.plusMinutes(EXPIRATION_MINUTES));
    }

    boolean canResend() {
      return LocalDateTime.now().isAfter(createdAt.plusSeconds(RESEND_LIMIT_SECONDS));
    }

    long getRemainingSeconds() {
      LocalDateTime expiresAt = createdAt.plusMinutes(EXPIRATION_MINUTES);
      return java.time.Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
    }

    long getRemainingResendSeconds() {
      LocalDateTime canResendAt = createdAt.plusSeconds(RESEND_LIMIT_SECONDS);
      long remaining = java.time.Duration.between(LocalDateTime.now(), canResendAt).getSeconds();
      return Math.max(0, remaining);
    }
  }
}
