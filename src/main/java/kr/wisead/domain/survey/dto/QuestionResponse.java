package kr.wisead.domain.survey.dto;

import kr.wisead.domain.survey.entity.SurveyQuestion;
import lombok.*;

import java.util.List;

/**
 * 문항 응답 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class QuestionResponse {

    private Integer questionSeq;            // 문항 시퀀스
    private Integer eventSeq;               // 이벤트 시퀀스
    private String questionType;            // 문항 종류
    private String questionTypeDetail;      // 문항 종류 상세
    private String question;                // 문항 내용
    private String questionImg;             // 문항 이미지
    private Integer order;                  // 순서

    // 객관식 문항의 보기 목록
    private List<ItemResponse> items;

    // 통계용 (집계 시 사용)
    private Integer answerCount;            // 응답 수

    /**
     * Entity -> Response 변환
     */
    public static QuestionResponse from(SurveyQuestion entity) {
        return QuestionResponse.builder()
                .questionSeq(entity.getQuestionSeq())
                .eventSeq(entity.getEventSeq())
                .questionType(entity.getQuestionType())
                .questionTypeDetail(entity.getQuestionTypeDetail())
                .question(entity.getQuestion())
                .questionImg(entity.getQuestionImg())
                .order(entity.getOrder())
                .build();
    }

    /**
     * 보기 목록 추가
     */
    public QuestionResponse withItems(List<ItemResponse> items) {
        this.items = items;
        return this;
    }

    /**
     * 응답 수 설정
     */
    public QuestionResponse withAnswerCount(Integer answerCount) {
        this.answerCount = answerCount;
        return this;
    }
}
