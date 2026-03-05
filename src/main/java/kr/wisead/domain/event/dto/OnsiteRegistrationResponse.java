package kr.wisead.domain.event.dto;

import java.time.LocalDateTime;
import kr.wisead.domain.event.entity.EventParticipant;
import lombok.*;

/** 현장 등록 응답 DTO */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OnsiteRegistrationResponse {

  private Long seq;
  private Integer eventSeq;
  private String checkCode;
  private String userName;
  private String userPhone;
  private String userEmail;
  private String department;
  private String position;
  private String participantType;
  private String registType;
  private LocalDateTime attendTime;
  private String eventName;
  private String qrCodeUrl;

  /** Entity -> Response 변환 (전화번호는 평문으로 전달) */
  public static OnsiteRegistrationResponse from(
      EventParticipant entity, String plainPhone, LocalDateTime attendTime) {
    return OnsiteRegistrationResponse.builder()
        .seq(entity.getSeq())
        .eventSeq(entity.getEventSeq())
        .checkCode(entity.getCheckCode())
        .userName(entity.getUserName())
        .userPhone(plainPhone)
        .userEmail(entity.getUserEmail())
        .department(entity.getDepartment())
        .position(entity.getPosition())
        .participantType(entity.getParticipantType())
        .registType("현장등록")
        .attendTime(attendTime)
        .eventName(entity.getEventName())
        .build();
  }

  /** QR 코드 URL 설정 */
  public OnsiteRegistrationResponse withQrCodeUrl(String baseUrl) {
    this.qrCodeUrl = baseUrl + "/event/" + this.eventSeq + "/check/" + this.checkCode;
    return this;
  }
}
