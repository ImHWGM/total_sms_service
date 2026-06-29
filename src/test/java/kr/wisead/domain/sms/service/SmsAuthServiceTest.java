package kr.wisead.domain.sms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import kr.wisead.common.dto.VerificationStatus;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.domain.sms.sender.SmsOtpSender;
import kr.wisead.domain.verification.InMemoryVerificationMapper;
import kr.wisead.domain.verification.service.VerificationAttemptPersister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * SmsAuthService 단위 테스트 — DB 기반(M3) fake mapper 사용.
 *
 * <p>plan v5 §4 Phase B-2.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SmsAuthService (로그인 SMS 2FA OTP, DB 기반)")
class SmsAuthServiceTest {

  private static final Integer USER_ID = 42;
  private static final String PHONE = "01012345678";
  private static final String PURPOSE = "SMS_2FA";
  private static final String CHANNEL = "SMS";

  @Mock private SmsOtpSender smsOtpSender;

  private InMemoryVerificationMapper mapper;
  private SmsAuthService sut;

  @BeforeEach
  void setUp() {
    mapper = new InMemoryVerificationMapper();
    sut = new SmsAuthService(smsOtpSender, mapper, new VerificationAttemptPersister(mapper));
  }

  @Test
  @DisplayName("sendVerificationCode: userId 기반으로 DB에 저장하고 OTP 발송")
  void sendVerificationCode_storesByUserId_andCallsSmsOtpSender() {
    sut.sendVerificationCode(USER_ID, PHONE);

    verify(smsOtpSender, times(1)).sendOtp(eq(PHONE), anyString());
    VerificationStatus status = sut.getVerificationStatus(USER_ID);
    assertThat(status.codeSent()).isTrue();
    assertThat(status.remainingAttempts()).isEqualTo(5);
  }

  @Test
  @DisplayName("sendVerificationCode: 6자리 숫자 OTP 생성")
  void sendVerificationCode_generates6DigitNumericCode() {
    sut.sendVerificationCode(USER_ID, PHONE);

    ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
    verify(smsOtpSender).sendOtp(eq(PHONE), codeCaptor.capture());

    assertThat(codeCaptor.getValue()).hasSize(6).matches("\\d{6}");
  }

  @Test
  @DisplayName("verifyCode: 올바른 코드 검증 성공 → DB 행 삭제")
  void verifyCode_succeedsWithCorrectCode() {
    sut.sendVerificationCode(USER_ID, PHONE);
    String code = captureCode();

    sut.verifyCode(USER_ID, code);

    assertThat(sut.getVerificationStatus(USER_ID).codeSent()).isFalse();
  }

  @Test
  @DisplayName("verifyCode: 5회 실패 후 행 삭제 → 이후 '먼저 발송' 예외")
  void verifyCode_failsAfterMaxAttempts() {
    sut.sendVerificationCode(USER_ID, PHONE);

    for (int i = 0; i < 5; i++) {
      try {
        sut.verifyCode(USER_ID, "000000");
      } catch (BusinessException ignored) {
      }
    }

    assertThatThrownBy(() -> sut.verifyCode(USER_ID, "000000"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("발송");
    assertThat(sut.getVerificationStatus(USER_ID).codeSent()).isFalse();
  }

  @Test
  @DisplayName("verifyCode: 5분 경과 후 만료 예외")
  void verifyCode_throwsAfterExpiry() {
    sut.sendVerificationCode(USER_ID, PHONE);
    mapper.backdateCreatedAt(PURPOSE, CHANNEL, String.valueOf(USER_ID),
        LocalDateTime.now().minusMinutes(6));

    assertThatThrownBy(() -> sut.verifyCode(USER_ID, "000000"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("만료");
  }

  @Test
  @DisplayName("sendVerificationCode 연속: 60초 쿨다운 적용")
  void resendVerificationCode_respects60SecondLimit() {
    sut.sendVerificationCode(USER_ID, PHONE);

    assertThatThrownBy(() -> sut.sendVerificationCode(USER_ID, PHONE))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("재발송");
  }

  @Test
  @DisplayName("resendVerificationCode: 70초 경과 후 새 코드 발급 (쿨다운 우회)")
  void resendVerificationCode_allowsAfter60Seconds() {
    sut.sendVerificationCode(USER_ID, PHONE);
    mapper.backdateCreatedAt(PURPOSE, CHANNEL, String.valueOf(USER_ID),
        LocalDateTime.now().minusSeconds(70));

    sut.resendVerificationCode(USER_ID, PHONE);

    verify(smsOtpSender, times(2)).sendOtp(eq(PHONE), anyString());
  }

  @Test
  @DisplayName("invalidate: 채널 전환 시 SMS 행 전체 삭제")
  void invalidate_clearsEntry() {
    sut.sendVerificationCode(USER_ID, PHONE);
    assertThat(sut.getVerificationStatus(USER_ID).codeSent()).isTrue();

    sut.invalidate(USER_ID);

    assertThat(sut.getVerificationStatus(USER_ID).codeSent()).isFalse();
  }

  @Test
  @DisplayName("invalidate: null userId 는 noop")
  void invalidate_nullUserId_isNoop() {
    sut.invalidate(null);
  }

  @Test
  @DisplayName("getVerificationStatus: 정상 상태 반환")
  void getVerificationStatus_returnsCurrentState() {
    sut.sendVerificationCode(USER_ID, PHONE);

    VerificationStatus status = sut.getVerificationStatus(USER_ID);

    assertThat(status.codeSent()).isTrue();
    assertThat(status.remainingAttempts()).isEqualTo(5);
    assertThat(status.remainingSeconds()).isBetween(0L, 5L * 60L);
    assertThat(status.remainingResendSeconds()).isBetween(0L, 60L);
  }

  @Test
  @DisplayName("getVerificationStatus: null userId → codeSent=false (NPE 없음)")
  void getVerificationStatus_nullUserId_returnsFalse() {
    VerificationStatus status = sut.getVerificationStatus(null);

    assertThat(status.codeSent()).isFalse();
    assertThat(status.remainingSeconds()).isZero();
    assertThat(status.remainingResendSeconds()).isZero();
    assertThat(status.remainingAttempts()).isZero();
  }

  @Test
  @DisplayName("verifyCode: 발송 없이 검증 시 예외")
  void verifyCode_withoutSend_throwsException() {
    assertThatThrownBy(() -> sut.verifyCode(USER_ID, "000000"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("먼저 발송");
  }

  @Test
  @DisplayName("sendVerificationCode: null userId → 예외")
  void sendVerificationCode_nullUserId_throws() {
    assertThatThrownBy(() -> sut.sendVerificationCode(null, PHONE))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("사용자");
  }

  @Test
  @DisplayName("sendVerificationCode: 빈 폰번호 → 예외")
  void sendVerificationCode_blankPhone_throws() {
    assertThatThrownBy(() -> sut.sendVerificationCode(USER_ID, ""))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("휴대폰");

    verify(smsOtpSender, never()).sendOtp(anyString(), anyString());
  }

  @Test
  @DisplayName("C7: 발송 실패 시 행 삭제 — 즉시 재시도 가능")
  void sendVerificationCode_smsOtpSenderFails_deletesEntryForRetry() {
    doThrow(new RuntimeException("GMGO 실패")).when(smsOtpSender).sendOtp(anyString(), anyString());

    assertThatThrownBy(() -> sut.sendVerificationCode(USER_ID, PHONE))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("발송");

    // 발송 실패 시 행 삭제 → 즉시 재시도 가능 (쿨다운 없음)
    assertThat(sut.getVerificationStatus(USER_ID).codeSent()).isFalse();
  }

  @Test
  @DisplayName("verifyCodeAndGetPhone: 검증 성공 시 발송 당시 전화번호 반환")
  void verifyCodeAndGetPhone_returnsStoredPhone() {
    sut.sendVerificationCode(USER_ID, PHONE);
    String code = captureCode();

    String phone = sut.verifyCodeAndGetPhone(USER_ID, code);

    assertThat(phone).isEqualTo(PHONE);
    assertThat(sut.getVerificationStatus(USER_ID).codeSent()).isFalse();
  }

  @Test
  @DisplayName("verifyCodeAndGetPhone: 하이픈 포함 번호는 정규화되어 저장/반환")
  void verifyCodeAndGetPhone_normalizesPhone() {
    sut.sendVerificationCode(USER_ID, "010-1234-5678");
    String code = captureCode();

    String phone = sut.verifyCodeAndGetPhone(USER_ID, code);

    assertThat(phone).isEqualTo("01012345678");
  }

  // ==================== Helpers ====================

  private String captureCode() {
    ArgumentCaptor<String> cap = ArgumentCaptor.forClass(String.class);
    verify(smsOtpSender, org.mockito.Mockito.atLeastOnce()).sendOtp(anyString(), cap.capture());
    return cap.getValue();
  }
}
