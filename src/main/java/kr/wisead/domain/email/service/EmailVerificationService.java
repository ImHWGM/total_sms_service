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
 * 사전 인증(로그인 전) 이메일 인증 서비스.
 *
 * <p><b>M3: 상태 저장을 DB 로 이전.</b> 종전 in-memory 이메일 사전인증을 대체하며, SMS 사전인증과 동일한 공용
 * {@code verification} 테이블(channel=EMAIL)을 {@link VerificationMapper} 로 공유한다. 식별 단위는 (purpose,
 * channel=EMAIL, identifier=이메일 소문자) 이다.
 *
 * <p>회원가입 흐름은 이메일을 게이트로 쓰지 않으므로(SMS 와 달리) checkVerified/consume 가 없고, 검증 성공 시 즉시 행을 제거한다.
 * 만료 정리 스케줄러는 {@link kr.wisead.domain.sms.service.SmsVerificationService#cleanupExpiredCodes()} 가
 * 테이블 전체(양 채널)를 정리하므로 별도로 두지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

  /** 회원가입 이메일 인증 용도. */
  public static final String PURPOSE_SIGNUP = "SIGNUP";

  /** 아이디 찾기 이메일 인증 용도. */
  public static final String PURPOSE_FIND_ID = "FIND_ID";

  /** 채널 식별자 (verification.channel). */
  private static final String CHANNEL = "EMAIL";

  private final EmailService emailService;
  private final VerificationMapper verificationMapper;

  private static final int EXPIRATION_MINUTES = 5;
  private static final int RESEND_LIMIT_SECONDS = 60;
  private static final int MAX_ATTEMPTS = 5;

  // ==================== Public API ====================

  /** 인증 코드 발송 (회원가입). */
  public boolean sendVerificationCode(String email) {
    if (!isValidEmail(email)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 이메일 형식입니다.");
    }
    return doSend(email, PURPOSE_SIGNUP, null);
  }

  /**
   * 인증 코드 발송 (아이디 찾기).
   *
   * <p>발송 시점에 특정된 {@code target}(user.seq 문자열)을 verification 행에 함께 보관한다. 검증 성공 후
   * {@link #verifyAndGetTarget} 으로 seq 를 꺼내 {@code findBySeq} 로 정확한 사용자를 조회하기 위함.
   * 이메일에 UNIQUE 제약이 없어 {@code findByEmail} 로 재조회하면 오조회 위험이 있다.
   *
   * @param email 발송 대상 이메일
   * @param target 보관할 user.seq 문자열
   */
  public boolean sendVerificationCode(String email, String target) {
    if (!isValidEmail(email)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 이메일 형식입니다.");
    }
    return doSend(email, PURPOSE_FIND_ID, target);
  }

  /**
   * 인증 코드 검증 (회원가입). 성공 시 행을 제거한다.
   *
   * <p><b>@Transactional 금지.</b> 코드 불일치 시 incrementAttempts 가 즉시 커밋되어야 brute-force 한도가
   * 유지된다. 호출자({@link kr.wisead.domain.user.service.UserService} 등)도 이 메서드를 @Transactional
   * 경계 안에서 호출해선 안 된다.
   */
  public boolean verifyCode(String email, String code) {
    doVerify(email, code, PURPOSE_SIGNUP);
    return true;
  }

  /**
   * 인증 코드 검증 (아이디 찾기). 성공 시 보관된 {@code target}(user.seq 문자열)을 반환한다.
   *
   * <p><b>@Transactional 금지.</b> {@link #verifyCode} 와 동일한 이유. 실패 시 {@link BusinessException}
   * 을 던진다.
   */
  public String verifyAndGetTarget(String email, String code) {
    return doVerify(email, code, PURPOSE_FIND_ID);
  }

  /** 인증 상태 확인. */
  public VerificationStatus getVerificationStatus(String email) {
    Verification info = verificationMapper.findByKey(PURPOSE_SIGNUP, CHANNEL, email.toLowerCase());
    if (info == null || info.getCode() == null) {
      return new VerificationStatus(false, 0, 0, 0);
    }
    LocalDateTime now = LocalDateTime.now();
    return new VerificationStatus(
        true,
        remainingSeconds(info.getCreatedAt(), now),
        remainingResendSeconds(info.getCreatedAt(), now),
        MAX_ATTEMPTS - info.getAttempts());
  }

  /** 인증 코드 재발송. (H1: 쿨다운 검사를 우회하지 않도록 send 에 위임) */
  public boolean resendVerificationCode(String email) {
    return sendVerificationCode(email);
  }

  // ==================== Private Core Logic ====================

  /**
   * 인증 코드 발송 공용 구현. purpose·target 만 다르고 나머지 흐름(쿨다운/insert-race/발송실패 롤백)이 동일하여 통합.
   */
  private boolean doSend(String email, String purpose, String target) {
    String id = email.toLowerCase();
    LocalDateTime now = LocalDateTime.now();

    Verification existing = verificationMapper.findByKey(purpose, CHANNEL, id);
    if (existing != null && !canResend(existing.getCreatedAt(), now)) {
      long remainingSeconds = remainingResendSeconds(existing.getCreatedAt(), now);
      throw new BusinessException(
          ErrorCode.INVALID_INPUT_VALUE, String.format("재발송은 %d초 후에 가능합니다.", remainingSeconds));
    }

    String code = emailService.createVerificationCode();
    if (existing == null) {
      try {
        verificationMapper.insert(
            Verification.builder()
                .purpose(purpose)
                .channel(CHANNEL)
                .identifier(id)
                .target(target)
                .code(code)
                .attempts(0)
                .createdAt(now)
                .verifiedAt(null)
                .build());
      } catch (DataIntegrityViolationException dup) {
        // 동시 최초발송(더블클릭): 다른 요청이 방금 같은 키 행을 만들어 UNIQUE 충돌 → 500 대신 안내로 변환
        throw new BusinessException(
            ErrorCode.INVALID_INPUT_VALUE, "이미 인증 코드를 발송했습니다. 잠시 후 다시 시도해주세요.");
      }
    } else {
      verificationMapper.updateForSend(
          Verification.builder()
              .purpose(purpose)
              .channel(CHANNEL)
              .identifier(id)
              .target(target)
              .code(code)
              .createdAt(now)
              .build());
    }

    try {
      emailService.sendVerificationEmail(email, code);
      log.info("[{}] 코드 발송 완료: email={}", purpose, CommonUtils.maskingEmailShort(email));
      return true;
    } catch (Exception e) {
      log.error("[{}] 코드 발송 실패: email={}", purpose, CommonUtils.maskingEmailShort(email), e);
      verificationMapper.deleteByKey(purpose, CHANNEL, id);
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "인증 코드 발송에 실패했습니다.");
    }
  }

  /** 인증 코드 검증 공용 구현. 성공 시 행을 제거하고 {@code target} 을 반환한다(SIGNUP 은 null). */
  private String doVerify(String email, String code, String purpose) {
    String id = email.toLowerCase();
    LocalDateTime now = LocalDateTime.now();

    Verification info = verificationMapper.findByKey(purpose, CHANNEL, id);
    if (info == null || info.getCode() == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드를 먼저 발송해주세요.");
    }
    if (isExpired(info.getCreatedAt(), now)) {
      verificationMapper.deleteByKey(purpose, CHANNEL, id);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드가 만료되었습니다. 다시 발송해주세요.");
    }
    if (info.getAttempts() >= MAX_ATTEMPTS) {
      verificationMapper.deleteByKey(purpose, CHANNEL, id);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 시도 횟수를 초과했습니다. 다시 발송해주세요.");
    }

    String target = info.getTarget();
    int matched = verificationMapper.markVerifiedIfCodeMatches(purpose, CHANNEL, id, code, now);
    if (matched == 1) {
      verificationMapper.deleteByKey(purpose, CHANNEL, id);
      log.info("[{}] 이메일 인증 성공: email={}", purpose, CommonUtils.maskingEmailShort(email));
      return target;
    }

    verificationMapper.incrementAttempts(purpose, CHANNEL, id);
    Verification reloaded = verificationMapper.findByKey(purpose, CHANNEL, id);
    int attempts = reloaded != null ? reloaded.getAttempts() : MAX_ATTEMPTS;
    log.warn("[{}] 코드 불일치: email={}, attempts={}", purpose, CommonUtils.maskingEmailShort(email), attempts);
    int remaining = MAX_ATTEMPTS - attempts;
    if (remaining <= 0) {
      verificationMapper.deleteByKey(purpose, CHANNEL, id);
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 시도 횟수를 초과했습니다. 다시 발송해주세요.");
    }
    throw new BusinessException(
        ErrorCode.INVALID_INPUT_VALUE,
        String.format("인증 코드가 일치하지 않습니다. (남은 시도: %d회)", remaining));
  }

  // ==================== Private Helpers ====================

  private boolean isValidEmail(String email) {
    if (email == null || email.isBlank()) {
      return false;
    }
    return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
  }

  private boolean isExpired(LocalDateTime createdAt, LocalDateTime now) {
    return now.isAfter(createdAt.plusMinutes(EXPIRATION_MINUTES));
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
