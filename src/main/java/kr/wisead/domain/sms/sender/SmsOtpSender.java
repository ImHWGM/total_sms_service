package kr.wisead.domain.sms.sender;

import kr.wisead.common.util.CommonUtils;
import kr.wisead.domain.message.entity.MsgQueue;
import kr.wisead.mapper.sms.MsgQueueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * SMS OTP 발송 전용 서비스 (Phase B-1).
 *
 * <p>{@link kr.wisead.domain.message.service.MultiMessageService#sendDirectMessage} 를 우회하여 발송 큐에만
 * 직접 적재한다. 결제(BalanceService), 야간 전송제한, 잔액 검증을 모두 건너뛰며 로그인 인증용 SMS 전송 경로를 사용자 지갑과 분리한다.
 *
 * <p>참고: plan v5 §4 Phase B-1. Architect v1 C1 (결제 경로 침범) 회피.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsOtpSender {

  /** OTP 발송 큐 적재 시 EXT_COL1(userKey) 접두사. 일반 발송 배치 ID와 구분 위해 사용. */
  private static final String OTP_USER_KEY_PREFIX = "OTP-";

  /** OTP 발송 큐 적재 시 EXT_COL3(regId). 시스템 발송임을 표시. */
  private static final String OTP_REG_ID = "SYSTEM";

  private final MsgQueueMapper msgQueueMapper;

  /** OTP 전용 발신번호. 일반 SMS 발신번호({@code sms.callback})와 분리한다. */
  @Value("${sms.otp.sender}")
  private String otpSender;

  /**
   * SMS OTP 발송. 결제/야간/잔액 검증을 거치지 않고 발송 큐에 직접 적재한다.
   *
   * <p>로그인 트랜잭션과 분리하기 위해 {@code REQUIRES_NEW} 로 동작하며 SMS 전용 {@code smsTransactionManager} 를 사용한다.
   *
   * @param phoneNumber 수신번호 (예: "01012345678" 또는 "010-1234-5678")
   * @param code OTP 인증코드 (로그에 출력하지 않는다)
   */
  @Transactional(value = "smsTransactionManager", propagation = Propagation.REQUIRES_NEW)
  public void sendOtp(String phoneNumber, String code) {
    String text = String.format("[WiseAd] 인증번호 [%s]를 입력해 주세요.", code);
    String userKey = OTP_USER_KEY_PREFIX + MsgQueue.generateUserKey();

    MsgQueue msg =
        MsgQueue.createSms(
            phoneNumber, otpSender, "-", text, userKey, /* txGroupId= */ null, OTP_REG_ID);

    msgQueueMapper.insertSms(msg);

    log.info("[SmsOtp] 발송 큐 적재 완료 - phone: {}", CommonUtils.maskingPhone(phoneNumber));
  }
}
