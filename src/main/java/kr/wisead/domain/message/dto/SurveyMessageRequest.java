package kr.wisead.domain.message.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 설문 문자 발송 요청 DTO 설문 문자는 LMS 전용 (제목 포함, 99원/건) */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SurveyMessageRequest {

  @NotNull(message = "이벤트 시퀀스는 필수입니다.")
  private Integer eventSeq;

  private String eventCode;

  @NotBlank(message = "발신번호는 필수입니다.")
  private String callback;

  private String subject;

  @NotBlank(message = "메시지 내용은 필수입니다.")
  private String text;

  private List<Receiver> receivers;

  private LocalDateTime requestTime; // 예약 발송 시간 (null이면 즉시발송)

  private boolean delDuplicateNum; // 중복 번호 삭제 여부

  private boolean adYn; // 광고문자 여부 (true: 수신거부 필터링 + 야간발송제한 적용)

  @Builder
  public SurveyMessageRequest(
      Integer eventSeq,
      String eventCode,
      String callback,
      String subject,
      String text,
      List<Receiver> receivers,
      LocalDateTime requestTime,
      boolean delDuplicateNum,
      boolean adYn) {
    this.eventSeq = eventSeq;
    this.eventCode = eventCode;
    this.callback = callback;
    this.subject = subject;
    this.text = text;
    this.receivers = receivers;
    this.requestTime = requestTime;
    this.delDuplicateNum = delDuplicateNum;
    this.adYn = adYn;
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
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Receiver {
    private String phone;
    private Integer userSeq;
    private String userKey;
    private String repChar01; // 대치문자1
    private String repChar02; // 대치문자2
    private String repChar03; // 대치문자3
    private String surveyRepChar01; // 설문대치1
    private String surveyRepChar02; // 설문대치2
    private String surveyRepChar03; // 설문대치3
    private String surveyRepChar04; // 설문대치4
    private String surveyRepChar05; // 설문대치5

    @Builder
    public Receiver(
        String phone,
        Integer userSeq,
        String userKey,
        String repChar01,
        String repChar02,
        String repChar03,
        String surveyRepChar01,
        String surveyRepChar02,
        String surveyRepChar03,
        String surveyRepChar04,
        String surveyRepChar05) {
      this.phone = phone;
      this.userSeq = userSeq;
      this.userKey = userKey;
      this.repChar01 = repChar01;
      this.repChar02 = repChar02;
      this.repChar03 = repChar03;
      this.surveyRepChar01 = surveyRepChar01;
      this.surveyRepChar02 = surveyRepChar02;
      this.surveyRepChar03 = surveyRepChar03;
      this.surveyRepChar04 = surveyRepChar04;
      this.surveyRepChar05 = surveyRepChar05;
    }

    /** 전화번호 정규화 (하이픈 제거) */
    public String getNormalizedPhone() {
      return phone != null ? phone.replaceAll("-", "") : null;
    }
  }
}
