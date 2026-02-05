package kr.wisead.domain.message.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 광고 문자 발송 요청 DTO */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdMessageRequest {

  /** 발송 타입: ip-direct(직접등록), excel(엑셀대량발송) */
  private String reqType;

  /** 메시지 타입: SMS, LMS, MMS */
  @NotBlank(message = "메시지 타입은 필수입니다.")
  @Pattern(regexp = "^(SMS|LMS|MMS).*$", message = "메시지 타입은 SMS, LMS, MMS 중 하나여야 합니다.")
  private String messageTypeIs;

  /** 발신번호 */
  @NotBlank(message = "발신번호는 필수입니다.")
  @Pattern(regexp = "^\\d{2,4}(-?\\d{3,4}){1,2}$", message = "올바른 발신번호 형식이 아닙니다.")
  private String reqNum;

  /** 제목 (LMS/MMS일 경우) */
  @Size(max = 120, message = "제목은 120자 이내로 입력해주세요.")
  private String sendTtl;

  /** 발송 시간 타입: direct(즉시), schedule(예약) */
  private String sendTimeType;

  /** 예약 발송 시간 (예약 발송일 경우) */
  private LocalDateTime reqDate;

  /** 중복번호 삭제 여부: Y/N */
  private String delDuplicateNum;

  /** 문자 내용 (엑셀 대량발송일 경우 기본 내용) */
  @Size(max = 4000, message = "메시지 내용은 4000자 이내로 입력해주세요.")
  private String contTxt;

  /** 발송 대상 목록 (직접등록일 경우) */
  @Valid private List<Recipient> recipients;

  // MMS 파일 정보
  private Integer fileCnt;
  private String fileloc1;
  private String fileloc2;
  private String fileloc3;

  /** 수신자 정보 */
  @Getter
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class Recipient {
    @NotBlank(message = "수신번호는 필수입니다.")
    @Pattern(regexp = "^\\d{2,3}-?\\d{3,4}-?\\d{4}$", message = "올바른 수신번호 형식이 아닙니다.")
    private String recPhone;

    private String contTxt; // 개별 문자 내용
    private String repChar01; // 대치문자1
    private String repChar02; // 대치문자2
    private String repChar03; // 대치문자3

    @Builder
    public Recipient(
        String recPhone, String contTxt, String repChar01, String repChar02, String repChar03) {
      this.recPhone = recPhone;
      this.contTxt = contTxt;
      this.repChar01 = repChar01;
      this.repChar02 = repChar02;
      this.repChar03 = repChar03;
    }

    /** 대치문자 적용된 최종 메시지 생성 */
    public String getProcessedContent(String baseContent) {
      String content = this.contTxt != null ? this.contTxt : baseContent;

      if (content == null) {
        return null;
      }

      if (repChar01 != null && !repChar01.isEmpty()) {
        content = content.replace("#대치문자1#", repChar01);
      }
      if (repChar02 != null && !repChar02.isEmpty()) {
        content = content.replace("#대치문자2#", repChar02);
      }
      if (repChar03 != null && !repChar03.isEmpty()) {
        content = content.replace("#대치문자3#", repChar03);
      }

      return content;
    }

    /** 전화번호 정규화 (하이픈 제거) */
    public String getNormalizedPhone() {
      return recPhone != null ? recPhone.replaceAll("-", "") : null;
    }
  }

  @Builder
  public AdMessageRequest(
      String reqType,
      String messageTypeIs,
      String reqNum,
      String sendTtl,
      String sendTimeType,
      LocalDateTime reqDate,
      String delDuplicateNum,
      String contTxt,
      List<Recipient> recipients,
      Integer fileCnt,
      String fileloc1,
      String fileloc2,
      String fileloc3) {
    this.reqType = reqType;
    this.messageTypeIs = messageTypeIs;
    this.reqNum = reqNum;
    this.sendTtl = sendTtl;
    this.sendTimeType = sendTimeType;
    this.reqDate = reqDate;
    this.delDuplicateNum = delDuplicateNum;
    this.contTxt = contTxt;
    this.recipients = recipients;
    this.fileCnt = fileCnt;
    this.fileloc1 = fileloc1;
    this.fileloc2 = fileloc2;
    this.fileloc3 = fileloc3;
  }

  /** 직접등록 방식인지 확인 */
  public boolean isDirectInput() {
    return "ip-direct".equals(reqType);
  }

  /** 즉시 발송인지 확인 */
  public boolean isImmediate() {
    return "direct".equals(sendTimeType)
        || reqDate == null
        || reqDate.isBefore(LocalDateTime.now().plusMinutes(1));
  }

  /** 중복번호 삭제 여부 */
  public boolean shouldDeleteDuplicate() {
    return "Y".equalsIgnoreCase(delDuplicateNum);
  }

  /** 발신번호 정규화 (하이픈 제거) */
  public String getNormalizedCallback() {
    return reqNum != null ? reqNum.replaceAll("-", "") : null;
  }

  /** 메시지 타입 코드 변환 (S/L/M) */
  public String getMsgTypeCode() {
    if (messageTypeIs == null) {
      return "S";
    }
    if (messageTypeIs.startsWith("SMS")) {
      return "S";
    } else if (messageTypeIs.startsWith("LMS")) {
      return "L";
    } else if (messageTypeIs.startsWith("MMS")) {
      return "M";
    }
    return "S";
  }

  /** 메시지 타입 라벨 (SMS/LMS/MMS) */
  public String getMsgTypeLabel() {
    if (messageTypeIs == null) {
      return "SMS";
    }
    if (messageTypeIs.startsWith("SMS")) {
      return "SMS";
    } else if (messageTypeIs.startsWith("LMS")) {
      return "LMS";
    } else if (messageTypeIs.startsWith("MMS")) {
      return "MMS";
    }
    return "SMS";
  }
}
