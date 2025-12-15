package kr.wisead.mapper.primary;

import kr.wisead.domain.survey.entity.SurveyQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 설문 문항 Mapper (Primary DB)
 */
@Mapper
public interface SurveyQuestionMapper {

    /**
     * 이벤트의 문항 목록 조회
     */
    List<SurveyQuestion> selectByEventSeq(@Param("eventSeq") Integer eventSeq);

    /**
     * 문항 상세 조회
     */
    Optional<SurveyQuestion> selectByQuestionSeq(@Param("questionSeq") Integer questionSeq);

    /**
     * 이벤트의 문항 개수
     */
    int countByEventSeq(@Param("eventSeq") Integer eventSeq);

    /**
     * 문항 등록
     */
    int insert(SurveyQuestion question);

    /**
     * 문항 이미지 수정
     */
    int updateQuestionImg(@Param("eventSeq") Integer eventSeq,
                          @Param("questionSeq") Integer questionSeq,
                          @Param("questionImg") String questionImg);

    /**
     * 이벤트의 문항 전체 삭제
     */
    int deleteByEventSeq(@Param("eventSeq") Integer eventSeq);
}
