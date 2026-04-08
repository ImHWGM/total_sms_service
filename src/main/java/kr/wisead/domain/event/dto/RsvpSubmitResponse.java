package kr.wisead.domain.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 통합 링크 RSVP 제출 응답. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RsvpSubmitResponse {
  private String registType;
  private String prevRegistType;
}
