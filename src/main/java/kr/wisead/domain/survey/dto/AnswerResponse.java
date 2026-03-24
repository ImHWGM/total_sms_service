package kr.wisead.domain.survey.dto;

import java.time.LocalDateTime;
import kr.wisead.domain.survey.entity.SurveyAnswer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 설문 답변 응답 DTO */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnswerResponse {

  private Integer answerSeq;
  private Integer eventSeq;
  private Integer questionSeq;
  private Integer userSeq;
  private Integer itemSeq;
  private String questionType;
  private String questionTypeDetail;
  private String answer;
  private String otherText;
  private String filePath;
  private LocalDateTime regDate;

  public static AnswerResponse from(SurveyAnswer answer) {
    return AnswerResponse.builder()
        .answerSeq(answer.getAnswerSeq())
        .eventSeq(answer.getEventSeq())
        .questionSeq(answer.getQuestionSeq())
        .userSeq(answer.getUserSeq())
        .itemSeq(answer.getItemSeq())
        .questionType(answer.getQuestionType())
        .questionTypeDetail(answer.getQuestionTypeDetail())
        .answer(answer.getAnswer())
        .otherText(answer.getOtherText())
        .filePath(answer.getFilePath())
        .regDate(answer.getRegDate())
        .build();
  }
}
