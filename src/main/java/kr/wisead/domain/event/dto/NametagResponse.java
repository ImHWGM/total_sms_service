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
        .build();
  }
}
