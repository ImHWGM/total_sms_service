package kr.wisead.domain.event.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 사전설문 기간 projection (SurveyMasterMapper.selectPreSurveyDatesByEventSeq) */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyMasterPreSurveyDto {
  private LocalDateTime preSurveyStartDate;
  private LocalDateTime preSurveyEndDate;
}
