package kr.wisead.mapper.primary;

import java.util.List;
import kr.wisead.domain.survey.entity.SurveyAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 설문 답변 Mapper (Primary DB) */
@Mapper
public interface SurveyAnswerMapper {

  /** 이벤트의 답변 수 */
  int countByEventSeq(@Param("eventSeq") Integer eventSeq);

  /** 문항별 응답자 수 */
  int countByQuestionSeq(
      @Param("eventSeq") Integer eventSeq, @Param("questionSeq") Integer questionSeq);

  /** 항목별 선택 수 (단일/복수선택) */
  int countByItemSeq(
      @Param("eventSeq") Integer eventSeq,
      @Param("questionSeq") Integer questionSeq,
      @Param("itemSeq") Integer itemSeq);

  /** 복수선택 항목값별 선택 수 */
  int countByItemValueMCM(
      @Param("eventSeq") Integer eventSeq,
      @Param("questionSeq") Integer questionSeq,
      @Param("itemValue") String itemValue);

  /** 사용자의 답변 목록 조회 */
  List<SurveyAnswer> selectByUserSeq(
      @Param("eventSeq") Integer eventSeq, @Param("userSeq") Integer userSeq);

  /** 답변 등록 */
  int insert(SurveyAnswer answer);

  /** 사용자의 특정 문항 답변이 있는지 확인 */
  int checkAnswerExists(
      @Param("eventSeq") Integer eventSeq,
      @Param("userSeq") Integer userSeq,
      @Param("questionSeq") Integer questionSeq);

  /** 사용자의 모든 답변 삭제 (재제출 시) */
  int deleteByUserSeq(@Param("eventSeq") Integer eventSeq, @Param("userSeq") Integer userSeq);

  /** 이벤트의 전체 답변 목록 조회 */
  List<SurveyAnswer> selectByEventSeq(@Param("eventSeq") Integer eventSeq);

  /** 문항별 답변 목록 조회 */
  List<SurveyAnswer> selectByQuestionSeq(
      @Param("eventSeq") Integer eventSeq, @Param("questionSeq") Integer questionSeq);

  /** 답변 일괄 등록 */
  int insertBatch(List<SurveyAnswer> answers);

  /** 사용자의 특정 개인정보 타입 답변 조회 (NE, CU, SO, EM, AD) */
  String selectAnswerByTypeDetail(
      @Param("eventSeq") Integer eventSeq,
      @Param("userSeq") Integer userSeq,
      @Param("questionTypeDetail") String questionTypeDetail);
  // ===== 레거시 PII 평문 백필 (일회성, SurveyPiiBackfillRunner 전용) =====

  /** ANSWER 컬럼의 NE/AD/CU/EM 평문(미암호, 'PII:' 접두 없음) 후보 조회. markers=파기/플레이스홀더 제외 목록 */
  java.util.List<kr.wisead.batch.SurveyPiiBackfillRow> selectBackfillAnswerCandidates(
      @Param("markers") java.util.List<String> markers);

  /** OTHER_TEXT 컬럼의 NE/AD/CU/EM 평문 후보 조회 (SURVEY_ITEM.OTHER_TYPE 조인). markers=제외 목록 */
  java.util.List<kr.wisead.batch.SurveyPiiBackfillRow> selectBackfillOtherTextCandidates(
      @Param("markers") java.util.List<String> markers);

  /** ANSWER 단건 갱신 (백필 전용) */
  int updateAnswerValueById(@Param("answerSeq") Integer answerSeq, @Param("value") String value);

  /** OTHER_TEXT 단건 갱신 (백필 전용) */
  int updateOtherTextValueById(@Param("answerSeq") Integer answerSeq, @Param("value") String value);
}
