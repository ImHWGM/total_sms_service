package kr.wisead.mapper.primary;

import java.time.LocalDateTime;
import java.util.List;
import kr.wisead.domain.profanity.entity.ProhibitedWordHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 금칙어 변경 이력 Mapper */
@Mapper
public interface ProhibitedWordHistoryMapper {

  /** 이력 등록 */
  void insert(ProhibitedWordHistory history);

  /**
   * 조건 검색 (페이징)
   *
   * @param wordId 대상 금칙어 SEQ (null 시 전체)
   * @param fromDate 조회 시작 일시 (null 시 제한 없음)
   * @param toDate 조회 종료 일시 (null 시 제한 없음)
   * @param offset 페이징 offset
   * @param limit 페이징 limit
   */
  List<ProhibitedWordHistory> selectByFilter(
      @Param("wordId") Long wordId,
      @Param("fromDate") LocalDateTime fromDate,
      @Param("toDate") LocalDateTime toDate,
      @Param("offset") int offset,
      @Param("limit") int limit);

  /** 조건 검색 총 건수 */
  long countByFilter(
      @Param("wordId") Long wordId,
      @Param("fromDate") LocalDateTime fromDate,
      @Param("toDate") LocalDateTime toDate);
}
