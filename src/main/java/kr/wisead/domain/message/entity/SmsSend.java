package kr.wisead.domain.message.entity;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 문자 발송 이력 Entity (PRIMARY DB - sms_send)
 *
 * <p>행사/설문 문자 발송 시 이력을 기록하여, 참여자별 발송 여부를 판단하는 데 사용합니다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SmsSend {

  private Integer seq;
  private Integer eventSeq;
  private Integer userSeq;
  private String subject;
  private String content;
  private String sendType;
  private String receivedNum;
  private String callback;
  private LocalDateTime reqDate;
  private String sendYn;
  private LocalDateTime regDate;
  private String regId;

  @Builder
  public SmsSend(
      Integer seq,
      Integer eventSeq,
      Integer userSeq,
      String subject,
      String content,
      String sendType,
      String receivedNum,
      String callback,
      LocalDateTime reqDate,
      String sendYn,
      LocalDateTime regDate,
      String regId) {
    this.seq = seq;
    this.eventSeq = eventSeq;
    this.userSeq = userSeq;
    this.subject = subject;
    this.content = content;
    this.sendType = sendType;
    this.receivedNum = receivedNum;
    this.callback = callback;
    this.reqDate = reqDate;
    this.sendYn = sendYn;
    this.regDate = regDate;
    this.regId = regId;
  }

  /** 발송 이력 생성 팩토리 메서드 */
  public static SmsSend create(
      Integer eventSeq,
      Integer userSeq,
      String subject,
      String content,
      String sendType,
      String receivedNum,
      String callback,
      String regId) {
    LocalDateTime now = LocalDateTime.now();
    return SmsSend.builder()
        .eventSeq(eventSeq)
        .userSeq(userSeq)
        .subject(subject)
        .content(content)
        .sendType(sendType)
        .receivedNum(receivedNum)
        .callback(callback)
        .reqDate(now)
        .sendYn("Y")
        .regDate(now)
        .regId(regId)
        .build();
  }
}
