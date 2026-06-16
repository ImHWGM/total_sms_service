package kr.wisead.domain.sms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.domain.sms.entity.SmsVerification;
import kr.wisead.domain.sms.sender.SmsOtpSender;
import kr.wisead.mapper.primary.SmsVerificationMapper;
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
 * SmsVerificationService 단위 테스트 — DB 기반(M3) 저장소를 인메모리 fake mapper 로 대체해 서비스 로직을 검증한다.
 *
 * <p>fake mapper 는 {@link SmsVerificationMapper} 의 SQL 의미(원자적 increment/markVerified, 만료 정리
 * 등)를 자바로 동일하게 재현한다. OTP 코드는 {@link SmsOtpSender#sendOtp} 로 전달된 값을 ArgumentCaptor 로 가로채 사용한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SmsVerificationService (DB 기반, key=purpose:phone)")
class SmsVerificationServiceTest {

  @Mock private SmsOtpSender smsOtpSender;

  private InMemorySmsVerificationMapper mapper;
  private SmsVerificationService sut;

  private static final String PHONE_DASHED = "010-1234-5678";
  private static final String PHONE_NORMALIZED = "01012345678";
  private static final String SIGNUP = SmsVerificationService.PURPOSE_SIGNUP;
  private static final String OTHER_PURPOSE = "PASSWORD_RESET"; // 가상의 다른 용도

  @BeforeEach
  void setUp() {
    mapper = new InMemorySmsVerificationMapper();
    sut = new SmsVerificationService(smsOtpSender, mapper);
  }

  /** 발송 시 SmsOtpSender 로 넘어간 OTP 코드를 가로채 반환한다 (마지막 호출). */
  private String captureLastSentCode() {
    ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
    verify(smsOtpSender, org.mockito.Mockito.atLeastOnce())
        .sendOtp(anyString(), codeCaptor.capture());
    return codeCaptor.getValue();
  }

  @Test
  @DisplayName("sendVerificationCode: 6자리 숫자 코드를 정규화된 번호로 발송한다")
  void sendVerificationCode_sends6DigitToNormalizedPhone() {
    doNothing().when(smsOtpSender).sendOtp(anyString(), anyString());

    sut.sendVerificationCode(PHONE_DASHED, SIGNUP);

    verify(smsOtpSender).sendOtp(eq(PHONE_NORMALIZED), anyString());
    assertThat(captureLastSentCode()).matches("\\d{6}");

    assertThat(sut.getVerificationStatus(PHONE_DASHED, SIGNUP).codeSent()).isTrue();
    assertThat(sut.getVerificationStatus(PHONE_NORMALIZED, SIGNUP).codeSent()).isTrue();
  }

  @Test
  @DisplayName("verifyCode: 올바른 코드로 검증 성공 후 코드가 소비된다")
  void verifyCode_succeedsWithCorrectCode() {
    doNothing().when(smsOtpSender).sendOtp(anyString(), anyString());
    sut.sendVerificationCode(PHONE_DASHED, SIGNUP);
    String code = captureLastSentCode();

    boolean result = sut.verifyCode(PHONE_DASHED, code, SIGNUP);

    assertThat(result).isTrue();
    // 검증 성공 후 code 소비 → status codeSent=false
    assertThat(sut.getVerificationStatus(PHONE_NORMALIZED, SIGNUP).codeSent()).isFalse();
  }

  @Test
  @DisplayName("강제함: 인증 성공 후 consumeVerification 통과 (하이픈 유무 무관)")
  void consumeVerification_passesAfterVerify() {
    doNothing().when(smsOtpSender).sendOtp(anyString(), anyString());
    sut.sendVerificationCode(PHONE_NORMALIZED, SIGNUP);
    String code = captureLastSentCode();
    sut.verifyCode(PHONE_NORMALIZED, code, SIGNUP);

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

    sut.sendVerificationCode(PHONE_NORMALIZED, OTHER_PURPOSE);
    String code = captureLastSentCode();
    sut.verifyCode(PHONE_NORMALIZED, code, OTHER_PURPOSE);

    assertThatThrownBy(() -> sut.consumeVerification(PHONE_NORMALIZED, SIGNUP))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("본인인증을 먼저 완료");

    sut.consumeVerification(PHONE_NORMALIZED, OTHER_PURPOSE); // 원래 용도로는 정상
  }

  @Test
  @DisplayName("purpose 분리: 한 용도의 코드로 다른 용도를 검증할 수 없다")
  void verifyCode_cannotCrossPurpose() {
    doNothing().when(smsOtpSender).sendOtp(anyString(), anyString());
    sut.sendVerificationCode(PHONE_NORMALIZED, OTHER_PURPOSE);
    String code = captureLastSentCode();

    assertThatThrownBy(() -> sut.verifyCode(PHONE_NORMALIZED, code, SIGNUP))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("인증 코드를 먼저 발송");
  }

  @Test
  @DisplayName("verifyCode: 최대 시도 초과 시 BusinessException 발생")
  void verifyCode_failsAfterMaxAttempts() {
    doNothing().when(smsOtpSender).sendOtp(anyString(), anyString());
    sut.sendVerificationCode(PHONE_NORMALIZED, SIGNUP);

    for (int i = 0; i < 4; i++) {
      try {
        sut.verifyCode(PHONE_NORMALIZED, "000000", SIGNUP);
      } catch (BusinessException ignored) {
        // 1~4번째는 남은 시도 안내 예외
      }
    }

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

  @Test
  @DisplayName("H1: resend 도 60초 쿨다운을 적용받는다 (우회 불가)")
  void resend_enforcesCooldown() {
    doNothing().when(smsOtpSender).sendOtp(anyString(), anyString());
    sut.sendVerificationCode(PHONE_NORMALIZED, SIGNUP); // 방금 발송 → 쿨다운 중

    assertThatThrownBy(() -> sut.resendVerificationCode(PHONE_NORMALIZED, SIGNUP))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("재발송은");
  }

  @Test
  @DisplayName("M2: checkVerified 는 도장을 소비하지 않는다 (여러 번 통과 + 이후 1회 소비 가능)")
  void checkVerified_doesNotConsume() {
    doNothing().when(smsOtpSender).sendOtp(anyString(), anyString());
    sut.sendVerificationCode(PHONE_NORMALIZED, SIGNUP);
    String code = captureLastSentCode();
    sut.verifyCode(PHONE_NORMALIZED, code, SIGNUP);

    sut.checkVerified(PHONE_NORMALIZED, SIGNUP);
    sut.checkVerified(PHONE_NORMALIZED, SIGNUP);

    sut.consumeVerification(PHONE_NORMALIZED, SIGNUP);
    assertThatThrownBy(() -> sut.consumeVerification(PHONE_NORMALIZED, SIGNUP))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("본인인증을 먼저 완료");
  }

  @Test
  @DisplayName("M2: 미인증 번호는 checkVerified 에서 거부된다")
  void checkVerified_rejectsUnverified() {
    assertThatThrownBy(() -> sut.checkVerified(PHONE_NORMALIZED, SIGNUP))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("본인인증을 먼저 완료");
  }

  // ==================== Fake Mapper ====================

  /** SmsVerificationMapper 의 SQL 의미를 자바로 재현한 인메모리 구현(테스트 전용). */
  private static class InMemorySmsVerificationMapper implements SmsVerificationMapper {

    private final Map<String, SmsVerification> store = new HashMap<>();

    private String key(String purpose, String phone) {
      return purpose + ":" + phone;
    }

    @Override
    public SmsVerification findByKey(String purpose, String phone) {
      return store.get(key(purpose, phone));
    }

    @Override
    public int insert(SmsVerification e) {
      store.put(key(e.getPurpose(), e.getPhone()), e);
      return 1;
    }

    @Override
    public int updateForSend(SmsVerification e) {
      String k = key(e.getPurpose(), e.getPhone());
      if (!store.containsKey(k)) {
        return 0;
      }
      // code/created_at 갱신, attempts=0, verified_at=NULL (seq 는 기존 행 유지)
      SmsVerification cur = store.get(k);
      store.put(
          k,
          new SmsVerification(
              cur.getSeq(), e.getPurpose(), e.getPhone(), e.getCode(), 0, e.getCreatedAt(), null));
      return 1;
    }

    @Override
    public int incrementAttempts(String purpose, String phone) {
      String k = key(purpose, phone);
      SmsVerification e = store.get(k);
      if (e == null) {
        return 0;
      }
      store.put(
          k,
          new SmsVerification(
              e.getSeq(),
              e.getPurpose(),
              e.getPhone(),
              e.getCode(),
              e.getAttempts() + 1,
              e.getCreatedAt(),
              e.getVerifiedAt()));
      return 1;
    }

    @Override
    public int markVerifiedIfCodeMatches(
        String purpose, String phone, String code, LocalDateTime verifiedAt) {
      String k = key(purpose, phone);
      SmsVerification e = store.get(k);
      if (e != null && code.equals(e.getCode()) && e.getVerifiedAt() == null) {
        store.put(
            k,
            new SmsVerification(
                e.getSeq(),
                e.getPurpose(),
                e.getPhone(),
                null,
                e.getAttempts(),
                e.getCreatedAt(),
                verifiedAt));
        return 1;
      }
      return 0;
    }

    @Override
    public int deleteByKey(String purpose, String phone) {
      return store.remove(key(purpose, phone)) != null ? 1 : 0;
    }

    @Override
    public int deleteExpired(LocalDateTime codeCutoff, LocalDateTime verifiedCutoff) {
      int[] count = {0};
      store
          .values()
          .removeIf(
              e -> {
                boolean codeExpired =
                    e.getVerifiedAt() == null && e.getCreatedAt().isBefore(codeCutoff);
                boolean stampExpired =
                    e.getVerifiedAt() != null && e.getVerifiedAt().isBefore(verifiedCutoff);
                if (codeExpired || stampExpired) {
                  count[0]++;
                  return true;
                }
                return false;
              });
      return count[0];
    }
  }
}
