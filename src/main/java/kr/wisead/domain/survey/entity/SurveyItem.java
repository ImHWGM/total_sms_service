package kr.wisead.domain.survey.entity;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 설문 항목(보기) Entity (TB_SURVEY_ITEM)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SurveyItem {

    private Integer itemSeq;                // 항목 시퀀스
    private Integer eventSeq;               // 이벤트 시퀀스
    private Integer questionSeq;            // 문항 시퀀스
    private String item;                    // 항목 내용
    private String itemValue;               // 항목 값
    private String itemImg;                 // 항목 이미지
    private Integer order;                  // 순서
    private Integer jumpQuestion;           // 분기 문항 시퀀스
    private LocalDateTime regDate;          // 등록일
    private String regId;                   // 등록 ID

    /**
     * 항목 생성
     */
    public static SurveyItem create(Integer eventSeq, Integer questionSeq,
                                     String item, String itemValue,
                                     Integer order, String regId) {
        return SurveyItem.builder()
                .eventSeq(eventSeq)
                .questionSeq(questionSeq)
                .item(item)
                .itemValue(itemValue)
                .order(order)
                .regId(regId)
                .build();
    }

    /**
     * 분기 문항 설정
     */
    public void setJumpQuestion(Integer jumpQuestion) {
        this.jumpQuestion = jumpQuestion;
    }

    /**
     * 항목 이미지 설정
     */
    public void setItemImg(String itemImg) {
        this.itemImg = itemImg;
    }

    /**
     * 분기가 있는지 확인
     */
    public boolean hasBranch() {
        return this.jumpQuestion != null && this.jumpQuestion > 0;
    }
}
