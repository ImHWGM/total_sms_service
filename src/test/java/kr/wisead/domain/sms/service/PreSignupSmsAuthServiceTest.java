package kr.wisead.domain.sms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import kr.wisead.common.dto.VerificationStatus;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.domain.sms.sender.SmsOtpSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * PreSignupSmsAuthService 단위 테스트 — 회원가입 도메인(key=purpose:phone) 및 강제 검사 / purpose 분리 검증.
 *
 * <p>OTP 코드는 서비스가 내부에서 생성하므로, {@link SmsOtpSender#sendOtp} 로 전달된 코드를 ArgumentCaptor 로 가로채어 검증에 사용한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PreSignupSmsAuthService (key=purpose:phone)")
class PreSignupSmsAuthServiceTest {

  @Mock private SmsOtpSender smsOtpSender;

  @InjectMocks private PreSignupSmsAuthService sut;

  private static final String PHONE_DASHED = "010-1234-5678";
  private static final String PHONE_NORMALIZED = "01012345678";
  private static final String SIGNUP = PreSignupSmsAuthService.PURPOSE_SIGNUP;
  private static final String OTHER_PURPOSE = "PASSWORD_RESET"; // 가상의 다른 용도

  /** 발송 시 SmsOtpSender 로 넘어간 OTP 코드를 가로채 반환한다 (마지막 호출). */
  private String captureLastSentCode() {
    ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
    verify(smsOtpSender, org.mockito.Mockito.atLeastOnce()).sendOtp(anyString(), codeCaptor.capture());
    return codeCaptor.getValue();
  }

  @Test
  @DisplayName("sendVerificationCode: 6자리 숫자 코드를 정규화된 번호로 발송한다")
  void sendVerificationCode_sends6DigitToNormalizedPhone() {
    doNothing().when(smsOtpSender).sendOtp(anyString(), anyString());

    sut.sendVerificationCode(PHONE_DASHED, SIGNUP);

    // 발신은 하이픈 제거된 번호로 나간다
    verify(smsOtpSender).sendOtp(eq(PHONE_NORMALIZED), anyString());
    // 생성 코드는 6자리 숫자
    assertThat(captureLastSentCode()).matches("\\d{6}");

    // 하이픈 유무에 관계없이 같은 키로 조회된다
    assertThat(sut.getVerificationStatus(PHONE_DASHED, SIGNUP).codeSent()).isTrue();
    assertThat(sut.getVerificationStatus(PHONE_NORMALIZED, SIGNUP).codeSent()).isTrue();
  }

  @Test
  @DisplayName("verifyCode: 올바른 코드로 검증 성공 후 저장소에서 제거된다")
  void verifyCode_succeedsWithCorrectCode() {
    doNothing().when(smsOtpSender).sendOtp(anyString(), anyString());
    sut.sendVerificationCode(PHONE_DASHED, SIGNUP);
    String code = captureLastSentCode();

    boolean result = sut.verifyCode(PHONE_DASHED, code, SIGNUP);

    assertThat(result).isTrue();
    // 검증 성공 후 코드 저장소는 비워진다
    assertThat(sut.getVerificationStatus(PHONE_NORMALIZED, SIGNUP).codeSent()).isFalse();
  }

  @Test
  @DisplayName("강제함: 인증 성공 후 consumeVerification 통과 (하이픈 유무 무관)")
  void consumeVerification_passesAfterVerify() {
    doNothing().when(smsOtpSender).sendOtp(anyString(), anyString());
    sut.sendVerificationCode(PHONE_NORMALIZED, SIGNUP);
    String code = captureLastSentCode();
    sut.verifyCode(PHONE_NORMALIZED, code, SIGNUP);

    // 회원가입에서 하이픈 포함 번호로 들어와도 통과해야 한다
    sut.consumeVerification(PHONE_DASHED, SIGNUP); // 예외 없이 통과
  }

  @Test
  @DisplayName("강제함: 인증하지 않은 번호는 consumeVerification 에서 거부된다")
  void consumeVerification_rejectsUnverifiedPhone() {
    assertThatThrownBy(() -> sut.consumeVerification(PHONE_DASHED, SIGNUP))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("본인인증을 먼저 완료");
  }

  @Test
  @DisplayName("강제함: consumeVerification 은 1회용 — 두 번째 호출은 거부된다")
  void consumeVerification_isOneTimeUse() {
    doNothing().when(smsOtpSender).sendOtp(anyString(), anyString());
    sut.sendVerificationCode(PHONE_NORMALIZED, SIGNUP);
    String code = captureLastSentCode();
    sut.verifyCode(PHONE_NORMALIZED, code, SIGNUP);

    sut.consumeVerification(PHONE_NORMALIZED, SIGNUP); // 1회차 통과

    assertThatThrownBy(() -> sut.consumeVerification(PHONE_NORMALIZED, SIGNUP))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("본인인증을 먼저 완료");
  }

  @Test
  @DisplayName("purpose 분리: 다른 용도로 인증한 도장은 회원가입(SIGNUP)이 가져다 쓸 수 없다")
  void consumeVerification_cannotStealOtherPurposeStamp() {
    doNothing().when(smsOtpSender).sendOtp(anyString(), anyString());

    // 다른 용도(PASSWORD_RESET)로 발송 + 검증 성공 → OTHER_PURPOSE 칸에만 도장
    sut.sendVerificationCode(PHONE_NORMALIZED, OTHER_PURPOSE);
    String code = captureLastSentCode();
    sut.verifyCode(PHONE_NORMALIZED, code, OTHER_PURPOSE);

    // 같은 번호라도 SIGNUP 용도로는 인증된 적이 없으므로 회원가입은 거부되어야 한다
    assertThatThrownBy(() -> sut.consumeVerification(PHONE_NORMALIZED, SIGNUP))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("본인인증을 먼저 완료");

    // 반대로 원래 용도(OTHER_PURPOSE)로는 정상 소비 가능
    sut.consumeVerification(PHONE_NORMALIZED, OTHER_PURPOSE);
  }

  @Test
  @DisplayName("purpose 분리: 한 용도의 코드로 다른 용도를 검증할 수 없다")
  void verifyCode_cannotCrossPurpose() {
    doNothing().when(smsOtpSender).sendOtp(anyString(), anyString());
    sut.sendVerificationCode(PHONE_NORMALIZED, OTHER_PURPOSE);
    String code = captureLastSentCode();

    // OTHER_PURPOSE 로 받은 코드를 SIGNUP 용도로 검증 시도 → 발송된 코드가 없다고 거부
    assertThatThrownBy(() -> sut.verifyCode(PHONE_NORMALIZED, code, SIGNUP))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("인증 코드를 먼저 발송");
  }

  @Test
  @DisplayName("verifyCode: 최대 시도 초과 시 BusinessException 발생")
  void verifyCode_failsAfterMaxAttempts() {
    doNothing().when(smsOtpSender).sendOtp(anyString(), anyString());
    sut.sendVerificationCode(PHONE_NORMALIZED, SIGNUP);

    // 틀린 코드 4번 시도 (남은 시도 메시지)
    for (int i = 0; i < 4; i++) {
      try {
        sut.verifyCode(PHONE_NORMALIZED, "000000", SIGNUP);
      } catch (BusinessException ignored) {
        // 1~4번째는 남은 시도 안내 예외
      }
    }

    // 5번째 시도에서 시도 소진 (SmsAuthService 관례: 초과 안내 + 코드 폐기)
    assertThatThrownBy(() -> sut.verifyCode(PHONE_NORMALIZED, "000000", SIGNUP))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("인증 시도 횟수를 초과");
  }

  @Test
  @DisplayName("sendVerificationCode: 잘못된 휴대폰 형식은 거부된다")
  void sendVerificationCode_rejectsInvalidPhone() {
    assertThatThrownBy(() -> sut.sendVerificationCode("02-123-4567", SIGNUP))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("휴대폰 번호 형식");
  }
}
