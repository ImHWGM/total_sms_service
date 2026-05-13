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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.domain.email.dto.EmailVerificationStatus;
import kr.wisead.domain.sms.sender.SmsOtpSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * SmsAuthService 단위 테스트 — EmailAuthServiceTest 와 평행 구조.
 *
 * <p>plan v5 §4 Phase B-2.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SmsAuthService (로그인 SMS 2FA OTP)")
class SmsAuthServiceTest {

  private static final Integer USER_ID = 42;
  private static final String PHONE = "01012345678";

  @Mock private SmsOtpSender smsOtpSender;

  @InjectMocks private SmsAuthService sut;

  @BeforeEach
  void cleanStore() {
    // @InjectMocks 가 ConcurrentHashMap 도 새로 만들지만, 안전을 위해 명시 클리어.
    @SuppressWarnings("unchecked")
    Map<Integer, Object> store =
        (Map<Integer, Object>) ReflectionTestUtils.getField(sut, "verificationStore");
    if (store != null) {
      store.clear();
    }
  }

  @Test
  @DisplayName("sendVerificationCode_storesByUserId_andCallsSmsOtpSender")
  void sendVerificationCode_storesByUserId_andCallsSmsOtpSender() {
    sut.sendVerificationCode(USER_ID, PHONE);

    verify(smsOtpSender, times(1)).sendOtp(eq(PHONE), anyString());
    EmailVerificationStatus status = sut.getVerificationStatus(USER_ID);
    assertThat(status.codeSent()).isTrue();
    assertThat(status.remainingAttempts()).isEqualTo(5);
  }

  @Test
  @DisplayName("sendVerificationCode_generates6DigitNumericCode")
  void sendVerificationCode_generates6DigitNumericCode() {
    sut.sendVerificationCode(USER_ID, PHONE);

    ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
    verify(smsOtpSender).sendOtp(eq(PHONE), codeCaptor.capture());

    String code = codeCaptor.getValue();
    assertThat(code).hasSize(6);
    assertThat(code).matches("\\d{6}");
  }

  @Test
  @DisplayName("verifyCode_succeedsWithCorrectCode")
  void verifyCode_succeedsWithCorrectCode() {
    sut.sendVerificationCode(USER_ID, PHONE);
    String code = captureSentCode();

    // 예외 없이 통과해야 함
    sut.verifyCode(USER_ID, code);

    // 검증 성공 후 store 에서 제거됨
    EmailVerificationStatus status = sut.getVerificationStatus(USER_ID);
    assertThat(status.codeSent()).isFalse();
  }

  @Test
  @DisplayName("verifyCode_failsAfterMaxAttempts: 5회 실패 후 코드 폐기")
  void verifyCode_failsAfterMaxAttempts() {
    sut.sendVerificationCode(USER_ID, PHONE);

    // 5번 틀린 코드 입력
    for (int i = 0; i < 5; i++) {
      try {
        sut.verifyCode(USER_ID, "000000");
      } catch (BusinessException ignored) {
        // 의도된 실패
      }
    }

    // 6번째 시도 — 이미 폐기되어 "먼저 발송" 메시지
    assertThatThrownBy(() -> sut.verifyCode(USER_ID, "000000"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("발송");

    EmailVerificationStatus status = sut.getVerificationStatus(USER_ID);
    assertThat(status.codeSent()).isFalse();
  }

  @Test
  @DisplayName("verifyCode_throwsAfterExpiry: 5분 경과 시 만료")
  void verifyCode_throwsAfterExpiry() {
    sut.sendVerificationCode(USER_ID, PHONE);

    // store 의 createdAt 을 6분 전으로 조작
    expireEntryFor(USER_ID, 6);

    assertThatThrownBy(() -> sut.verifyCode(USER_ID, "000000"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("만료");
  }

  @Test
  @DisplayName("resendVerificationCode_respects60SecondLimit")
  void resendVerificationCode_respects60SecondLimit() {
    sut.sendVerificationCode(USER_ID, PHONE);

    // 즉시 재발송 시도 — 60초 제한에 걸려야 함
    assertThatThrownBy(() -> sut.sendVerificationCode(USER_ID, PHONE))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("재발송");
  }

  @Test
  @DisplayName("resendVerificationCode_allowsAfter60Seconds: 60초 경과 후 새 코드 발급")
  void resendVerificationCode_allowsAfter60Seconds() {
    sut.sendVerificationCode(USER_ID, PHONE);

    // createdAt 을 70초 전으로 조작 → 재발송 가능
    setCreatedAtSecondsAgo(USER_ID, 70);

    sut.resendVerificationCode(USER_ID, PHONE);

    verify(smsOtpSender, times(2)).sendOtp(eq(PHONE), anyString());
  }

  @Test
  @DisplayName("invalidate_clearsEntry: OtpStoreCoordinator 용 강제 무효화")
  void invalidate_clearsEntry() {
    sut.sendVerificationCode(USER_ID, PHONE);
    assertThat(sut.getVerificationStatus(USER_ID).codeSent()).isTrue();

    sut.invalidate(USER_ID);

    assertThat(sut.getVerificationStatus(USER_ID).codeSent()).isFalse();
  }

  @Test
  @DisplayName("invalidate_nullUserId_isNoop")
  void invalidate_nullUserId_isNoop() {
    // 예외 없이 통과해야 함
    sut.invalidate(null);
  }

  @Test
  @DisplayName("getVerificationStatus_returnsCurrentState")
  void getVerificationStatus_returnsCurrentState() {
    sut.sendVerificationCode(USER_ID, PHONE);

    EmailVerificationStatus status = sut.getVerificationStatus(USER_ID);

    assertThat(status.codeSent()).isTrue();
    assertThat(status.remainingAttempts()).isEqualTo(5);
    assertThat(status.remainingSeconds()).isBetween(0L, 5L * 60L);
    assertThat(status.remainingResendSeconds()).isBetween(0L, 60L);
  }

  @Test
  @DisplayName("getVerificationStatus_nullUserId_returnsFalse: null 안전")
  void getVerificationStatus_nullUserId_returnsFalse() {
    EmailVerificationStatus status = sut.getVerificationStatus(null);

    assertThat(status.codeSent()).isFalse();
    assertThat(status.remainingSeconds()).isZero();
    assertThat(status.remainingResendSeconds()).isZero();
    assertThat(status.remainingAttempts()).isZero();
  }

  @Test
  @DisplayName("verifyCode_withoutSend_throwsException")
  void verifyCode_withoutSend_throwsException() {
    assertThatThrownBy(() -> sut.verifyCode(USER_ID, "000000"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("먼저 발송");
  }

  @Test
  @DisplayName("sendVerificationCode_nullUserId_throws")
  void sendVerificationCode_nullUserId_throws() {
    assertThatThrownBy(() -> sut.sendVerificationCode(null, PHONE))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("사용자");
  }

  @Test
  @DisplayName("sendVerificationCode_blankPhone_throws")
  void sendVerificationCode_blankPhone_throws() {
    assertThatThrownBy(() -> sut.sendVerificationCode(USER_ID, ""))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("휴대폰");

    verify(smsOtpSender, never()).sendOtp(anyString(), anyString());
  }

  @Test
  @DisplayName("sendVerificationCode_smsOtpSenderFails_doesNotFallbackToEmail")
  void sendVerificationCode_smsOtpSenderFails_doesNotFallbackToEmail() {
    doThrow(new RuntimeException("GMGO 실패")).when(smsOtpSender).sendOtp(anyString(), anyString());

    // 스펙 C7: GMGO 실패 시 자동 EMAIL 폴백 금지 → BusinessException 만 throw
    assertThatThrownBy(() -> sut.sendVerificationCode(USER_ID, PHONE))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("발송");

    // 발송 실패 시 entry 유지 → 사용자가 재발송 명시 선택 가능
    verify(smsOtpSender, times(1)).sendOtp(eq(PHONE), anyString());
  }

  // ==================== Helpers ====================

  private String captureSentCode() {
    ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
    verify(smsOtpSender).sendOtp(eq(PHONE), codeCaptor.capture());
    return codeCaptor.getValue();
  }

  /** 지정한 userId 의 createdAt 을 minutesAgo 분 전으로 조작 (만료 시뮬레이션). */
  private void expireEntryFor(Integer userId, int minutesAgo) {
    setCreatedAt(userId, LocalDateTime.now().minusMinutes(minutesAgo));
  }

  /** 지정한 userId 의 createdAt 을 secondsAgo 초 전으로 조작 (재발송 윈도우 시뮬레이션). */
  private void setCreatedAtSecondsAgo(Integer userId, int secondsAgo) {
    setCreatedAt(userId, LocalDateTime.now().minusSeconds(secondsAgo));
  }

  private void setCreatedAt(Integer userId, LocalDateTime createdAt) {
    @SuppressWarnings("unchecked")
    ConcurrentHashMap<Integer, Object> store =
        (ConcurrentHashMap<Integer, Object>) ReflectionTestUtils.getField(sut, "verificationStore");
    assertThat(store).isNotNull();
    Object info = store.get(userId);
    assertThat(info).isNotNull();
    ReflectionTestUtils.setField(info, "createdAt", createdAt);
  }
}
