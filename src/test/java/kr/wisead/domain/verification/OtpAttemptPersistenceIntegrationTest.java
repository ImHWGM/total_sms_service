package kr.wisead.domain.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.domain.email.service.EmailAuthService;
import kr.wisead.domain.sms.sender.SmsOtpSender;
import kr.wisead.domain.sms.service.SmsAuthService;
import kr.wisead.domain.verification.entity.Verification;
import kr.wisead.mapper.primary.VerificationMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * OTP 시도 횟수 누적 격리 통합 테스트.
 *
 * <p>핵심 검증: {@link kr.wisead.domain.verification.service.VerificationAttemptPersister}의
 * {@code @Transactional(REQUIRES_NEW)} 가 호출자 트랜잭션 롤백과 독립적으로 커밋되는지 확인.
 *
 * <p>단위 테스트(InMemoryVerificationMapper)로는 Spring AOP 프록시가 동작하지 않아 이 동작을 검증할 수 없다.
 * {@link TransactionTemplate}으로 호출자 {@code @Transactional}(AuthService.login 등)을 시뮬레이션한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/schema-verification.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@DisplayName("OTP 시도 횟수 — REQUIRES_NEW 격리 통합 테스트")
class OtpAttemptPersistenceIntegrationTest {

  private static final Integer USER_ID = 9001;
  private static final String SMS_PURPOSE = "SMS_2FA";
  private static final String EMAIL_PURPOSE = "LOGIN_2FA";
  private static final String SMS_CHANNEL = "SMS";
  private static final String EMAIL_CHANNEL = "EMAIL";
  private static final String IDENTIFIER = String.valueOf(USER_ID);
  private static final String CORRECT_CODE = "391827";

  @Autowired private SmsAuthService smsAuthService;
  @Autowired private EmailAuthService emailAuthService;
  @Autowired private VerificationMapper verificationMapper;
  @Autowired private TransactionTemplate transactionTemplate;

  @MockitoBean private SmsOtpSender smsOtpSender;
  @MockitoBean private kr.wisead.domain.email.service.EmailService emailService;

  @AfterEach
  void cleanup() {
    verificationMapper.deleteByKey(SMS_PURPOSE, SMS_CHANNEL, IDENTIFIER);
    verificationMapper.deleteByKey(EMAIL_PURPOSE, EMAIL_CHANNEL, IDENTIFIER);
  }

  // ==================== SmsAuthService ====================

  @Test
  @DisplayName("SMS — 오답 시 호출자 롤백에도 attempts 가 1로 보존된다")
  void sms_wrongCode_attemptsPersistDespiteCallerRollback() {
    // given: verification 행 직접 삽입 (트랜잭션 없이 auto-commit)
    verificationMapper.insert(
        Verification.builder()
            .purpose(SMS_PURPOSE)
            .channel(SMS_CHANNEL)
            .identifier(IDENTIFIER)
            .target("01012345678")
            .code(CORRECT_CODE)
            .attempts(0)
            .createdAt(LocalDateTime.now())
            .build());

    // when: @Transactional 호출자를 TransactionTemplate 으로 시뮬레이션
    //       verifyCode 오답 → BusinessException → 호출자 트랜잭션 롤백
    assertThatThrownBy(
            () ->
                transactionTemplate.execute(
                    status -> {
                      smsAuthService.verifyCode(USER_ID, "WRONG!"); // 오답
                      return null;
                    }))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("남은 시도");

    // then: 롤백 이후에도 attempts = 1 (REQUIRES_NEW 덕분에 이미 커밋)
    Verification v = verificationMapper.findByKey(SMS_PURPOSE, SMS_CHANNEL, IDENTIFIER);
    assertThat(v).isNotNull();
    assertThat(v.getAttempts())
        .as("호출자 롤백 후에도 incrementAttempts 는 커밋돼야 한다")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("SMS — 오답 5회 누적 시 attempts=5 로 보존되고 이후 즉시 차단된다")
  void sms_fiveWrongAttempts_blockedAfterLimit() {
    // given
    verificationMapper.insert(
        Verification.builder()
            .purpose(SMS_PURPOSE)
            .channel(SMS_CHANNEL)
            .identifier(IDENTIFIER)
            .target("01012345678")
            .code(CORRECT_CODE)
            .attempts(0)
            .createdAt(LocalDateTime.now())
            .build());

    // when: 5회 오답 (각 호출마다 REQUIRES_NEW 로 attempts 누적 커밋)
    //       deleteByKey 는 호출자 TX 에 합류 → 롤백돼 행이 삭제되지 않음 (스케줄러가 정리)
    for (int i = 0; i < 5; i++) {
      try {
        transactionTemplate.execute(
            status -> {
              smsAuthService.verifyCode(USER_ID, "WRONG!");
              return null;
            });
      } catch (BusinessException ignored) {
      }
    }

    // then 1: attempts = 5 로 누적됨
    Verification v = verificationMapper.findByKey(SMS_PURPOSE, SMS_CHANNEL, IDENTIFIER);
    assertThat(v).isNotNull();
    assertThat(v.getAttempts())
        .as("5회 오답 후 attempts 는 5여야 한다")
        .isEqualTo(5);

    // then 2: 6번째 시도는 즉시 '횟수 초과' 로 차단됨 (올바른 코드도 거부)
    assertThatThrownBy(
            () ->
                transactionTemplate.execute(
                    status -> {
                      smsAuthService.verifyCode(USER_ID, CORRECT_CODE);
                      return null;
                    }))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("초과");
  }

  // ==================== EmailAuthService ====================

  @Test
  @DisplayName("Email — 오답 시 호출자 롤백에도 attempts 가 1로 보존된다")
  void email_wrongCode_attemptsPersistDespiteCallerRollback() {
    // given
    verificationMapper.insert(
        Verification.builder()
            .purpose(EMAIL_PURPOSE)
            .channel(EMAIL_CHANNEL)
            .identifier(IDENTIFIER)
            .code(CORRECT_CODE)
            .attempts(0)
            .createdAt(LocalDateTime.now())
            .build());

    // when: unlockByEmailOtp / recoverDormant 의 @Transactional 시뮬레이션
    assertThatThrownBy(
            () ->
                transactionTemplate.execute(
                    status -> {
                      emailAuthService.verifyCode(USER_ID, "WRONG!", EMAIL_PURPOSE);
                      return null;
                    }))
        .isInstanceOf(BusinessException.class);

    // then
    Verification v = verificationMapper.findByKey(EMAIL_PURPOSE, EMAIL_CHANNEL, IDENTIFIER);
    assertThat(v).isNotNull();
    assertThat(v.getAttempts())
        .as("호출자 롤백 후에도 incrementAttempts 는 커밋돼야 한다")
        .isEqualTo(1);
  }
}
