package kr.wisead.domain.sms.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kr.wisead.common.dto.VerificationStatus;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CommonUtils;
import kr.wisead.domain.sms.sender.SmsOtpSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 로그인/2FA 도메인 SMS 인증 서비스 (key=userId).
 *
 * <p>이미 사용자 ID(seq)가 식별된 흐름(로그인 SMS 2FA 등)에서만 사용한다. 저장소 키는 {@code user.seq(Integer)}이며, {@link
 * kr.wisead.domain.email.service.EmailAuthService} 와 평행 구조를 유지한다. phoneNumber 인자는 *발송용*으로만 받고 인증
 * 키에는 사용하지 않는다.
 *
 * <p>SMS 발송은 {@link SmsOtpSender} 를 통해 결제/야간/잔액 검증을 우회하여 큐에 직접 적재한다. GMGO API 실패 시 자동 EMAIL 폴백은
 * 금지한다 (스펙 C7).
 *
 * <p>응답 스키마 통일을 위해 {@link VerificationStatus} record 를 재사용한다.
 *
 * <p>plan v5 §4 Phase B-2.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsAuthService {

  private final SmsOtpSender smsOtpSender;

  /** 인증 코드 저장소 (userId(seq) -> 인증정보). */
  private final Map<Integer, VerificationInfo> verificationStore = new ConcurrentHashMap<>();

  /** OTP 생성용 SecureRandom (스레드 안전). */
  private final SecureRandom secureRandom = new SecureRandom();

  /** 인증 코드 유효 시간 (5분). */
  private static final int EXPIRATION_MINUTES = 5;

  /** 재발송 제한 시간 (1분). */
  private static final int RESEND_LIMIT_SECONDS = 60;

  /** 최대 시도 횟수. */
  private static final int MAX_ATTEMPTS = 5;

  /**
   * SMS 인증 코드 발송.
   *
   * @param userId 사용자 seq (인증 저장소 key)
   * @param phoneNumber 발송 대상 휴대폰번호 (발송용; 저장소 key가 아님)
   */
  public void sendVerificationCode(Integer userId, String phoneNumber) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 식별자가 필요합니다.");
    }
    if (phoneNumber == null || phoneNumber.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "휴대폰번호가 필요합니다.");
    }

    VerificationInfo existingInfo = verificationStore.get(userId);
    if (existingInfo != null && !existingInfo.canResend()) {
      long remainingSeconds = existingInfo.getRemainingResendSeconds();
      throw new BusinessException(
          ErrorCode.INVALID_INPUT_VALUE, String.format("재발송은 %d초 후에 가능합니다.", remainingSeconds));
    }

    String code = generateOtpCode();

    VerificationInfo info = new VerificationInfo(code, LocalDateTime.now(), phoneNumber);
    verificationStore.put(userId, info);

    try {
      smsOtpSender.sendOtp(phoneNumber, code);
      log.info(
          "SMS 인증 코드 발송 완료: userId={}, phone={}", userId, CommonUtils.maskingPhone(phoneNumber));
    } catch (Exception e) {
      // GMGO 실패 시 자동 EMAIL 폴백 금지 (스펙 C7). in-memory entry 는 유지하여
      // 사용자가 명시적으로 재발송을 선택할 수 있도록 한다.
      log.error(
          "SMS 인증 코드 발송 실패: userId={}, phone={}", userId, CommonUtils.maskingPhone(phoneNumber), e);
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SMS 인증 코드 발송에 실패했습니다.");
    }
  }

  /**
   * SMS 인증 코드 검증.
   *
   * @param userId 사용자 seq (인증 저장소 key)
   * @param code 입력 코드
   */
  public void verifyCode(Integer userId, String code) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 식별자가 필요합니다.");
    }
    VerificationInfo info = verificationStore.get(userId);

    if (info == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드를 먼저 발송해주세요.");
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
      log.warn("SMS 인증 코드 불일치: userId={}, attempts={}", userId, info.getAttempts());
      int remaining = MAX_ATTEMPTS - info.getAttempts();
      if (remaining <= 0) {
        // 5회 실패 → 코드 폐기. 새 코드 발급 시 60초 재발송 제한이 새로 적용된다.
        verificationStore.remove(userId);
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 시도 횟수를 초과했습니다. 다시 발송해주세요.");
      }
      throw new BusinessException(
          ErrorCode.INVALID_INPUT_VALUE,
          String.format("인증 코드가 일치하지 않습니다. (남은 시도: %d회)", remaining));
    }

    verificationStore.remove(userId);
    log.info("SMS 인증 성공: userId={}", userId);
  }

  /**
   * SMS 인증 코드 검증 + 검증된 phoneNumber 반환 (마이페이지 SMS 등록 흐름 전용).
   *
   * <p>{@link #verifyCode}와 동일한 검증 로직을 수행하되, 성공 시 entry 에 저장된 phoneNumber 를 반환한다. 마이페이지 SMS 활성화
   * 흐름에서 OTP 검증 통과 후 login_phone 으로 저장하기 위해 사용한다.
   *
   * <p>plan v5 §4 Phase E-2.
   *
   * @param userId 사용자 seq (인증 저장소 key)
   * @param code 입력 코드
   * @return 발송 시점에 저장된 phoneNumber
   */
  public String verifyCodeAndGetPhone(Integer userId, String code) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 식별자가 필요합니다.");
    }
    VerificationInfo info = verificationStore.get(userId);

    if (info == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드를 먼저 발송해주세요.");
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
      log.warn(
          "SMS 인증 코드 불일치(verifyAndGetPhone): userId={}, attempts={}", userId, info.getAttempts());
      int remaining = MAX_ATTEMPTS - info.getAttempts();
      if (remaining <= 0) {
        verificationStore.remove(userId);
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 시도 횟수를 초과했습니다. 다시 발송해주세요.");
      }
      throw new BusinessException(
          ErrorCode.INVALID_INPUT_VALUE,
          String.format("인증 코드가 일치하지 않습니다. (남은 시도: %d회)", remaining));
    }

    String phoneNumber = info.getPhoneNumber();
    verificationStore.remove(userId);
    log.info("SMS 인증 성공(phone 반환): userId={}", userId);
    return phoneNumber;
  }

  /**
   * 인증 상태 확인.
   *
   * @param userId 사용자 seq
   */
  public VerificationStatus getVerificationStatus(Integer userId) {
    if (userId == null) {
      return new VerificationStatus(false, 0, 0, 0);
    }
    VerificationInfo info = verificationStore.get(userId);
    if (info == null) {
      return new VerificationStatus(false, 0, 0, 0);
    }

    long remainingSeconds = info.getRemainingSeconds();
    long remainingResendSeconds = info.getRemainingResendSeconds();
    int remainingAttempts = MAX_ATTEMPTS - info.getAttempts();

    return new VerificationStatus(
        true, remainingSeconds, remainingResendSeconds, remainingAttempts);
  }

  /**
   * SMS 인증 코드 재발송 (기존 코드 무효화).
   *
   * @param userId 사용자 seq (저장소 key)
   * @param phoneNumber 발송 대상 휴대폰번호 (발송용)
   */
  public void resendVerificationCode(Integer userId, String phoneNumber) {
    if (userId != null) {
      verificationStore.remove(userId);
    }
    sendVerificationCode(userId, phoneNumber);
  }

  /**
   * 사용자에 묶인 인증 정보 강제 무효화 (OtpStoreCoordinator 등 외부 협력자용).
   *
   * <p>EmailAuthService 와 평행. plan §4 Phase B-2.
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
      log.debug("만료된 SMS 인증 코드 정리: {} -> {}", before, after);
    }
  }

  // ==================== Private Methods ====================

  /** 6자리 숫자 OTP 생성 (SecureRandom). */
  private String generateOtpCode() {
    return String.format("%06d", secureRandom.nextInt(1_000_000));
  }

  // ==================== Inner Classes ====================

  /** 인증 정보 (userId 키 도메인 전용). */
  private static class VerificationInfo {
    private final String code;
    private final LocalDateTime createdAt;
    private final String phoneNumber;
    private int attempts;

    VerificationInfo(String code, LocalDateTime createdAt, String phoneNumber) {
      this.code = code;
      this.createdAt = createdAt;
      this.phoneNumber = phoneNumber;
      this.attempts = 0;
    }

    String getCode() {
      return code;
    }

    String getPhoneNumber() {
      return phoneNumber;
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
