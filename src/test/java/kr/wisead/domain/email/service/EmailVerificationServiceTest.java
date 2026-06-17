package kr.wisead.domain.email.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.domain.verification.InMemoryVerificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * EmailVerificationService 단위 테스트 — DB 기반(M3, 채널 통합) 저장소를 인메모리 fake mapper 로 대체해 검증한다. 코드 생성은
 * {@link EmailService#createVerificationCode()} 목으로 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EmailVerificationService (DB 기반, channel=EMAIL)")
class EmailVerificationServiceTest {

  @Mock private EmailService emailService;

  private InMemoryVerificationMapper mapper;
  private EmailVerificationService sut;

  private static final String EMAIL = "Test@Example.com";
  private static final String EMAIL_LOWER = "test@example.com";
  private static final String CODE = "A1B2C3D4";

  @BeforeEach
  void setUp() {
    mapper = new InMemoryVerificationMapper();
    sut = new EmailVerificationService(emailService, mapper);
    when(emailService.createVerificationCode()).thenReturn(CODE);
    doNothing().when(emailService).sendVerificationEmail(anyString(), anyString());
  }

  @Test
  @DisplayName("sendVerificationCode: 소문자 정규화 키로 저장, 대소문자 무관 조회")
  void send_storesByLowercasedEmail() {
    sut.sendVerificationCode(EMAIL);

    assertThat(sut.getVerificationStatus(EMAIL).codeSent()).isTrue();
    assertThat(sut.getVerificationStatus(EMAIL_LOWER).codeSent()).isTrue();
  }

  @Test
  @DisplayName("verifyCode: 올바른 코드로 성공 후 행 제거(codeSent=false)")
  void verify_succeedsAndRemoves() {
    sut.sendVerificationCode(EMAIL);

    assertThat(sut.verifyCode(EMAIL, CODE)).isTrue();
    assertThat(sut.getVerificationStatus(EMAIL).codeSent()).isFalse();
  }

  @Test
  @DisplayName("verifyCode: 미발송 상태면 예외")
  void verify_withoutSend_throws() {
    assertThatThrownBy(() -> sut.verifyCode(EMAIL, CODE))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("인증 코드를 먼저 발송");
  }

  @Test
  @DisplayName("verifyCode: 최대 시도 초과 시 예외")
  void verify_maxAttempts() {
    sut.sendVerificationCode(EMAIL);
    for (int i = 0; i < 4; i++) {
      try {
        sut.verifyCode(EMAIL, "WRONGXXX");
      } catch (BusinessException ignored) {
        // 1~4회: 남은 시도 안내
      }
    }
    assertThatThrownBy(() -> sut.verifyCode(EMAIL, "WRONGXXX"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("인증 시도 횟수를 초과");
  }

  @Test
  @DisplayName("H1: resend 도 60초 쿨다운을 적용받는다")
  void resend_enforcesCooldown() {
    sut.sendVerificationCode(EMAIL); // 방금 발송

    assertThatThrownBy(() -> sut.resendVerificationCode(EMAIL))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("재발송은");
  }

  @Test
  @DisplayName("sendVerificationCode: 잘못된 이메일 형식은 거부")
  void send_rejectsInvalidEmail() {
    assertThatThrownBy(() -> sut.sendVerificationCode("not-an-email"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("이메일 형식");
  }

  @Test
  @DisplayName("동시 최초발송: UNIQUE 충돌 → 500 아닌 안내 예외로 변환")
  void concurrentFirstSend_translatesUniqueViolation() {
    kr.wisead.mapper.primary.VerificationMapper mockMapper =
        org.mockito.Mockito.mock(kr.wisead.mapper.primary.VerificationMapper.class);
    org.mockito.Mockito.when(mockMapper.findByKey(anyString(), anyString(), anyString()))
        .thenReturn(null);
    org.mockito.Mockito.when(mockMapper.insert(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new org.springframework.dao.DuplicateKeyException("uk violation"));
    EmailVerificationService racy = new EmailVerificationService(emailService, mockMapper);

    assertThatThrownBy(() -> racy.sendVerificationCode(EMAIL))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("잠시 후 다시 시도");
  }
}
