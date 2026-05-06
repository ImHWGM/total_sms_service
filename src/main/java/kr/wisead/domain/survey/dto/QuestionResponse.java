package kr.wisead.domain.survey.dto;

import java.util.List;
import kr.wisead.domain.survey.entity.SurveyQuestion;
import lombok.*;

/** 문항 응답 DTO */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class QuestionResponse {

  private Integer questionSeq; // 문항 시퀀스
  private Integer eventSeq; // 이벤트 시퀀스
  private String questionType; // 문항 종류
  private String questionTypeDetail; // 문항 종류 상세
  private String question; // 문항 내용
  private String questionImg; // 문항 이미지
  private Integer order; // 순서
  private String foreignAllow; // 외국인 허용 여부
  private String requiredYn; // 필수 응답 여부 (Y:필수, N:선택)

  // 객관식 문항의 보기 목록
  private List<ItemResponse> items;

  // 통계용 (집계 시 사용)
  private Integer answerCount; // 응답 수

  /** Entity -> Response 변환 */
  public static QuestionResponse from(SurveyQuestion entity) {
    return QuestionResponse.builder()
        .questionSeq(entity.getQuestionSeq())
        .eventSeq(entity.getEventSeq())
        .questionType(normalizeQuestionType(entity.getQuestionType()))
        .questionTypeDetail(entity.getQuestionTypeDetail())
        .question(entity.getQuestion())
        .questionImg(entity.getQuestionImg())
        .order(entity.getOrder())
        .foreignAllow(entity.getForeignAllow())
        .requiredYn(entity.getRequiredYn())
        .build();
  }

  /** 레거시 questionType 정규화 (SAA -> SA) */
  private static String normalizeQuestionType(String questionType) {
    if ("SAA".equals(questionType)) {
      return "SA";
    }
    return questionType;
  }

  /** 보기 목록 추가 */
  public QuestionResponse withItems(List<ItemResponse> items) {
    this.items = items;
    return this;
  }

  public QuestionResponse withReplacedQuestion(String replacedQuestion) {
    this.question = replacedQuestion;
    return this;
  }

  /** 응답 수 설정 */
  public QuestionResponse withAnswerCount(Integer answerCount) {
    this.answerCount = answerCount;
    return this;
  }

  /** 이미지 URL을 절대 경로로 변환 */
  public QuestionResponse withFullImageUrls(String apiBaseUrl) {
    this.questionImg = kr.wisead.common.util.UrlUtils.toAbsoluteUrl(this.questionImg, apiBaseUrl);
    if (this.items != null) {
      this.items.forEach(item -> item.withFullImageUrls(apiBaseUrl));
    }
    return this;
  }
}
