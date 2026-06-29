package kr.wisead.batch;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 설문 PII 레거시 평문 백필 대상 행.
 *
 * <p>{@code type}은 암호화 유형 키(NE/AD/CU/EM). ANSWER 컬럼은 답변 row의 QUESTION_TYPE_DETAIL, OTHER_TEXT 컬럼은 해당
 * 문항 '기타' 항목의 OTHER_TYPE 에서 온다. {@code value}는 현재 저장된 평문.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SurveyPiiBackfillRow {
  private Integer answerSeq;
  private String type;
  private String value;
}
