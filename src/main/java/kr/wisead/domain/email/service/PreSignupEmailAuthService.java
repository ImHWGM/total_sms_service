package kr.wisead.domain.email.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kr.wisead.common.dto.VerificationStatus;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CommonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 회원가입(사전 인증) 도메인 이메일 인증 서비스.
 *
 * <p>회원가입 흐름과 같이 아직 로그인된 사용자 식별자(userId/seq)가 존재하지 않는 단계에서 사용한다. 저장소 키는 {@code email}(소문자 정규화)이며,
 * 행동상 기존 {@link EmailAuthService}(key=email) 로직과 1:1 동등하다.
 *
 * <p>로그인 후/2FA 흐름은 {@link EmailAuthService}(key=userId)를 사용해야 한다. 두 서비스는 저장소가 완전히 격리되어 있어 동일 이메일이라도
 * 도메인 간 인증 정보가 공유되지 않는다.
 *
 * <p>plan §4 Phase B-0-2.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreSignupEmailAuthService {

  private final EmailService emailService;

  /** 인증 코드 저장소 (이메일 소문자 -> 인증정보). */
  private final Map<String, VerificationInfo> verificationStore = new ConcurrentHashMap<>();

  /** 인증 코드 유효 시간 (5분). */
  private static final int EXPIRATION_MINUTES = 5;

  /** 재발송 제한 시간 (1분). */
  private static final int RESEND_LIMIT_SECONDS = 60;

  /** 최대 시도 횟수. */
  private static final int MAX_ATTEMPTS = 5;

  /** 인증 코드 발송. */
  public boolean sendVerificationCode(String email) {
    if (!isValidEmail(email)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 이메일 형식입니다.");
    }

    String key = email.toLowerCase();
    VerificationInfo existingInfo = verificationStore.get(key);
    if (existingInfo != null && !existingInfo.canResend()) {
      long remainingSeconds = existingInfo.getRemainingResendSeconds();
      throw new BusinessException(
          ErrorCode.INVALID_INPUT_VALUE, String.format("재발송은 %d초 후에 가능합니다.", remainingSeconds));
    }

    String code = emailService.createVerificationCode();

    VerificationInfo info = new VerificationInfo(code, LocalDateTime.now());
    verificationStore.put(key, info);

    try {
      emailService.sendVerificationEmail(email, code);
      log.info("[PreSignup] 인증 코드 발송 완료: email={}", CommonUtils.maskingEmailShort(email));
      return true;
    } catch (Exception e) {
      log.error("[PreSignup] 인증 코드 발송 실패: email={}", CommonUtils.maskingEmailShort(email), e);
      verificationStore.remove(key);
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "인증 코드 발송에 실패했습니다.");
    }
  }

  /** 인증 코드 검증. */
  public boolean verifyCode(String email, String code) {
    String normalizedEmail = email.toLowerCase();
    VerificationInfo info = verificationStore.get(normalizedEmail);

    if (info == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드를 먼저 발송해주세요.");
    }

    if (info.isExpired()) {
      verificationStore.remove(normalizedEmail);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드가 만료되었습니다. 다시 발송해주세요.");
    }

    if (info.getAttempts() >= MAX_ATTEMPTS) {
      verificationStore.remove(normalizedEmail);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 시도 횟수를 초과했습니다. 다시 발송해주세요.");
    }

    info.incrementAttempts();
    if (!info.getCode().equals(code)) {
      log.warn(
          "[PreSignup] 인증 코드 불일치: email={}, attempts={}",
          CommonUtils.maskingEmailShort(email),
          info.getAttempts());
      throw new BusinessException(
          ErrorCode.INVALID_INPUT_VALUE,
          String.format("인증 코드가 일치하지 않습니다. (남은 시도: %d회)", MAX_ATTEMPTS - info.getAttempts()));
    }

    verificationStore.remove(normalizedEmail);
    log.info("[PreSignup] 이메일 인증 성공: email={}", CommonUtils.maskingEmailShort(email));
    return true;
  }

  /** 인증 상태 확인. */
  public VerificationStatus getVerificationStatus(String email) {
    VerificationInfo info = verificationStore.get(email.toLowerCase());
    if (info == null) {
      return new VerificationStatus(false, 0, 0, 0);
    }

    long remainingSeconds = info.getRemainingSeconds();
    long remainingResendSeconds = info.getRemainingResendSeconds();
    int remainingAttempts = MAX_ATTEMPTS - info.getAttempts();

    return new VerificationStatus(
        true, remainingSeconds, remainingResendSeconds, remainingAttempts);
  }

  /** 인증 코드 재발송 (기존 코드 무효화). */
  public boolean resendVerificationCode(String email) {
    verificationStore.remove(email.toLowerCase());
    return sendVerificationCode(email);
  }

  /** 만료된 인증 정보 정리 (5분마다 실행). */
  @Scheduled(fixedRate = 300000)
  public void cleanupExpiredCodes() {
    int before = verificationStore.size();
    verificationStore.entrySet().removeIf(entry -> entry.getValue().isExpired());
    int after = verificationStore.size();
    if (before != after) {
      log.debug("[PreSignup] 만료된 인증 코드 정리: {} -> {}", before, after);
    }
  }

  // ==================== Private Methods ====================

  private boolean isValidEmail(String email) {
    if (email == null || email.isBlank()) {
      return false;
    }
    return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
  }

  // ==================== Inner Classes ====================

  /** 인증 정보 (이메일 키 도메인 전용). */
  private static class VerificationInfo {
    private final String code;
    private final LocalDateTime createdAt;
    private int attempts;

    VerificationInfo(String code, LocalDateTime createdAt) {
      this.code = code;
      this.createdAt = createdAt;
      this.attempts = 0;
    }

    String getCode() {
      return code;
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
