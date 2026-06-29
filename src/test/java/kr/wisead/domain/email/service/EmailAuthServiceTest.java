package kr.wisead.domain.email.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import kr.wisead.common.dto.VerificationStatus;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.domain.verification.InMemoryVerificationMapper;
import kr.wisead.domain.verification.service.VerificationAttemptPersister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * EmailAuthService 단위 테스트 — DB 기반(M3) fake mapper 사용.
 *
 * <p>plan §4 Phase B-0-8.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EmailAuthService (로그인 이메일 2FA, DB 기반)")
class EmailAuthServiceTest {

  private static final Integer USER_ID = 42;
  private static final String EMAIL = "user@example.com";
  private static final String CODE = "Z9Y8X7";
  private static final String CHANNEL = "EMAIL";
  private static final String LOGIN_2FA = EmailAuthService.PURPOSE_LOGIN_2FA;
  private static final String UNLOCK = EmailAuthService.PURPOSE_UNLOCK;

  @Mock private EmailService emailService;

  private InMemoryVerificationMapper mapper;
  private EmailAuthService sut;

  @BeforeEach
  void setUp() {
    mapper = new InMemoryVerificationMapper();
    sut = new EmailAuthService(emailService, mapper, new VerificationAttemptPersister(mapper));
    when(emailService.createVerificationCode()).thenReturn(CODE);
    doNothing().when(emailService).sendVerificationEmail(anyString(), anyString());
  }

  @Test
  @DisplayName("getVerificationStatus: 발송 후 codeSent=true, 상태 정상")
  void getVerificationStatus_byUserIdReturnsStatus() {
    sut.sendVerificationCode(USER_ID, EMAIL, LOGIN_2FA);

    VerificationStatus status = sut.getVerificationStatus(USER_ID);

    assertThat(status.codeSent()).isTrue();
    assertThat(status.remainingSeconds()).isPositive();
    assertThat(status.remainingAttempts()).isEqualTo(5);
    verify(emailService).sendVerificationEmail(eq(EMAIL), eq(CODE));
  }

  @Test
  @DisplayName("verifyCode: 올바른 코드 검증 성공 → DB 행 삭제")
  void verifyCode_succeedsWithCorrectCode() {
    sut.sendVerificationCode(USER_ID, EMAIL, LOGIN_2FA);

    boolean result = sut.verifyCode(USER_ID, CODE, LOGIN_2FA);

    assertThat(result).isTrue();
    assertThat(sut.getVerificationStatus(USER_ID).codeSent()).isFalse();
  }

  @Test
  @DisplayName("verifyCode: 발송 없이 검증 시 BusinessException")
  void verifyCode_withoutSend_throwsException() {
    assertThatThrownBy(() -> sut.verifyCode(USER_ID, CODE, LOGIN_2FA))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("먼저 발송");
  }

  @Test
  @DisplayName("invalidate: SMS 포함 해당 채널 행 전부 삭제 → codeSent=false")
  void invalidate_removesEntry() {
    sut.sendVerificationCode(USER_ID, EMAIL, LOGIN_2FA);
    assertThat(sut.getVerificationStatus(USER_ID).codeSent()).isTrue();

    sut.invalidate(USER_ID);

    assertThat(sut.getVerificationStatus(USER_ID).codeSent()).isFalse();
  }

  @Test
  @DisplayName("getVerificationStatus: null userId → codeSent=false (NPE 없음)")
  void getVerificationStatus_nullUserId_returnsFalse() {
    VerificationStatus status = sut.getVerificationStatus(null);
    assertThat(status.codeSent()).isFalse();
  }

  @Test
  @DisplayName("purpose 분리: UNLOCK 코드로 LOGIN_2FA 검증 불가")
  void verifyCode_cannotCrossPurpose() {
    sut.sendVerificationCode(USER_ID, EMAIL, UNLOCK);

    assertThatThrownBy(() -> sut.verifyCode(USER_ID, CODE, LOGIN_2FA))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("먼저 발송");
  }

  @Test
  @DisplayName("purpose 분리: UNLOCK 발송 → UNLOCK 검증 성공")
  void verifyCode_unlockPurpose_succeeds() {
    sut.sendVerificationCode(USER_ID, EMAIL, UNLOCK);

    boolean result = sut.verifyCode(USER_ID, CODE, UNLOCK);

    assertThat(result).isTrue();
    // UNLOCK 행 삭제됨, LOGIN_2FA 행은 없으므로 codeSent=false
    assertThat(sut.getVerificationStatus(USER_ID).codeSent()).isFalse();
  }

  @Test
  @DisplayName("5분 경과 후 만료 예외")
  void verifyCode_throwsAfterExpiry() {
    sut.sendVerificationCode(USER_ID, EMAIL, LOGIN_2FA);
    mapper.backdateCreatedAt(LOGIN_2FA, CHANNEL, String.valueOf(USER_ID),
        LocalDateTime.now().minusMinutes(6));

    assertThatThrownBy(() -> sut.verifyCode(USER_ID, CODE, LOGIN_2FA))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("만료");
  }

  @Test
  @DisplayName("resendVerificationCode: 기존 행 삭제 후 재발송 (쿨다운 우회)")
  void resendVerificationCode_bypassesCooldown() {
    sut.sendVerificationCode(USER_ID, EMAIL, LOGIN_2FA);

    // resend 는 쿨다운 없이 즉시 가능
    sut.resendVerificationCode(USER_ID, EMAIL);

    verify(emailService, org.mockito.Mockito.times(2)).sendVerificationEmail(anyString(), anyString());
  }
}
