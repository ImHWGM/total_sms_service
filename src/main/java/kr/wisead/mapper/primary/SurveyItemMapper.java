package kr.wisead.mapper.primary;

import kr.wisead.domain.survey.entity.SurveyItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 설문 항목(보기) Mapper (Primary DB)
 */
@Mapper
public interface SurveyItemMapper {

    /**
     * 이벤트의 전체 항목 목록 조회
     */
    List<SurveyItem> selectByEventSeq(@Param("eventSeq") Integer eventSeq);

    /**
     * 문항의 항목 목록 조회
     */
    List<SurveyItem> selectByQuestionSeq(@Param("eventSeq") Integer eventSeq,
                                          @Param("questionSeq") Integer questionSeq);

    /**
     * 항목 등록
     */
    int insert(SurveyItem item);

    /**
     * 항목 이미지 수정
     */
    int updateItemImg(@Param("eventSeq") Integer eventSeq,
                      @Param("questionSeq") Integer questionSeq,
                      @Param("itemSeq") Integer itemSeq,
                      @Param("itemImg") String itemImg);

    /**
     * 이벤트의 항목 전체 삭제
     */
    int deleteByEventSeq(@Param("eventSeq") Integer eventSeq);
}
