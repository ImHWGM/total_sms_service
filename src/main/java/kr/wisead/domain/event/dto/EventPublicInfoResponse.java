package kr.wisead.domain.event.dto;

import kr.wisead.domain.survey.entity.SurveyMaster;
import lombok.Builder;
import lombok.Getter;

/** 행사 공개 정보 응답 DTO (인증 불필요) */
@Getter
@Builder
public class EventPublicInfoResponse {

  private String eventName;
  private String eventDescription;
  private String eventDescriptionImage;
  private String startDate;
  private String endDate;
  private String eventLocation;
  private String eventOrganizer;

  public static EventPublicInfoResponse from(SurveyMaster event) {
    return EventPublicInfoResponse.builder()
        .eventName(event.getEventName())
        .eventDescription(event.getEventDesc())
        .eventDescriptionImage(event.getEventDescImg())
        .startDate(event.getStartDate())
        .endDate(event.getEndDate())
        .eventLocation(event.getVenue())
        .eventOrganizer(event.getOrganizer())
        .build();
  }
}
