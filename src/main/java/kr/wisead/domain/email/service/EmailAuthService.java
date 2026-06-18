package kr.wisead.domain.email.service;

import java.time.Duration;
import java.time.LocalDateTime;
import kr.wisead.common.dto.VerificationStatus;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CommonUtils;
import kr.wisead.domain.verification.entity.Verification;
import kr.wisead.mapper.primary.VerificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 로그인/2FA 도메인 이메일 인증 서비스 (key=userId).
 *
 * <p>이미 사용자 ID(seq)가 식별된 흐름(로그인 이메일 2FA, 잠금해제, 휴면복구)에서 사용한다.
 *
 * <p><b>M3: 상태 저장을 DB 로 이전.</b> {@code verification} 테이블(channel=EMAIL, identifier=userId)에
 * purpose 별로 보관한다. 이메일 주소는 발송 후 User 레코드에서 다시 조회 가능하므로 target 컬럼을 사용하지 않는다.
 *
 * <p>만료 행 정리는 {@link SmsVerificationService} 의 {@code @Scheduled} 가 공용 테이블 전체를 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAuthService {

  /** 채널 식별자. */
  private static final String CHANNEL = "EMAIL";

  // ── OTP 용도 상수 (cross-purpose replay 방지) ──────────────────────────────
  /** 로그인 2FA용 OTP */
  public static final String PURPOSE_LOGIN_2FA = "LOGIN_2FA";

  /** 계정 잠금 해제용 OTP */
  public static final String PURPOSE_UNLOCK = "UNLOCK";

  /** 휴면 계정 복구용 OTP */
  public static final String PURPOSE_DORMANT_RECOVERY = "DORMANT_RECOVERY";

  private final EmailService emailService;
  private final VerificationMapper verificationMapper;

  private static final int EXPIRATION_MINUTES = 5;
  private static final int RESEND_LIMIT_SECONDS = 60;
  private static final int MAX_ATTEMPTS = 5;

  /**
   * 인증 코드 발송 (purpose 지정).
   *
   * <p><b>의도적으로 @Transactional 을 달지 않는다.</b> 발송 실패 시 DB 행 롤백을 직접 수행한다.
   */
  public boolean sendVerificationCode(Integer userId, String email, String purpose) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 식별자가 필요합니다.");
    }
    if (!isValidEmail(email)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 이메일 형식입니다.");
    }
    String resolvedPurpose = resolvePurpose(purpose);
    String identifier = String.valueOf(userId);
    LocalDateTime now = LocalDateTime.now();

    Verification existing = verificationMapper.findByKey(resolvedPurpose, CHANNEL, identifier);
    if (existing != null && !canResend(existing.getCreatedAt(), now)) {
      long remaining = remainingResendSeconds(existing.getCreatedAt(), now);
      throw new BusinessException(
          ErrorCode.INVALID_INPUT_VALUE, String.format("재발송은 %d초 후에 가능합니다.", remaining));
    }

    String code = emailService.createVerificationCode();
    if (existing == null) {
      try {
        verificationMapper.insert(
            Verification.builder()
                .purpose(resolvedPurpose)
                .channel(CHANNEL)
                .identifier(identifier)
                .code(code)
                .attempts(0)
                .createdAt(now)
                .build());
      } catch (DataIntegrityViolationException dup) {
        throw new BusinessException(
            ErrorCode.INVALID_INPUT_VALUE, "이미 인증 코드를 발송했습니다. 잠시 후 다시 시도해주세요.");
      }
    } else {
      verificationMapper.updateForSend(
          Verification.builder()
              .purpose(resolvedPurpose)
              .channel(CHANNEL)
              .identifier(identifier)
              .code(code)
              .createdAt(now)
              .build());
    }

    try {
      emailService.sendVerificationEmail(email, code);
      log.info(
          "[이메일 2FA] 코드 발송 완료: userId={}, email={}, purpose={}",
          userId,
          CommonUtils.maskingEmailShort(email),
          resolvedPurpose);
      return true;
    } catch (Exception e) {
      log.error(
          "[이메일 2FA] 코드 발송 실패: userId={}, email={}",
          userId,
          CommonUtils.maskingEmailShort(email),
          e);
      verificationMapper.deleteByKey(resolvedPurpose, CHANNEL, identifier);
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "인증 코드 발송에 실패했습니다.");
    }
  }

  /**
   * 인증 코드 검증 (purpose 검증 포함).
   *
   * <p><b>의도적으로 @Transactional 을 달지 않는다.</b> 불일치 시 incrementAttempts 가 즉시 커밋되어야
   * brute-force 한도가 유지된다.
   */
  public boolean verifyCode(Integer userId, String code, String expectedPurpose) {
    if (userId == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 식별자가 필요합니다.");
    }
    String resolvedPurpose = resolvePurpose(expectedPurpose);
    String identifier = String.valueOf(userId);
    Verification info = verificationMapper.findByKey(resolvedPurpose, CHANNEL, identifier);

    if (info == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드를 먼저 발송해주세요.");
    }
    if (isExpired(info.getCreatedAt())) {
      verificationMapper.deleteByKey(resolvedPurpose, CHANNEL, identifier);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드가 만료되었습니다. 다시 발송해주세요.");
    }
    if (info.getAttempts() >= MAX_ATTEMPTS) {
      verificationMapper.deleteByKey(resolvedPurpose, CHANNEL, identifier);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 시도 횟수를 초과했습니다. 다시 발송해주세요.");
    }

    // 코드 일치 시 원자적으로 행 삭제 (단발 검증)
    int deleted = verificationMapper.deleteIfCodeMatches(resolvedPurpose, CHANNEL, identifier, code);
    if (deleted == 1) {
      log.info("[이메일 2FA] 인증 성공: userId={}, purpose={}", userId, resolvedPurpose);
      return true;
    }

    // 불일치: 시도 횟수 누적
    verificationMapper.incrementAttempts(resolvedPurpose, CHANNEL, identifier);
    Verification reloaded = verificationMapper.findByKey(resolvedPurpose, CHANNEL, identifier);
    int attempts = reloaded != null ? reloaded.getAttempts() : MAX_ATTEMPTS;
    int remaining = MAX_ATTEMPTS - attempts;
    log.warn("[이메일 2FA] 코드 불일치: userId={}, purpose={}, attempts={}", userId, resolvedPurpose, attempts);
    if (remaining <= 0) {
      verificationMapper.deleteByKey(resolvedPurpose, CHANNEL, identifier);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 시도 횟수를 초과했습니다. 다시 발송해주세요.");
    }
    throw new BusinessException(
        ErrorCode.INVALID_INPUT_VALUE,
        String.format("인증 코드가 일치하지 않습니다. (남은 시도: %d회)", remaining));
  }

  /**
   * 인증 상태 확인 (로그인 2FA 기준).
   *
   * <p>로그인 흐름에서 OTP 재사용 여부를 확인하기 위해 PURPOSE_LOGIN_2FA 고정으로 조회한다.
   */
  public VerificationStatus getVerificationStatus(Integer userId) {
    if (userId == null) {
      return new VerificationStatus(false, 0, 0, 0);
    }
    Verification info =
        verificationMapper.findByKey(PURPOSE_LOGIN_2FA, CHANNEL, String.valueOf(userId));
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
   * 인증 코드 재발송 (로그인 2FA — 기존 코드 삭제 후 재발송으로 쿨다운 우회).
   *
   * <p>사용자가 명시적으로 재발송을 선택한 경우이므로 쿨다운을 우회한다.
   */
  public boolean resendVerificationCode(Integer userId, String email) {
    if (userId != null) {
      verificationMapper.deleteByKey(PURPOSE_LOGIN_2FA, CHANNEL, String.valueOf(userId));
    }
    return sendVerificationCode(userId, email, PURPOSE_LOGIN_2FA);
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

  private String resolvePurpose(String purpose) {
    if (purpose == null || purpose.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 용도(purpose)가 필요합니다.");
    }
    return purpose;
  }

  private boolean isValidEmail(String email) {
    if (email == null || email.isBlank()) return false;
    return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
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
