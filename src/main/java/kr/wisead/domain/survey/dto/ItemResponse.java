package kr.wisead.domain.survey.dto;

import kr.wisead.domain.survey.entity.OtherType;
import kr.wisead.domain.survey.entity.SurveyItem;
import lombok.*;

/** 항목(보기) 응답 DTO */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ItemResponse {

  private Integer itemSeq; // 항목 시퀀스
  private Integer eventSeq; // 이벤트 시퀀스
  private Integer questionSeq; // 문항 시퀀스
  private String item; // 항목 내용
  private String itemValue; // 항목 값
  private String itemImg; // 항목 이미지
  private Integer order; // 순서
  private Integer jumpQuestion; // 분기 문항 시퀀스
  private String otherYn; // 기타 항목 여부 (Y/N)
  private String otherPlaceholder; // 기타 항목 입력 안내 문구
  private OtherType otherType; // 기타 답변 유형 (SA/NE/SO/EM/AD/CU)

  // 통계용
  private Integer answerCount; // 해당 보기 선택 수
  private Double percentage; // 선택 비율

  /** Entity -> Response 변환 */
  public static ItemResponse from(SurveyItem entity) {
    return ItemResponse.builder()
        .itemSeq(entity.getItemSeq())
        .eventSeq(entity.getEventSeq())
        .questionSeq(entity.getQuestionSeq())
        .item(entity.getItem())
        .itemValue(entity.getItemValue())
        .itemImg(entity.getItemImg())
        .order(entity.getOrder())
        .jumpQuestion(entity.getJumpQuestion())
        .otherYn(entity.getOtherYn())
        .otherPlaceholder(entity.getOtherPlaceholder())
        .otherType(entity.getOtherType() != null ? entity.getOtherType() : OtherType.SA)
        .build();
  }

  /** 통계 정보 추가 */
  public ItemResponse withStats(Integer answerCount, Integer totalAnswers) {
    this.answerCount = answerCount;
    if (totalAnswers != null && totalAnswers > 0) {
      this.percentage = (double) answerCount / totalAnswers * 100;
    }
    return this;
  }

  /** 이미지 URL을 절대 경로로 변환 */
  public void withFullImageUrls(String apiBaseUrl) {
    this.itemImg = kr.wisead.common.util.UrlUtils.toAbsoluteUrl(this.itemImg, apiBaseUrl);
  }
}
