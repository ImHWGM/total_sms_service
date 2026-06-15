package kr.wisead.domain.email.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import kr.wisead.common.dto.VerificationStatus;
import kr.wisead.common.exception.BusinessException;
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
 * PreSignupEmailAuthService 단위 테스트 — 회원가입 도메인 격리 검증.
 *
 * <p>plan §4 Phase B-0-8.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PreSignupEmailAuthService (key=email)")
class PreSignupEmailAuthServiceTest {

  @Mock private EmailService emailService;

  @InjectMocks private PreSignupEmailAuthService sut;

  private static final String EMAIL = "Test@Example.com";
  private static final String EMAIL_LOWER = "test@example.com";
  private static final String CODE = "A1B2C3";

  @BeforeEach
  void setupEmailServiceMock() {
    when(emailService.createVerificationCode()).thenReturn(CODE);
    doNothing().when(emailService).sendVerificationEmail(anyString(), anyString());
  }

  @Test
  @DisplayName("sendVerificationCode: 소문자 정규화된 키로 저장된다")
  void sendVerificationCode_storesByLowercasedEmail() {
    // when
    sut.sendVerificationCode(EMAIL);

    // then: 소문자 정규화 키로 조회하면 codeSent=true
    VerificationStatus status = sut.getVerificationStatus(EMAIL_LOWER);
    assertThat(status.codeSent()).isTrue();
    // 원본 대소문자 키로도 동일하게 조회 가능
    VerificationStatus statusUpper = sut.getVerificationStatus(EMAIL);
    assertThat(statusUpper.codeSent()).isTrue();

    verify(emailService).sendVerificationEmail(eq(EMAIL), eq(CODE));
  }

  @Test
  @DisplayName("verifyCode: 올바른 코드로 검증 성공 후 저장소에서 제거된다")
  void verifyCode_succeedsWithCorrectCode() {
    sut.sendVerificationCode(EMAIL);

    boolean result = sut.verifyCode(EMAIL, CODE);

    assertThat(result).isTrue();
    // 검증 성공 후 상태 조회 → codeSent=false (저장소 제거)
    VerificationStatus status = sut.getVerificationStatus(EMAIL_LOWER);
    assertThat(status.codeSent()).isFalse();
  }

  @Test
  @DisplayName("verifyCode: 최대 시도 초과 시 BusinessException 발생")
  void verifyCode_failsAfterMaxAttempts() {
    sut.sendVerificationCode(EMAIL);

    // 틀린 코드 5번 시도
    for (int i = 0; i < 4; i++) {
      try {
        sut.verifyCode(EMAIL, "WRONG1");
      } catch (BusinessException ignored) {
        // 1~4번째는 남은 시도 메시지 예외
      }
    }

    // 5번째 시도에서 마지막 시도 소진 (남은 시도 0회) 예외
    assertThatThrownBy(() -> sut.verifyCode(EMAIL, "WRONG1"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("남은 시도: 0회");
  }

  @Test
  @DisplayName("getVerificationStatus: 발송 후 codeSent=true, 남은 시간 양수")
  void getVerificationStatus_returnsCurrentState() {
    sut.sendVerificationCode(EMAIL);

    VerificationStatus status = sut.getVerificationStatus(EMAIL);

    assertThat(status.codeSent()).isTrue();
    assertThat(status.remainingSeconds()).isPositive();
    assertThat(status.remainingAttempts()).isEqualTo(5);
  }

  @Test
  @DisplayName("getVerificationStatus: 발송 전 조회 시 codeSent=false")
  void getVerificationStatus_beforeSend_returnsFalse() {
    VerificationStatus status = sut.getVerificationStatus(EMAIL);
    assertThat(status.codeSent()).isFalse();
  }
}
