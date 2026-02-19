package kr.wisead.domain.event.dto;

import kr.wisead.domain.event.entity.EventParticipant;
import lombok.*;

/** 명찰 데이터 응답 DTO */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NametagResponse {

  private Long participantSeq;
  private Integer eventSeq;
  private String eventName;
  private String name;
  private String department;
  private String position;
  private String participantType;
  private String checkCode;
  private String nametagPrinted;
  private String contact; // 프론트엔드가 명찰에 연락처 표시용
  private String nametagConfig; // 명찰 설정

  /** Entity -> Response 변환 */
  public static NametagResponse from(EventParticipant participant) {
    return NametagResponse.builder()
        .participantSeq(participant.getSeq())
        .eventSeq(participant.getEventSeq())
        .eventName(participant.getEventName())
        .name(participant.getUserName())
        .department(participant.getDepartment())
        .position(participant.getPosition())
        .participantType(participant.getParticipantType())
        .checkCode(participant.getCheckCode())
        .nametagPrinted(participant.getNametagPrinted())
        .nametagConfig(participant.getNametagConfig())
        .build();
  }

  /**
   * Entity -> Response 변환 (이름, 연락처 복호화)
   */
  public static NametagResponse from(EventParticipant participant, String decryptedName,
      String decryptedPhone) {
    return NametagResponse.builder()
        .participantSeq(participant.getSeq())
        .eventSeq(participant.getEventSeq())
        .eventName(participant.getEventName())
        .name(decryptedName)
        .department(participant.getDepartment())
        .position(participant.getPosition())
        .participantType(participant.getParticipantType())
        .checkCode(participant.getCheckCode())
        .nametagPrinted(participant.getNametagPrinted())
        .contact(decryptedPhone)
        .nametagConfig(participant.getNametagConfig())
        .build();
  }
}
