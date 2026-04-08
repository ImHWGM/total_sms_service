package kr.wisead.domain.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 사전설문 기간 projection (SurveyMasterMapper.selectPreSurveyDatesByEventSeq).
 *
 * <p>날짜 타입은 String으로 유지하여 SurveyMaster.startDate/endDate와 동일한 컨벤션을 따른다. DB DATETIME 값은 MyBatis가
 * 문자열로 읽어온다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyMasterPreSurveyDto {
  private String preSurveyStartDate;
  private String preSurveyEndDate;
}
