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
import kr.wisead.domain.verification.service.VerificationAttemptPersister;
import kr.wisead.mapper.primary.VerificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 로그인/2FA 도메인 SMS 인증 서비스 (key=userId).
 *
 * <p>이미 사용자 ID(seq)가 식별된 흐름(로그인 SMS 2FA, 마이페이지 SMS 등록)에서 사용한다.
 *
 * <p><b>M3: 상태 저장을 DB 로 이전.</b> {@code verification} 테이블(purpose=SMS_2FA, channel=SMS,
 * identifier=userId)에 인증 상태를 보관한다. 발송 대상 전화번호는 {@code target} 컬럼에 저장하며,
 * {@link #verifyCodeAndGetPhone} 에서 반환한다.
 *
 * <p>만료 행 정리는 {@link SmsVerificationService} 의 {@code @Scheduled} 가 공용 테이블 전체를 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsAuthService {

  /** 로그인/마이페이지 SMS 인증 용도. */
  private static final String PURPOSE_SMS_2FA = "SMS_2FA";

  /** 채널 식별자. */
  private static final String CHANNEL = "SMS";

  private final SmsOtpSender smsOtpSender;
  private final VerificationMapper verificationMapper;
  private final VerificationAttemptPersister attemptPersister;

  private final SecureRandom secureRandom = new SecureRandom();

  private static final int EXPIRATION_MINUTES = 5;
  private static final int RESEND_LIMIT_SECONDS = 60;
  private static final int MAX_ATTEMPTS = 5;

  /**
   * SMS 인증 코드 발송.
   *
   * @param userId 사용자 seq (식별자)
   * @param phoneNumber 발송 대상 휴대폰번호 (target 컬럼에도 보관)
   */
  public void sendVerificationCode(Integer userId, String phoneNumber) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 식별자가 필요합니다.");
    }
    if (phoneNumber == null || phoneNumber.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "휴대폰번호가 필요합니다.");
    }
    String phone = normalizePhone(phoneNumber);
    String identifier = String.valueOf(userId);
    LocalDateTime now = LocalDateTime.now();

    Verification existing = verificationMapper.findByKey(PURPOSE_SMS_2FA, CHANNEL, identifier);
    if (existing != null && !canResend(existing.getCreatedAt(), now)) {
      long remaining = remainingResendSeconds(existing.getCreatedAt(), now);
      throw new BusinessException(
          ErrorCode.INVALID_INPUT_VALUE, String.format("재발송은 %d초 후에 가능합니다.", remaining));
    }

    String code = generateOtpCode();
    if (existing == null) {
      try {
        verificationMapper.insert(
            Verification.builder()
                .purpose(PURPOSE_SMS_2FA)
                .channel(CHANNEL)
                .identifier(identifier)
                .target(phone)
                .code(code)
                .attempts(0)
                .createdAt(now)
                .build());
      } catch (DuplicateKeyException dup) {
        throw new BusinessException(
            ErrorCode.INVALID_INPUT_VALUE, "이미 인증 코드를 발송했습니다. 잠시 후 다시 시도해주세요.");
      }
    } else {
      verificationMapper.updateForSend(
          Verification.builder()
              .purpose(PURPOSE_SMS_2FA)
              .channel(CHANNEL)
              .identifier(identifier)
              .target(phone)
              .code(code)
              .createdAt(now)
              .build());
    }

    try {
      smsOtpSender.sendOtp(phone, code);
      log.info("[SMS 2FA] 코드 발송 완료: userId={}, phone={}", userId, CommonUtils.maskingPhone(phone));
    } catch (Exception e) {
      log.error("[SMS 2FA] 코드 발송 실패: userId={}", userId, e);
      verificationMapper.deleteByKey(PURPOSE_SMS_2FA, CHANNEL, identifier);
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "SMS 인증 코드 발송에 실패했습니다.");
    }
  }

  /**
   * SMS 인증 코드 검증.
   *
   * <p><b>의도적으로 @Transactional 을 달지 않는다.</b> 코드 불일치 시 incrementAttempts 가 즉시 커밋되어
   * brute-force 한도가 유지된다. @Transactional 이면 예외 시 rollback 되어 시도 횟수가 취소된다.
   */
  public void verifyCode(Integer userId, String code) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 식별자가 필요합니다.");
    }
    String identifier = String.valueOf(userId);
    Verification info = verificationMapper.findByKey(PURPOSE_SMS_2FA, CHANNEL, identifier);

    if (info == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드를 먼저 발송해주세요.");
    }
    if (isExpired(info.getCreatedAt())) {
      verificationMapper.deleteByKey(PURPOSE_SMS_2FA, CHANNEL, identifier);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드가 만료되었습니다. 다시 발송해주세요.");
    }
    if (info.getAttempts() >= MAX_ATTEMPTS) {
      verificationMapper.deleteByKey(PURPOSE_SMS_2FA, CHANNEL, identifier);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 시도 횟수를 초과했습니다. 다시 발송해주세요.");
    }

    // 코드 일치 시 원자적으로 행 삭제 (단발 검증)
    int deleted = verificationMapper.deleteIfCodeMatches(PURPOSE_SMS_2FA, CHANNEL, identifier, code);
    if (deleted == 1) {
      log.info("[SMS 2FA] 인증 성공: userId={}", userId);
      return;
    }

    // 불일치: 시도 횟수 누적 (REQUIRES_NEW 트랜잭션 — 호출자 rollback 에 영향 없이 즉시 커밋)
    attemptPersister.persistIncrement(PURPOSE_SMS_2FA, CHANNEL, identifier);
    Verification reloaded = verificationMapper.findByKey(PURPOSE_SMS_2FA, CHANNEL, identifier);
    int attempts = reloaded != null ? reloaded.getAttempts() : MAX_ATTEMPTS;
    int remaining = MAX_ATTEMPTS - attempts;
    log.warn("[SMS 2FA] 코드 불일치: userId={}, attempts={}", userId, attempts);
    if (remaining <= 0) {
      verificationMapper.deleteByKey(PURPOSE_SMS_2FA, CHANNEL, identifier);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 시도 횟수를 초과했습니다. 다시 발송해주세요.");
    }
    throw new BusinessException(
        ErrorCode.INVALID_INPUT_VALUE,
        String.format("인증 코드가 일치하지 않습니다. (남은 시도: %d회)", remaining));
  }

  /**
   * SMS 인증 코드 검증 + 검증된 phoneNumber 반환 (마이페이지 SMS 등록 전용).
   *
   * <p>성공 시 발송 당시 {@code target} 에 저장된 전화번호를 반환한다.
   *
   * <p><b>의도적으로 @Transactional 을 달지 않는다.</b> (verifyCode 와 동일한 이유)
   */
  public String verifyCodeAndGetPhone(Integer userId, String code) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 식별자가 필요합니다.");
    }
    String identifier = String.valueOf(userId);
    Verification info = verificationMapper.findByKey(PURPOSE_SMS_2FA, CHANNEL, identifier);

    if (info == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드를 먼저 발송해주세요.");
    }
    if (isExpired(info.getCreatedAt())) {
      verificationMapper.deleteByKey(PURPOSE_SMS_2FA, CHANNEL, identifier);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드가 만료되었습니다. 다시 발송해주세요.");
    }
    if (info.getAttempts() >= MAX_ATTEMPTS) {
      verificationMapper.deleteByKey(PURPOSE_SMS_2FA, CHANNEL, identifier);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 시도 횟수를 초과했습니다. 다시 발송해주세요.");
    }

    // target(전화번호) 을 먼저 읽어두고 원자적 삭제 시도
    String phone = info.getTarget();

    int deleted = verificationMapper.deleteIfCodeMatches(PURPOSE_SMS_2FA, CHANNEL, identifier, code);
    if (deleted == 1) {
      log.info("[SMS 2FA] 인증 성공(phone 반환): userId={}", userId);
      return phone;
    }

    // REQUIRES_NEW 트랜잭션 — 호출자 rollback 에 영향 없이 즉시 커밋
    attemptPersister.persistIncrement(PURPOSE_SMS_2FA, CHANNEL, identifier);
    Verification reloaded = verificationMapper.findByKey(PURPOSE_SMS_2FA, CHANNEL, identifier);
    int attempts = reloaded != null ? reloaded.getAttempts() : MAX_ATTEMPTS;
    int remaining = MAX_ATTEMPTS - attempts;
    log.warn("[SMS 2FA] 코드 불일치(verifyAndGetPhone): userId={}, attempts={}", userId, attempts);
    if (remaining <= 0) {
      verificationMapper.deleteByKey(PURPOSE_SMS_2FA, CHANNEL, identifier);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 시도 횟수를 초과했습니다. 다시 발송해주세요.");
    }
    throw new BusinessException(
        ErrorCode.INVALID_INPUT_VALUE,
        String.format("인증 코드가 일치하지 않습니다. (남은 시도: %d회)", remaining));
  }

  /** 인증 상태 확인. */
  public VerificationStatus getVerificationStatus(Integer userId) {
    if (userId == null) {
      return new VerificationStatus(false, 0, 0, 0);
    }
    Verification info =
        verificationMapper.findByKey(PURPOSE_SMS_2FA, CHANNEL, String.valueOf(userId));
    if (info == null || info.getCode() == null) {
      return new VerificationStatus(false, 0, 0, 0);
    }
    LocalDateTime now = LocalDateTime.now();
    long remainingSeconds = remainingSeconds(info.getCreatedAt(), now);
    long remainingResendSeconds = remainingResendSeconds(info.getCreatedAt(), now);
    int remainingAttempts = MAX_ATTEMPTS - info.getAttempts();
    return new VerificationStatus(true, remainingSeconds, remainingResendSeconds, remainingAttempts);
  }

  /**
   * SMS 인증 코드 재발송.
   *
   * <p>기존 행을 삭제한 뒤 재발송하여 쿨다운을 우회한다(현재 로그인 UX 에서 사용자가 명시적으로 재발송을 선택하는 경우).
   */
  public void resendVerificationCode(Integer userId, String phoneNumber) {
    if (userId != null) {
      verificationMapper.deleteByKey(PURPOSE_SMS_2FA, CHANNEL, String.valueOf(userId));
    }
    sendVerificationCode(userId, phoneNumber);
  }

  /**
   * 사용자에 묶인 인증 정보 강제 무효화 (채널 전환 시 cross-channel 정리용).
   *
   * <p>purpose 무관하게 해당 채널의 모든 행을 삭제한다.
   */
  public void invalidate(Integer userId) {
    if (userId == null) {
      return;
    }
    verificationMapper.deleteByChannelAndIdentifier(CHANNEL, String.valueOf(userId));
  }

  // ==================== Private Methods ====================

  private String normalizePhone(String phoneNumber) {
    if (phoneNumber == null) return "";
    return phoneNumber.replaceAll("[^0-9]", "");
  }

  private String generateOtpCode() {
    return String.format("%06d", secureRandom.nextInt(1_000_000));
  }

  private boolean isExpired(LocalDateTime createdAt) {
    return LocalDateTime.now().isAfter(createdAt.plusMinutes(EXPIRATION_MINUTES));
  }

  private boolean canResend(LocalDateTime createdAt, LocalDateTime now) {
    return now.isAfter(createdAt.plusSeconds(RESEND_LIMIT_SECONDS));
  }

  private long remainingSeconds(LocalDateTime createdAt, LocalDateTime now) {
    long remaining = Duration.between(now, createdAt.plusMinutes(EXPIRATION_MINUTES)).getSeconds();
    return Math.max(0, remaining);
  }

  private long remainingResendSeconds(LocalDateTime createdAt, LocalDateTime now) {
    long remaining = Duration.between(now, createdAt.plusSeconds(RESEND_LIMIT_SECONDS)).getSeconds();
    return Math.max(0, remaining);
  }
}
