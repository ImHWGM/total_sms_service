package kr.wisead.domain.message.dto;

import java.time.LocalDateTime;
import kr.wisead.common.util.UrlUtils;
import kr.wisead.domain.message.entity.MsgQueue;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 발송 대기 목록 응답 DTO */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MsgQueueResponse {

  private Integer mseq;
  private String msgType;
  private String msgTypeName;
  private String dstaddr; // 수신번호
  private String callback; // 발신번호
  private String stat;
  private String subject;
  private String text;

  // 첨부파일
  private Integer filecnt;
  private String fileloc1;
  private String fileloc2;
  private String fileloc3;

  // 시간
  private LocalDateTime insertTime;
  private LocalDateTime requestTime;

  // 확장 정보
  private Integer eventSeq; // 설문 시퀀스 (EXT_COL0)
  private String userKey; // 배치ID 또는 userSeq (EXT_COL1)
  private String sendType; // 발송 타입 (수신번호 직접입력인지 엑셀로 대량입력인지였는데 이제 안중요함)
  private String regId; // 등록자 ID (EXT_COL3)

  @Builder
  public MsgQueueResponse(
      Integer mseq,
      String msgType,
      String msgTypeName,
      String dstaddr,
      String callback,
      String stat,
      String subject,
      String text,
      Integer filecnt,
      String fileloc1,
      String fileloc2,
      String fileloc3,
      LocalDateTime insertTime,
      LocalDateTime requestTime,
      Integer eventSeq,
      String userKey,
      String sendType,
      String regId) {
    this.mseq = mseq;
    this.msgType = msgType;
    this.msgTypeName = msgTypeName;
    this.dstaddr = dstaddr;
    this.callback = callback;
    this.stat = stat;
    this.subject = subject;
    this.text = text;
    this.filecnt = filecnt;
    this.fileloc1 = fileloc1;
    this.fileloc2 = fileloc2;
    this.fileloc3 = fileloc3;
    this.insertTime = insertTime;
    this.requestTime = requestTime;
    this.eventSeq = eventSeq;
    this.userKey = userKey;
    this.sendType = sendType;
    this.regId = regId;
  }

  /** Entity -> Response 변환 */
  public static MsgQueueResponse from(MsgQueue entity) {
    return MsgQueueResponse.builder()
        .mseq(entity.getMseq())
        .msgType(entity.getMsgType())
        .msgTypeName(getMsgTypeName(entity.getMsgType()))
        .dstaddr(entity.getDstaddr())
        .callback(entity.getCallback())
        .stat(entity.getStat())
        .subject(entity.getSubject())
        .text(entity.getText())
        .filecnt(entity.getFilecnt())
        .fileloc1(entity.getFileloc1())
        .fileloc2(entity.getFileloc2())
        .fileloc3(entity.getFileloc3())
        .insertTime(entity.getInsertTime())
        .requestTime(entity.getRequestTime())
        .eventSeq(entity.getExtCol0())
        .userKey(entity.getExtCol1())
        .sendType(null)
        .regId(entity.getExtCol3())
        .build();
  }

  /** 파일 경로를 절대 URL로 변환 */
  public void withFullImageUrls(String apiBaseUrl) {
    this.fileloc1 = UrlUtils.toAbsoluteUrl(this.fileloc1, apiBaseUrl);
    this.fileloc2 = UrlUtils.toAbsoluteUrl(this.fileloc2, apiBaseUrl);
    this.fileloc3 = UrlUtils.toAbsoluteUrl(this.fileloc3, apiBaseUrl);
  }

  private static String getMsgTypeName(String msgType) {
    if (msgType == null) return null;
    return switch (msgType) {
      case "S" -> "SMS";
      case "L" -> "LMS";
      case "M" -> "MMS";
      default -> msgType;
    };
  }
}
