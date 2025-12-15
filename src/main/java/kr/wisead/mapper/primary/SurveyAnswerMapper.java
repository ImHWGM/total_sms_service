package kr.wisead.mapper.primary;

import kr.wisead.domain.survey.entity.SurveyAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 설문 답변 Mapper (Primary DB)
 */
@Mapper
public interface SurveyAnswerMapper {

    /**
     * 이벤트의 답변 수
     */
    int countByEventSeq(@Param("eventSeq") Integer eventSeq);

    /**
     * 문항별 응답자 수
     */
    int countByQuestionSeq(@Param("eventSeq") Integer eventSeq,
                           @Param("questionSeq") Integer questionSeq);

    /**
     * 항목별 선택 수 (단일/복수선택)
     */
    int countByItemSeq(@Param("eventSeq") Integer eventSeq,
                       @Param("questionSeq") Integer questionSeq,
                       @Param("itemSeq") Integer itemSeq);

    /**
     * 복수선택 항목값별 선택 수
     */
    int countByItemValueMCM(@Param("eventSeq") Integer eventSeq,
                            @Param("questionSeq") Integer questionSeq,
                            @Param("itemValue") String itemValue);

    /**
     * 사용자의 답변 목록 조회
     */
    List<SurveyAnswer> selectByUserSeq(@Param("eventSeq") Integer eventSeq,
                                        @Param("userSeq") Integer userSeq);

    /**
     * 답변 등록
     */
    int insert(SurveyAnswer answer);

    /**
     * 사용자의 특정 문항 답변이 있는지 확인
     */
    int checkAnswerExists(@Param("eventSeq") Integer eventSeq,
                          @Param("userSeq") Integer userSeq,
                          @Param("questionSeq") Integer questionSeq);

    /**
     * 사용자의 모든 답변 삭제 (재제출 시)
     */
    int deleteByUserSeq(@Param("eventSeq") Integer eventSeq,
                        @Param("userSeq") Integer userSeq);
}
