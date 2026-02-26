package kr.wisead.domain.event.dto;

import lombok.Builder;
import lombok.Getter;

/** RSVP 제출 응답 DTO */
@Getter
@Builder
public class RsvpResponse {

  private String participantName;
  private String response;
}
