package kr.wisead.domain.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 문자 발송용 참가자 정보 응답 DTO */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipantForMessageResponse {

  private Long participantSeq;
  private Integer surveyUserSeq;
  private String name;
  private String phone;
  private String checkCode;
  private String department;
  private String position;
  private String participantType;
  private String registType;
  private boolean messageSent;
}
