package kr.wisead.domain.survey.entity;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 설문 참여자별 치환문자 값 (SURVEY_USER_REP_CHAR).
 *
 * <p>First-Write-Wins(INSERT IGNORE): 재발송 시 기존 row 유지. 빈 값은 row 미생성 → 빈 문자열 치환.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SurveyUserRepChar {

  private Integer userSeq; // 설문 참여자 시퀀스 (SURVEY_USER.SEQ)
  private Integer repCharIdx; // 치환 슬롯 인덱스 (1~5)
  private String repCharVal; // 치환 값
  private LocalDateTime regDate; // 등록일

  /** value가 null/blank이면 null 반환 (insert 대상 제외). */
  public static SurveyUserRepChar create(Integer userSeq, Integer idx, String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return SurveyUserRepChar.builder().userSeq(userSeq).repCharIdx(idx).repCharVal(value).build();
  }
}
