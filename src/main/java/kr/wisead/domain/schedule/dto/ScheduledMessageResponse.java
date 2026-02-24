package kr.wisead.domain.schedule.dto;

import java.time.LocalDateTime;
import kr.wisead.common.util.UrlUtils;
import kr.wisead.domain.schedule.entity.ScheduledMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 예약 메시지 응답 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledMessageResponse {

  private Integer mSeq;
  private String msgType;
  private String msgTypeName;
  private String dstAddr;
  private String callBack;
  private Integer stat;
  private String statName;
  private String subject;
  private String text;
  private LocalDateTime insertTime;
  private LocalDateTime requestTime;
  private String sendType; // 발송타입 (extCol2)
  private String userId; // 등록자 ID (extCol3)
  private Integer messageCount;
  private Integer fileCnt;
  private String fileLoc1;
  private String fileLoc2;
  private String fileLoc3;

  public static ScheduledMessageResponse from(ScheduledMessage entity) {
    if (entity == null) return null;

    return ScheduledMessageResponse.builder()
        .mSeq(entity.getMSeq())
        .msgType(entity.getMsgType())
        .msgTypeName(translateMsgType(entity.getMsgType()))
        .dstAddr(maskPhoneNumber(entity.getDstAddr()))
        .callBack(entity.getCallBack())
        .stat(entity.getStat())
        .statName(translateStat(entity.getStat()))
        .subject(entity.getSubject())
        .text(entity.getText())
        .insertTime(entity.getInsertTime())
        .requestTime(entity.getRequestTime())
        .sendType(entity.getExtCol2())
        .userId(entity.getExtCol3())
        .messageCount(entity.getMessageCount())
        .fileCnt(entity.getFileCnt())
        .fileLoc1(entity.getFileLoc1())
        .fileLoc2(entity.getFileLoc2())
        .fileLoc3(entity.getFileLoc3())
        .build();
  }

  /** 파일 경로를 절대 URL로 변환 */
  public void withFullImageUrls(String apiBaseUrl) {
    this.fileLoc1 = UrlUtils.toAbsoluteUrl(this.fileLoc1, apiBaseUrl);
    this.fileLoc2 = UrlUtils.toAbsoluteUrl(this.fileLoc2, apiBaseUrl);
    this.fileLoc3 = UrlUtils.toAbsoluteUrl(this.fileLoc3, apiBaseUrl);
  }

  /** 메시지 타입 코드를 명칭으로 변환 */
  private static String translateMsgType(String msgType) {
    return switch (msgType) {
      case "S" -> "SMS";
      case "L" -> "LMS";
      case "M" -> "MMS";
      default -> msgType;
    };
  }

  /** 상태 코드를 명칭으로 변환 */
  private static String translateStat(Integer stat) {
    return stat != null && stat == 0 ? "대기" : "처리중";
  }

  /** 전화번호 마스킹 */
  private static String maskPhoneNumber(String phone) {
    if (phone == null || phone.length() < 7) return phone;

    // 010-1234-5678 -> 010-****-5678
    if (phone.length() == 11) {
      return phone.substring(0, 3) + "-****-" + phone.substring(7);
    }
    // 그 외 형식
    return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
  }
}
