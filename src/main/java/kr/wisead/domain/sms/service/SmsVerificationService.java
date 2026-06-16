package kr.wisead.domain.sms.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import kr.wisead.common.dto.VerificationStatus;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CommonUtils;
import kr.wisead.domain.sms.sender.SmsOtpSender;
import kr.wisead.domain.verification.entity.Verification;
import kr.wisead.mapper.primary.VerificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 사전 인증(로그인 전) SMS 휴대폰 인증 서비스.
 *
 * <p>아직 로그인된 사용자 식별자(userId/seq)가 없는 흐름(회원가입·아이디찾기·비밀번호찾기 등)에서 공용으로 쓴다. 용도는 호출자가
 * {@code purpose} 로 지정한다(예: {@link #PURPOSE_SIGNUP}).
 *
 * <p><b>M3: 상태 저장을 DB 로 이전 + 채널 통합.</b> 인증 상태(발송 코드/시도횟수/인증완료 도장)를 인스턴스 메모리가 아닌 공용
 * {@code verification} 테이블(channel=SMS)에 보관한다. 이메일 사전인증({@link
 * kr.wisead.domain.email.service.EmailVerificationService}, channel=EMAIL)과 동일 테이블/매퍼를 공유하며, 식별
 * 단위는 (purpose, channel, identifier=정규화 휴대폰번호) 이다. 로그인 2FA({@link SmsAuthService})는 별개(userId 기반
 * in-memory).
 *
 * <p>SMS 발송은 {@link SmsOtpSender} 를 통해 결제/야간/잔액 검증을 우회하여 큐에 직접 적재한다.
 *
 * <p>검증 성공 시 {@code verified_at} 도장을 찍어 30분간 유지하고, 회원가입({@code signUp})에서 {@link
 * #checkVerified(String, String)} 게이트로 검사한 뒤 가입 성공 시 {@link #consumeVerification(String, String)} 로
 * 소비한다. consume 는 signUp 트랜잭션에 합류하므로, 가입이 롤백되면 도장 삭제도 함께 롤백되어 인증 상태가 보존된다(리뷰 M2).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsVerificationService {

  /** 회원가입 본인인증 용도. */
  public static final String PURPOSE_SIGNUP = "SIGNUP";

  /** 채널 식별자 (verification.channel). */
  private static final String CHANNEL = "SMS";

  private final SmsOtpSender smsOtpSender;
  private final VerificationMapper verificationMapper;

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
    String p = resolvePurpose(purpose);
    LocalDateTime now = LocalDateTime.now();

    Verification existing = verificationMapper.findByKey(p, CHANNEL, phone);
    if (existing != null && !canResend(existing.getCreatedAt(), now)) {
      long remainingSeconds = remainingResendSeconds(existing.getCreatedAt(), now);
      throw new BusinessException(
          ErrorCode.INVALID_INPUT_VALUE, String.format("재발송은 %d초 후에 가능합니다.", remainingSeconds));
    }

    String code = generateOtpCode();
    if (existing == null) {
      verificationMapper.insert(
          Verification.builder()
              .purpose(p)
              .channel(CHANNEL)
              .identifier(phone)
              .code(code)
              .attempts(0)
              .createdAt(now)
              .verifiedAt(null)
              .build());
    } else {
      // 재발송: 코드/발송시각 갱신 + 시도횟수·도장 리셋
      verificationMapper.updateForSend(
          Verification.builder()
              .purpose(p)
              .channel(CHANNEL)
              .identifier(phone)
              .code(code)
              .createdAt(now)
              .build());
    }

    try {
      smsOtpSender.sendOtp(phone, code);
      log.info("[SMS인증] 코드 발송 완료: phone={}, purpose={}", CommonUtils.maskingPhone(phone), p);
      return true;
    } catch (Exception e) {
      log.error("[SMS인증] 코드 발송 실패: phone={}, purpose={}", CommonUtils.maskingPhone(phone), p, e);
      verificationMapper.deleteByKey(p, CHANNEL, phone);
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SMS 인증 코드 발송에 실패했습니다.");
    }
  }

  /**
   * 인증 코드 검증. 성공 시 verified_at 도장을 찍고 code 를 무효화한다.
   *
   * <p><b>의도적으로 @Transactional 을 달지 않는다.</b> 코드 불일치 시 incrementAttempts(시도횟수 +1)를 DB 에
   * 반영한 뒤 BusinessException 을 던지는데, 만약 이 메서드가 @Transactional 이면 예외로 트랜잭션이 롤백되어 시도횟수
   * 증가가 취소된다 → brute-force 한도(MAX_ATTEMPTS)가 무력화된다. 트랜잭션 없이 각 mapper 호출이 개별 커밋되어야
   * "실패 시도는 누적되고 예외는 던진다"가 성립한다.
   *
   * @param phoneNumber 휴대폰번호
   * @param code 입력 코드
   * @param purpose OTP 용도 — 발송 시점과 동일해야 검증 가능
   */
  public boolean verifyCode(String phoneNumber, String code, String purpose) {
    String phone = normalizePhone(phoneNumber);
    String p = resolvePurpose(purpose);
    LocalDateTime now = LocalDateTime.now();

    Verification info = verificationMapper.findByKey(p, CHANNEL, phone);
    if (info == null || info.getCode() == null) {
      // code == null: 미발송이거나 이미 검증되어 소비된 상태
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드를 먼저 발송해주세요.");
    }

    if (isExpired(info.getCreatedAt(), now)) {
      verificationMapper.deleteByKey(p, CHANNEL, phone);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드가 만료되었습니다. 다시 발송해주세요.");
    }

    if (info.getAttempts() >= MAX_ATTEMPTS) {
      verificationMapper.deleteByKey(p, CHANNEL, phone);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 시도 횟수를 초과했습니다. 다시 발송해주세요.");
    }

    // 코드 일치 + 미인증일 때만 원자적으로 도장 처리
    int matched = verificationMapper.markVerifiedIfCodeMatches(p, CHANNEL, phone, code, now);
    if (matched == 1) {
      log.info("[SMS인증] 성공: phone={}, purpose={}", CommonUtils.maskingPhone(phone), p);
      return true;
    }

    // 불일치 → 시도 횟수 원자적 증가 후 안내
    verificationMapper.incrementAttempts(p, CHANNEL, phone);
    Verification reloaded = verificationMapper.findByKey(p, CHANNEL, phone);
    int attempts = reloaded != null ? reloaded.getAttempts() : MAX_ATTEMPTS;
    log.warn(
        "[SMS인증] 코드 불일치: phone={}, purpose={}, attempts={}",
        CommonUtils.maskingPhone(phone),
        p,
        attempts);
    int remaining = MAX_ATTEMPTS - attempts;
    if (remaining <= 0) {
      verificationMapper.deleteByKey(p, CHANNEL, phone);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 시도 횟수를 초과했습니다. 다시 발송해주세요.");
    }
    throw new BusinessException(
        ErrorCode.INVALID_INPUT_VALUE,
        String.format("인증 코드가 일치하지 않습니다. (남은 시도: %d회)", remaining));
  }

  /** 인증 상태 확인. (검증 완료되어 code 가 소비된 행은 codeSent=false 로 본다.) */
  public VerificationStatus getVerificationStatus(String phoneNumber, String purpose) {
    Verification info =
        verificationMapper.findByKey(resolvePurpose(purpose), CHANNEL, normalizePhone(phoneNumber));
    if (info == null || info.getCode() == null) {
      return new VerificationStatus(false, 0, 0, 0);
    }
    LocalDateTime now = LocalDateTime.now();
    long remainingSeconds = remainingSeconds(info.getCreatedAt(), now);
    long remainingResendSeconds = remainingResendSeconds(info.getCreatedAt(), now);
    int remainingAttempts = MAX_ATTEMPTS - info.getAttempts();
    return new VerificationStatus(
        true, remainingSeconds, remainingResendSeconds, remainingAttempts);
  }

  /**
   * 인증 코드 재발송.
   *
   * <p>H1: 기존 코드를 먼저 제거하지 않는다. {@link #sendVerificationCode} 가 created_at 기준 60초 쿨다운을 검사한 뒤 통과 시에만
   * 새 코드로 덮어쓰므로, resend 경로도 쿨다운을 동일하게 적용받는다.
   */
  public boolean resendVerificationCode(String phoneNumber, String purpose) {
    return sendVerificationCode(phoneNumber, purpose);
  }

  /**
   * 회원가입 게이트용 — 번호가 인증 완료 상태인지 *소비하지 않고* 검사만 한다(M2).
   *
   * <p>signUp 초입에서 미인증/유예초과를 조기 거부하기 위한 용도. 실제 소비는 가입 처리가 끝난 뒤 {@link
   * #consumeVerification(String, String)} 으로 수행한다.
   */
  public void checkVerified(String phoneNumber, String purpose) {
    Verification info =
        verificationMapper.findByKey(resolvePurpose(purpose), CHANNEL, normalizePhone(phoneNumber));
    requireValidStamp(info);
  }

  /**
   * 회원가입 강제 검사용 — 번호가 인증 완료 상태인지 확인하고, 맞으면 행을 삭제(1회용 소비)한다.
   *
   * <p>signUp 의 트랜잭션에 합류하므로(별도 propagation 미지정), 가입이 롤백되면 이 삭제도 함께 롤백되어 인증 상태가 보존된다(M2).
   */
  public void consumeVerification(String phoneNumber, String purpose) {
    String phone = normalizePhone(phoneNumber);
    String p = resolvePurpose(purpose);
    Verification info = verificationMapper.findByKey(p, CHANNEL, phone);
    requireValidStamp(info);
    verificationMapper.deleteByKey(p, CHANNEL, phone);
    log.info("[SMS인증] 소비 완료: phone={}, purpose={}", CommonUtils.maskingPhone(phone), p);
  }

  /** 만료된 인증 행 정리 (5분마다 실행). 채널 무관 전체 정리이므로 SMS·EMAIL 공통으로 동작한다. */
  @Scheduled(fixedRate = 300000)
  public void cleanupExpiredCodes() {
    LocalDateTime now = LocalDateTime.now();
    int deleted =
        verificationMapper.deleteExpired(
            now.minusMinutes(EXPIRATION_MINUTES), now.minusMinutes(VERIFIED_TTL_MINUTES));
    if (deleted > 0) {
      log.debug("[인증] 만료된 인증 행 정리: {} 건", deleted);
    }
  }

  // ==================== Private Methods ====================

  /** 인증 완료 도장 유효성 검사 (미인증/유예초과 시 예외). */
  private void requireValidStamp(Verification info) {
    if (info == null || info.getVerifiedAt() == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "휴대폰 본인인증을 먼저 완료해주세요.");
    }
    if (LocalDateTime.now().isAfter(info.getVerifiedAt().plusMinutes(VERIFIED_TTL_MINUTES))) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "본인인증 후 시간이 초과되었습니다. 다시 인증해주세요.");
    }
  }

  private String resolvePurpose(String purpose) {
    // 범용 저장소이므로 용도를 반드시 명시받는다. 빠뜨린 호출을 조용히 SIGNUP 으로 처리하지 않는다.
    if (purpose == null || purpose.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 용도(purpose)가 필요합니다.");
    }
    return purpose;
  }

  /** 휴대폰번호 정규화 (숫자만 남김). 예: "010-1234-5678" -> "01012345678". */
  private String normalizePhone(String phoneNumber) {
    if (phoneNumber == null) {
      return "";
    }
    return phoneNumber.replaceAll("[^0-9]", "");
  }

  private boolean isValidPhone(String normalizedPhone) {
    return normalizedPhone.matches("^010\\d{8}$");
  }

  private String generateOtpCode() {
    return String.format("%06d", secureRandom.nextInt(1_000_000));
  }

  private boolean isExpired(LocalDateTime createdAt, LocalDateTime now) {
    return now.isAfter(createdAt.plusMinutes(EXPIRATION_MINUTES));
  }

  private boolean canResend(LocalDateTime createdAt, LocalDateTime now) {
    return now.isAfter(createdAt.plusSeconds(RESEND_LIMIT_SECONDS));
  }

  private long remainingSeconds(LocalDateTime createdAt, LocalDateTime now) {
    long remaining = Duration.between(now, createdAt.plusMinutes(EXPIRATION_MINUTES)).getSeconds();
    return Math.max(0, remaining); // L1: 음수 노출 방지
  }

  private long remainingResendSeconds(LocalDateTime createdAt, LocalDateTime now) {
    long remaining = Duration.between(now, createdAt.plusSeconds(RESEND_LIMIT_SECONDS)).getSeconds();
    return Math.max(0, remaining);
  }
}
