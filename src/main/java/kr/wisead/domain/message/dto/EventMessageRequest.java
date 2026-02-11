package kr.wisead.domain.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 행사참여자 문자 발송 요청 DTO LMS 전용 (99원/건) */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventMessageRequest {

  @NotNull(message = "이벤트 시퀀스는 필수입니다.")
  private Integer eventSeq;

  @NotBlank(message = "발신번호는 필수입니다.")
  private String callback;

  private String subject;

  @NotBlank(message = "메시지 내용은 필수입니다.")
  private String text;

  /**
   * 발송 유형 IMMEDIATE: 즉시발송 (QR링크 직접 발송) SELECTIVE: 선택발송 (접속링크 발송 → 참석 확인 시 QR 노출) REVIEW: 검토발송 (접속링크
   * 발송 → 인증정보 입력 → 2차 QR 발송)
   */
  private String sendType;

  private List<Receiver> receivers;

  private LocalDateTime requestTime; // 예약 발송 시간 (null이면 즉시발송)

  private boolean delDuplicateNum; // 중복 번호 삭제 여부

  // 이벤트 정보 (치환용)
  private String eventName; // 이벤트명 (#이벤트명# 치환)
  private String eventPeriod; // 이벤트 기간 (#이벤트기간# 치환)
  private String eventLocation; // 이벤트 장소 (#이벤트장소# 치환)

  @Builder
  public EventMessageRequest(
      Integer eventSeq,
      String callback,
      String subject,
      String text,
      String sendType,
      List<Receiver> receivers,
      LocalDateTime requestTime,
      boolean delDuplicateNum,
      String eventName,
      String eventPeriod,
      String eventLocation) {
    this.eventSeq = eventSeq;
    this.callback = callback;
    this.subject = subject;
    this.text = text;
    this.sendType = sendType;
    this.receivers = receivers;
    this.requestTime = requestTime;
    this.delDuplicateNum = delDuplicateNum;
    this.eventName = eventName;
    this.eventPeriod = eventPeriod;
    this.eventLocation = eventLocation;
  }

  /** 즉시 발송 여부 */
  public boolean isImmediate() {
    return requestTime == null || requestTime.isBefore(LocalDateTime.now().plusMinutes(1));
  }

  /** 발신번호 정규화 (하이픈 제거) */
  public String getNormalizedCallback() {
    return callback != null ? callback.replaceAll("-", "") : null;
  }

  /** 수신자 정보 */
  @Getter
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class Receiver {
    private String phone;
    private Long participantSeq; // 행사 참가자 시퀀스
    private Integer surveyUserSeq; // 설문 사용자 시퀀스
    private String checkCode; // 체크코드 (QR용)
    private String name; // 참가자 이름 (#이름# 치환)
    private String qrLink; // QR 링크 (#QR링크# 치환)
    private String accessLink; // 접속 링크 (#접속링크# 치환)
    private String repChar01; // 대치문자1
    private String repChar02; // 대치문자2

    @Builder
    public Receiver(
        String phone,
        Long participantSeq,
        Integer surveyUserSeq,
        String checkCode,
        String name,
        String qrLink,
        String accessLink,
        String repChar01,
        String repChar02) {
      this.phone = phone;
      this.participantSeq = participantSeq;
      this.surveyUserSeq = surveyUserSeq;
      this.checkCode = checkCode;
      this.name = name;
      this.qrLink = qrLink;
      this.accessLink = accessLink;
      this.repChar01 = repChar01;
      this.repChar02 = repChar02;
    }

    /** 전화번호 정규화 (하이픈 제거) */
    public String getNormalizedPhone() {
      return phone != null ? phone.replaceAll("-", "") : null;
    }
  }
}
