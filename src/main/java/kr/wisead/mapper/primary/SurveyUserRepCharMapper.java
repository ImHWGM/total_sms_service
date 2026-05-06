package kr.wisead.mapper.primary;

import java.util.List;
import kr.wisead.domain.survey.entity.SurveyUserRepChar;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 설문 참여자별 치환문자 Mapper (Primary DB) */
@Mapper
public interface SurveyUserRepCharMapper {

  /** INSERT IGNORE 일괄 등록 — first-write-wins. */
  int insertIgnoreBatch(@Param("rows") List<SurveyUserRepChar> rows);

  /** userSeq로 치환문자 목록 조회 (idx 오름차순). */
  List<SurveyUserRepChar> selectByUserSeq(@Param("userSeq") Integer userSeq);
}
