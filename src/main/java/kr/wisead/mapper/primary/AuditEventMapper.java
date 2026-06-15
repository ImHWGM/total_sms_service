package kr.wisead.mapper.primary;

import java.time.LocalDateTime;
import java.util.List;
import kr.wisead.domain.audit.entity.AuditEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 감사 이벤트 Mapper */
@Mapper
public interface AuditEventMapper {

  /** 감사 이벤트 기록 */
  void insert(AuditEvent event);

  /**
   * 필터 조건으로 감사 이벤트 목록 조회 (페이징).
   *
   * @param eventType 이벤트 유형 (null 시 전체)
   * @param userId 대상 사용자 seq (null 시 전체)
   * @param from 조회 시작 시각 (null 시 제한 없음)
   * @param to 조회 종료 시각 (null 시 제한 없음)
   * @param offset 페이징 offset
   * @param limit 페이징 limit
   */
  List<AuditEvent> selectByFilter(
      @Param("eventType") String eventType,
      @Param("userId") Integer userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to,
      @Param("offset") int offset,
      @Param("limit") int limit);

  /**
   * 필터 조건에 맞는 감사 이벤트 총 건수 (페이징용).
   *
   * @param eventType 이벤트 유형 (null 시 전체)
   * @param userId 대상 사용자 seq (null 시 전체)
   * @param from 조회 시작 시각 (null 시 제한 없음)
   * @param to 조회 종료 시각 (null 시 제한 없음)
   */
  long countByFilter(
      @Param("eventType") String eventType,
      @Param("userId") Integer userId,
      @Param("from") LocalDateTime from,
      @Param("to") LocalDateTime to);
}
