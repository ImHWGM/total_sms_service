package kr.wisead.domain.sms.sender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import kr.wisead.domain.message.entity.MsgQueue;
import kr.wisead.mapper.sms.MsgQueueMapper;
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
 * SmsOtpSender 단위 테스트 — MultiMessageService 우회 및 결제 경로 분리 검증.
 *
 * <p>plan v5 §4 Phase B-1.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SmsOtpSender (결제 경로 분리)")
class SmsOtpSenderTest {

  private static final String OTP_SENDER = "15880000";
  private static final String PHONE = "01012345678";
  private static final String CODE = "123456";

  @Mock private MsgQueueMapper msgQueueMapper;

  @InjectMocks private SmsOtpSender sut;

  @BeforeEach
  void setUpOtpSenderProperty() {
    ReflectionTestUtils.setField(sut, "otpSender", OTP_SENDER);
    when(msgQueueMapper.insertSms(any(MsgQueue.class))).thenReturn(1);
  }

  @Test
  @DisplayName("sendOtp_callsInsertSms_withSystemRegId: SYSTEM regId로 insertSms 호출")
  void sendOtp_callsInsertSms_withSystemRegId() {
    sut.sendOtp(PHONE, CODE);

    ArgumentCaptor<MsgQueue> captor = ArgumentCaptor.forClass(MsgQueue.class);
    verify(msgQueueMapper).insertSms(captor.capture());

    MsgQueue captured = captor.getValue();
    assertThat(captured.getExtCol3()).isEqualTo("SYSTEM");
    assertThat(captured.getDstaddr()).isEqualTo(PHONE);
    assertThat(captured.getMsgType()).isEqualTo("S");
  }

  @Test
  @DisplayName("sendOtp_doesNotCallMultiMessageService: 발송 큐만 적재하고 다른 SMS 진입점 미사용")
  void sendOtp_doesNotCallMultiMessageService() {
    sut.sendOtp(PHONE, CODE);

    // LMS/MMS 경로 미진입 확인 (격리 검증)
    verify(msgQueueMapper, never()).insertLms(any(MsgQueue.class));
    verify(msgQueueMapper, never()).insertMms(any(MsgQueue.class));
    verify(msgQueueMapper, never()).insertForSurvey(any(MsgQueue.class));
  }

  @Test
  @DisplayName("sendOtp_usesOtpSenderProperty: 발신번호로 sms.otp.sender 사용")
  void sendOtp_usesOtpSenderProperty() {
    sut.sendOtp(PHONE, CODE);

    ArgumentCaptor<MsgQueue> captor = ArgumentCaptor.forClass(MsgQueue.class);
    verify(msgQueueMapper).insertSms(captor.capture());

    assertThat(captor.getValue().getCallback()).isEqualTo(OTP_SENDER);
  }

  @Test
  @DisplayName("sendOtp_setsTxGroupIdToNull: 결제 추적용 txGroupId 미설정 (환불 대상 아님)")
  void sendOtp_setsTxGroupIdToNull() {
    sut.sendOtp(PHONE, CODE);

    ArgumentCaptor<MsgQueue> captor = ArgumentCaptor.forClass(MsgQueue.class);
    verify(msgQueueMapper).insertSms(captor.capture());

    assertThat(captor.getValue().getTxGroupId()).isNull();
    assertThat(captor.getValue().getExtCol2()).isNull();
  }

  @Test
  @DisplayName("sendOtp_userKeyHasOtpPrefix: EXT_COL1 userKey에 OTP- 접두사")
  void sendOtp_userKeyHasOtpPrefix() {
    sut.sendOtp(PHONE, CODE);

    ArgumentCaptor<MsgQueue> captor = ArgumentCaptor.forClass(MsgQueue.class);
    verify(msgQueueMapper).insertSms(captor.capture());

    assertThat(captor.getValue().getExtCol1()).startsWith("OTP-");
  }

  @Test
  @DisplayName("sendOtp_textIncludesCode: 발송 본문에 인증코드 포함")
  void sendOtp_textIncludesCode() {
    sut.sendOtp(PHONE, CODE);

    ArgumentCaptor<MsgQueue> captor = ArgumentCaptor.forClass(MsgQueue.class);
    verify(msgQueueMapper).insertSms(captor.capture());

    assertThat(captor.getValue().getText()).contains(CODE);
  }
}
