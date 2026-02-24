package kr.wisead.domain.message.dto;

import java.time.LocalDateTime;
import kr.wisead.common.util.UrlUtils;
import kr.wisead.domain.message.entity.MsgResult;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 발송 이력 응답 DTO */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MsgResultResponse {

  private Integer mseq;
  private String msgType;
  private String msgTypeName;
  private String dstaddr; // 수신번호
  private String callback; // 발신번호
  private Integer stat;
  private String subject;
  private String text;

  // 첨부파일
  private Integer filecnt;
  private String fileloc1;
  private String fileloc2;
  private String fileloc3;

  // 시간
  private LocalDateTime requestTime;
  private LocalDateTime sendTime;
  private LocalDateTime reportTime;

  // 결과
  private String telecom;
  private String result;
  private String resultName;

  // 확장 정보
  private String sendType; // 발송 타입명
  private String regId; // 등록자 ID

  @Builder
  public MsgResultResponse(
      Integer mseq,
      String msgType,
      String msgTypeName,
      String dstaddr,
      String callback,
      Integer stat,
      String subject,
      String text,
      Integer filecnt,
      String fileloc1,
      String fileloc2,
      String fileloc3,
      LocalDateTime requestTime,
      LocalDateTime sendTime,
      LocalDateTime reportTime,
      String telecom,
      String result,
      String resultName,
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
    this.requestTime = requestTime;
    this.sendTime = sendTime;
    this.reportTime = reportTime;
    this.telecom = telecom;
    this.result = result;
    this.resultName = resultName;
    this.sendType = sendType;
    this.regId = regId;
  }

  /** Entity -> Response 변환 */
  public static MsgResultResponse from(MsgResult entity) {
    return MsgResultResponse.builder()
        .mseq(entity.getMseq())
        .msgType(entity.getMsgType())
        .msgTypeName(entity.getMsgTypeName())
        .dstaddr(entity.getDstaddr())
        .callback(entity.getCallback())
        .stat(entity.getStat())
        .subject(entity.getSubject())
        .text(entity.getText())
        .filecnt(entity.getFilecnt())
        .fileloc1(entity.getFileloc1())
        .fileloc2(entity.getFileloc2())
        .fileloc3(entity.getFileloc3())
        .requestTime(entity.getRequestTime())
        .sendTime(entity.getSendTime())
        .reportTime(entity.getReportTime())
        .telecom(entity.getTelecom())
        .result(entity.getResult())
        .resultName(entity.getResultName())
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
}
