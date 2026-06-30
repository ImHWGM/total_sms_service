package kr.wisead.domain.verification.service;

import kr.wisead.mapper.primary.VerificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 시도 횟수 누적 전용 빈.
 *
 * <p>호출자(AuthService, TwoFactorService)가 {@code @Transactional} 을 가져도 incrementAttempts 가
 * 독립적으로 커밋되어야 brute-force 한도가 보존된다. {@code REQUIRES_NEW} 로 호출자 트랜잭션을 중단하고
 * 별도 트랜잭션을 열어 즉시 커밋한다.
 *
 * <p>{@link kr.wisead.domain.sms.service.SmsAuthService} 와
 * {@link kr.wisead.domain.email.service.EmailAuthService} 에서 공유한다.
 */
@Service
@RequiredArgsConstructor
public class VerificationAttemptPersister {

  private final VerificationMapper verificationMapper;

  /**
   * 시도 횟수를 1 증가하고 즉시 커밋한다.
   *
   * <p>호출자 트랜잭션이 이후 rollback 돼도 이 커밋은 취소되지 않는다.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void persistIncrement(String purpose, String channel, String identifier) {
    verificationMapper.incrementAttempts(purpose, channel, identifier);
  }
}
