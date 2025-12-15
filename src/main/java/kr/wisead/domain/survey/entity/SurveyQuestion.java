package kr.wisead.domain.survey.entity;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 설문 문항 Entity (TB_SURVEY_QUESTION)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SurveyQuestion {

    private Integer questionSeq;            // 문항 시퀀스
    private Integer eventSeq;               // 이벤트 시퀀스
    private String questionType;            // 문항 종류 (MC:객관식, SA:주관식)
    private String questionTypeDetail;      // 문항 종류 상세 (MCS:단일선택, MCM:복수선택, SA:단답, FE:파일)
    private String question;                // 문항 내용
    private String questionImg;             // 문항 이미지
    private Integer order;                  // 순서
    private LocalDateTime regDate;          // 등록일
    private String regId;                   // 등록 ID

    /**
     * 문항 생성
     */
    public static SurveyQuestion create(Integer eventSeq, String questionType,
                                         String questionTypeDetail, String question,
                                         Integer order, String regId) {
        return SurveyQuestion.builder()
                .eventSeq(eventSeq)
                .questionType(questionType)
                .questionTypeDetail(questionTypeDetail)
                .question(question)
                .order(order)
                .regId(regId)
                .build();
    }

    /**
     * 문항 이미지 설정
     */
    public void setQuestionImg(String questionImg) {
        this.questionImg = questionImg;
    }

    /**
     * 객관식 문항인지 확인
     */
    public boolean isMultipleChoice() {
        return "MC".equals(this.questionType);
    }

    /**
     * 복수 선택 가능한지 확인
     */
    public boolean isMultiSelect() {
        return "MCM".equals(this.questionTypeDetail);
    }

    /**
     * 파일 업로드 문항인지 확인
     */
    public boolean isFileUpload() {
        return "FE".equals(this.questionTypeDetail);
    }
}
