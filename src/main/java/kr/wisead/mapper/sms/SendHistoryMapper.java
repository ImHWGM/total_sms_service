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

  /**
   * seq(MSEQ) 단건 조회 (단일 테이블). 원본 수신번호 조회용.
   *
   * <p>{@code userId}가 권한 범위(콤마 구분 발신 userId 목록, "ALL"이면 무제한)를 벗어난 seq는 {@code null}을
   * 반환한다. 이를 통해 타 거래처 이력의 seq 직접 조회를 차단한다. MSEQ는 전역 유일하므로 테이블당 최대 1건이다.
   */
  SendHistory selectBySeq(
      @Param("tableName") String tableName,
      @Param("seq") Long seq,
      @Param("userId") String userId);

  /** 전화번호로 최근 발송자 ID 조회 (월별 테이블) ARS 자동등록형에서 사용 */
  String selectUserIdByAni(@Param("ani") String ani, @Param("tableName") String tableName);

  /** 행사별 발송 결과 통계 (msg_result_YYYYMM 테이블) EXT_COL0 = eventSeq 기준으로 성공/실패 집계 */
  Map<String, Object> selectMessageStatsByEventSeq(
      @Param("tableName") String tableName, @Param("eventSeq") Integer eventSeq);
}
