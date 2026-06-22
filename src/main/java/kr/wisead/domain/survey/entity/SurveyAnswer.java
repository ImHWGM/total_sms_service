package kr.wisead.domain.survey.entity;

import java.time.LocalDateTime;
import lombok.*;

/** 설문 답변 Entity (SURVEY_ANSWER) */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SurveyAnswer {

  private Integer answerSeq; // 답변 시퀀스
  private Integer eventSeq; // 이벤트 시퀀스
  private Integer questionSeq; // 문항 시퀀스
  private Integer userSeq; // 사용자 시퀀스
  private Integer itemSeq; // 항목 시퀀스 (객관식)
  private String questionType; // 문항 종류
  private String questionTypeDetail; // 문항 종류 상세
  private String answer; // 답변 내용
  private String otherText; // 기타 항목 텍스트
  private String filePath; // 파일 경로 (파일업로드 문항)
  private LocalDateTime regDate; // 등록일

  /** 객관식 답변 생성 */
  public static SurveyAnswer createMultipleChoice(
      Integer eventSeq,
      Integer questionSeq,
      Integer userSeq,
      Integer itemSeq,
      String questionType,
      String questionTypeDetail,
      String answer) {
    return SurveyAnswer.builder()
        .eventSeq(eventSeq)
        .questionSeq(questionSeq)
        .userSeq(userSeq)
        .itemSeq(itemSeq)
        .questionType(questionType)
        .questionTypeDetail(questionTypeDetail)
        .answer(answer)
        .build();
  }

  /** 주관식 답변 생성 */
  public static SurveyAnswer createShortAnswer(
      Integer eventSeq,
      Integer questionSeq,
      Integer userSeq,
      Integer itemSeq,
      String questionTypeDetail,
      String answer) {
    return SurveyAnswer.builder()
        .eventSeq(eventSeq)
        .questionSeq(questionSeq)
        .userSeq(userSeq)
        .itemSeq(itemSeq)
        .questionType("SA")
        .questionTypeDetail(questionTypeDetail)
        .answer(answer)
        .build();
  }

  /** 파일 업로드 답변 생성 */
  public static SurveyAnswer createFileUpload(
      Integer eventSeq, Integer questionSeq, Integer userSeq, Integer itemSeq, String filePath) {
    return SurveyAnswer.builder()
        .eventSeq(eventSeq)
        .questionSeq(questionSeq)
        .userSeq(userSeq)
        .itemSeq(itemSeq)
        .questionType("SA")
        .questionTypeDetail("FE")
        .filePath(filePath)
        .build();
  }

  /** 답변 내용 설정 (복호화 후처리용) */
  public void setAnswer(String answer) {
    this.answer = answer;
  }

  /** 기타 텍스트 설정 */
  public void setOtherText(String otherText) {
    this.otherText = otherText;
  }

  /** 파일 업로드 답변인지 확인 */
  public boolean isFileUpload() {
    return "FE".equals(this.questionTypeDetail);
  }

  /** 복수 선택 답변인지 확인 */
  public boolean isMultiSelect() {
    return "MCM".equals(this.questionTypeDetail);
  }
}
