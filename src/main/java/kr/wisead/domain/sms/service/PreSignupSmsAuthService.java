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
 * 회원가입(사전 인증) 도메인 SMS 인증 서비스.
 *
 * <p>회원가입 흐름과 같이 아직 로그인된 사용자 식별자(userId/seq)가 존재하지 않는 단계에서 사용한다. 저장소 키는 *용도(purpose) + 정규화된
 * 휴대폰번호(숫자만)* 의 조합이며, 기존 {@link kr.wisead.domain.email.service.PreSignupEmailAuthService}(key=email) 와
 * 평행 구조다.
 *
 * <p>로그인 후/2FA 흐름은 {@link SmsAuthService}(key=userId)를 사용해야 한다. 두 서비스는 저장소가 완전히 격리되어 있어 동일 번호라도
 * 도메인 간 인증 정보가 공유되지 않는다.
 *
 * <p>SMS 발송은 {@link SmsOtpSender} 를 통해 결제/야간/잔액 검증을 우회하여 큐에 직접 적재한다.
 *
 * <p>검증 성공 시 해당 번호를 {@code verifiedStore}에 일정 시간(=가입 완료 유예) 기록해 두고, 회원가입({@code signUp})에서
 * {@link #consumeVerification(String, String)} 으로 "인증된 번호인지"를 강제 검사한다.
 *
 * <p><b>purpose 분리(cross-purpose replay 방지)</b>: 저장소 키에 용도를 포함해, 예컨대 "비밀번호 찾기"용으로 인증한 번호를
 * "회원가입"이 가져다 쓰지 못하도록 칸을 분리한다. {@code EmailAuthService}(AC28)도 같은 목적을 달성하지만, 그쪽은 purpose 를
 * record 필드로 저장해 검증 시 대조하는 방식이고 이 서비스는 저장소 키 자체에 purpose 를 합성하는 방식이라는 차이가 있다. 효과는 동등하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreSignupSmsAuthService {

  // ── OTP 용도 상수 (cross-purpose replay 방지) ──────────────────────────────

  /** 회원가입 본인인증 용도. */
  public static final String PURPOSE_SIGNUP = "SIGNUP";

  private final SmsOtpSender smsOtpSender;

  /** 인증 코드 저장소 ("용도:정규화번호" -> 인증정보). */
  private final Map<String, VerificationInfo> verificationStore = new ConcurrentHashMap<>();

  /** 인증 완료 저장소 ("용도:정규화번호" -> 인증 성공 시각). 회원가입 강제 검사용. */
  private final Map<String, LocalDateTime> verifiedStore = new ConcurrentHashMap<>();

  /** OTP 생성용 SecureRandom (스레드 안전). */
  private final SecureRandom secureRandom = new SecureRandom();

  /** 인증 코드 유효 시간 (5분). */
  private static final int EXPIRATION_MINUTES = 5;

  /** 재발송 제한 시간 (1분). */
  private static final int RESEND_LIMIT_SECONDS = 60;

  /** 최대 시도 횟수. */
  private static final int MAX_ATTEMPTS = 5;

  /** 인증 성공 후 회원가입까지 허용되는 유예 시간 (30분). */
  private static final int VERIFIED_TTL_MINUTES = 30;

  /**
   * 인증 코드 발송.
   *
   * @param phoneNumber 수신 휴대폰번호 (하이픈 유무 무관)
   * @param purpose OTP 용도 (예: {@link #PURPOSE_SIGNUP})
   */
  public boolean sendVerificationCode(String phoneNumber, String purpose) {
    String phone = normalizePhone(phoneNumber);
    if (!isValidPhone(phone)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 휴대폰 번호 형식입니다.");
    }
    String key = storeKey(purpose, phone);

    VerificationInfo existingInfo = verificationStore.get(key);
    if (existingInfo != null && !existingInfo.canResend()) {
      long remainingSeconds = existingInfo.getRemainingResendSeconds();
      throw new BusinessException(
          ErrorCode.INVALID_INPUT_VALUE, String.format("재발송은 %d초 후에 가능합니다.", remainingSeconds));
    }

    String code = generateOtpCode();

    VerificationInfo info = new VerificationInfo(code, LocalDateTime.now());
    verificationStore.put(key, info);

    try {
      smsOtpSender.sendOtp(phone, code);
      log.info(
          "[PreSignup] SMS 인증 코드 발송 완료: phone={}, purpose={}",
          CommonUtils.maskingPhone(phone),
          purpose);
      return true;
    } catch (Exception e) {
      log.error(
          "[PreSignup] SMS 인증 코드 발송 실패: phone={}, purpose={}",
          CommonUtils.maskingPhone(phone),
          purpose,
          e);
      verificationStore.remove(key);
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SMS 인증 코드 발송에 실패했습니다.");
    }
  }

  /**
   * 인증 코드 검증. 성공 시 번호를 (해당 용도의) 인증 완료 저장소에 기록한다.
   *
   * @param phoneNumber 휴대폰번호
   * @param code 입력 코드
   * @param purpose OTP 용도 — 발송 시점과 동일해야 검증 가능
   */
  public boolean verifyCode(String phoneNumber, String code, String purpose) {
    String phone = normalizePhone(phoneNumber);
    String key = storeKey(purpose, phone);
    VerificationInfo info = verificationStore.get(key);

    if (info == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드를 먼저 발송해주세요.");
    }

    if (info.isExpired()) {
      verificationStore.remove(key);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드가 만료되었습니다. 다시 발송해주세요.");
    }

    if (info.getAttempts() >= MAX_ATTEMPTS) {
      verificationStore.remove(key);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 시도 횟수를 초과했습니다. 다시 발송해주세요.");
    }

    info.incrementAttempts();
    if (!info.getCode().equals(code)) {
      log.warn(
          "[PreSignup] SMS 인증 코드 불일치: phone={}, purpose={}, attempts={}",
          CommonUtils.maskingPhone(phone),
          purpose,
          info.getAttempts());
      int remaining = MAX_ATTEMPTS - info.getAttempts();
      if (remaining <= 0) {
        verificationStore.remove(key);
        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 시도 횟수를 초과했습니다. 다시 발송해주세요.");
      }
      throw new BusinessException(
          ErrorCode.INVALID_INPUT_VALUE,
          String.format("인증 코드가 일치하지 않습니다. (남은 시도: %d회)", remaining));
    }

    verificationStore.remove(key);
    verifiedStore.put(key, LocalDateTime.now());
    log.info(
        "[PreSignup] SMS 인증 성공: phone={}, purpose={}", CommonUtils.maskingPhone(phone), purpose);
    return true;
  }

  /** 인증 상태 확인. */
  public VerificationStatus getVerificationStatus(String phoneNumber, String purpose) {
    VerificationInfo info =
        verificationStore.get(storeKey(purpose, normalizePhone(phoneNumber)));
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
  public boolean resendVerificationCode(String phoneNumber, String purpose) {
    verificationStore.remove(storeKey(purpose, normalizePhone(phoneNumber)));
    return sendVerificationCode(phoneNumber, purpose);
  }

  /**
   * 회원가입 강제 검사용 — (해당 용도로) 번호가 인증 완료 상태인지 확인하고, 맞으면 1회용으로 소비(제거)한다.
   *
   * <p>인증 안 됐거나 유예시간(30분)을 넘겼으면 예외를 던진다. 화면(프론트)을 우회한 미인증 가입을 백엔드에서 차단한다. 용도가 다른 인증 도장은
   * 키 자체가 달라 조회되지 않으므로 cross-purpose 탈취가 불가능하다.
   *
   * @param phoneNumber 회원가입 요청의 휴대폰번호
   * @param purpose OTP 용도 — 인증 시점과 동일해야 통과
   */
  public void consumeVerification(String phoneNumber, String purpose) {
    String phone = normalizePhone(phoneNumber);
    String key = storeKey(purpose, phone);
    LocalDateTime verifiedAt = verifiedStore.remove(key);

    if (verifiedAt == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "휴대폰 본인인증을 먼저 완료해주세요.");
    }
    if (LocalDateTime.now().isAfter(verifiedAt.plusMinutes(VERIFIED_TTL_MINUTES))) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "본인인증 후 시간이 초과되었습니다. 다시 인증해주세요.");
    }
    log.info(
        "[PreSignup] SMS 인증 소비 완료: phone={}, purpose={}",
        CommonUtils.maskingPhone(phone),
        purpose);
  }

  /** 만료된 인증 정보/인증 완료 기록 정리 (5분마다 실행). */
  @Scheduled(fixedRate = 300000)
  public void cleanupExpiredCodes() {
    int before = verificationStore.size();
    verificationStore.entrySet().removeIf(entry -> entry.getValue().isExpired());

    LocalDateTime now = LocalDateTime.now();
    verifiedStore
        .entrySet()
        .removeIf(entry -> now.isAfter(entry.getValue().plusMinutes(VERIFIED_TTL_MINUTES)));

    int after = verificationStore.size();
    if (before != after) {
      log.debug("[PreSignup] 만료된 SMS 인증 코드 정리: {} -> {}", before, after);
    }
  }

  // ==================== Private Methods ====================

  /** 저장소 키 생성: "용도:정규화번호". 용도별로 칸을 분리해 cross-purpose 탈취를 방지한다. */
  private String storeKey(String purpose, String normalizedPhone) {
    String resolvedPurpose = (purpose != null && !purpose.isBlank()) ? purpose : PURPOSE_SIGNUP;
    return resolvedPurpose + ":" + normalizedPhone;
  }

  /** 휴대폰번호 정규화 (숫자만 남김). 예: "010-1234-5678" -> "01012345678". */
  private String normalizePhone(String phoneNumber) {
    if (phoneNumber == null) {
      return "";
    }
    return phoneNumber.replaceAll("[^0-9]", "");
  }

  /** 정규화된(숫자만) 휴대폰번호 형식 검증. */
  private boolean isValidPhone(String normalizedPhone) {
    return normalizedPhone.matches("^010\\d{8}$");
  }

  /** 6자리 숫자 OTP 생성 (SecureRandom). */
  private String generateOtpCode() {
    return String.format("%06d", secureRandom.nextInt(1_000_000));
  }

  // ==================== Inner Classes ====================

  /** 인증 정보 (휴대폰번호 키 도메인 전용). */
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
