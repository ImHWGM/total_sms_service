package kr.wisead.mapper.sms;

import java.util.List;
import java.util.Map;
import kr.wisead.domain.history.entity.SendHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 발송 이력 Mapper (SMS DB) - msg_result_YYYYMM 테이블 조회 */
@Mapper
public interface SendHistoryMapper {

  /** 발송 이력 목록 조회 (단일 테이블) */
  List<SendHistory> selectList(
      @Param("tableName") String tableName, @Param("params") Map<String, Object> params);

  /** 발송 이력 총 건수 (단일 테이블) */
  int selectCount(
      @Param("tableName") String tableName, @Param("params") Map<String, Object> params);

  /** 발송 이력 전체 조회 (엑셀 다운로드용, 단일 테이블) */
  List<SendHistory> selectAllForDownload(
      @Param("tableName") String tableName, @Param("params") Map<String, Object> params);

  /** 전화번호로 최근 발송자 ID 조회 (월별 테이블) ARS 자동등록형에서 사용 */
  String selectUserIdByAni(@Param("ani") String ani, @Param("tableName") String tableName);

  /** 행사별 발송 결과 통계 (msg_result_YYYYMM 테이블) EXT_COL0 = eventSeq 기준으로 성공/실패 집계 */
  Map<String, Object> selectMessageStatsByEventSeq(
      @Param("tableName") String tableName, @Param("eventSeq") Integer eventSeq);
}
