package kr.wisead.mapper.primary;

import java.time.LocalDateTime;
import java.util.List;
import kr.wisead.domain.profanity.entity.ProfanityBlockLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 금칙어 차단 로그 Mapper */
@Mapper
public interface ProfanityBlockLogMapper {

  /** 차단 로그 등록 */
  void insert(ProfanityBlockLog log);

  /**
   * 조건 검색 (페이징)
   *
   * @param userId 사용자 SEQ (null 시 전체)
   * @param source 발생 경로 DRAFT/SEND/SCHEDULE (null 시 전체)
   * @param from 조회 시작 일시 (null 시 제한 없음)
   * @param to 조회 종료 일시 (null 시 제한 없음)
   * @param offset 페이징 offset
   * @param limit 페이징 limit
   */
  List<ProfanityBlockLog> selectByFilter(
      @Param("userId") Integer userId,
      @Param("source") String source,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      @Param("offset") int offset,
      @Param("limit") int limit);

  /** 조건 검색 총 건수 */
  long countByFilter(
      @Param("userId") Integer userId,
      @Param("source") String source,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);
}
