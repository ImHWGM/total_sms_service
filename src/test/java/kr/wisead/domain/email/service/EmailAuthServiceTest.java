package kr.wisead.domain.email.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.domain.email.dto.EmailVerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * EmailAuthService 단위 테스트 — 로그인/2FA 도메인 key=userId 검증.
 *
 * <p>plan §4 Phase B-0-8.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EmailAuthService (key=userId)")
class EmailAuthServiceTest {

  @Mock private EmailService emailService;

  @InjectMocks private EmailAuthService sut;

  private static final Integer USER_ID = 42;
  private static final String EMAIL = "user@example.com";
  private static final String CODE = "Z9Y8X7";

  @BeforeEach
  void setupEmailServiceMock() {
    when(emailService.createVerificationCode()).thenReturn(CODE);
    doNothing().when(emailService).sendVerificationEmail(anyString(), anyString());
  }

  @Test
  @DisplayName("getVerificationStatus_byUserIdReturnsStatus: userId로 상태 조회")
  void getVerificationStatus_byUserIdReturnsStatus() {
    sut.sendVerificationCode(USER_ID, EMAIL);

    EmailVerificationStatus status = sut.getVerificationStatus(USER_ID);

    assertThat(status.codeSent()).isTrue();
    assertThat(status.remainingSeconds()).isPositive();
    assertThat(status.remainingAttempts()).isEqualTo(5);
    verify(emailService).sendVerificationEmail(eq(EMAIL), eq(CODE));
  }

  @Test
  @DisplayName("verifyCode: userId key로 올바른 코드 검증 성공")
  void verifyCode_succeedsWithCorrectCode() {
    sut.sendVerificationCode(USER_ID, EMAIL);

    boolean result = sut.verifyCode(USER_ID, CODE);

    assertThat(result).isTrue();
    // 성공 후 저장소 제거 확인
    assertThat(sut.getVerificationStatus(USER_ID).codeSent()).isFalse();
  }

  @Test
  @DisplayName("verifyCode: 발송 없이 검증 시 BusinessException")
  void verifyCode_withoutSend_throwsException() {
    assertThatThrownBy(() -> sut.verifyCode(USER_ID, CODE))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("먼저 발송");
  }

  @Test
  @DisplayName("invalidate: 강제 무효화 후 codeSent=false")
  void invalidate_removesEntry() {
    sut.sendVerificationCode(USER_ID, EMAIL);
    assertThat(sut.getVerificationStatus(USER_ID).codeSent()).isTrue();

    sut.invalidate(USER_ID);

    assertThat(sut.getVerificationStatus(USER_ID).codeSent()).isFalse();
  }

  @Test
  @DisplayName("getVerificationStatus: null userId → codeSent=false (NPE 없음)")
  void getVerificationStatus_nullUserId_returnsFalse() {
    EmailVerificationStatus status = sut.getVerificationStatus(null);
    assertThat(status.codeSent()).isFalse();
  }
}
