package kr.wisead.mapper.sms;

import java.time.LocalDateTime;
import java.util.List;
import kr.wisead.domain.message.entity.MsgResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 메시지 발송 이력 Mapper (SMS DB - msg_result_YYYYMM) 월별 테이블을 동적으로 조회 */
@Mapper
public interface MsgResultMapper {

  /**
   * 발송 이력 조회 (msg_queue + msg_result_YYYYMM UNION)
   *
   * @param regId 등록자 ID (EXT_COL3)
   * @param tables 조회할 월별 테이블명 목록 (예: ["msg_result_202511", "msg_result_202512"])
   * @param srhDateStart 검색 시작일
   * @param srhDateEnd 검색 종료일
   * @param type 검색 타입 (A: 메시지타입, B: 수신번호, C: 발신번호, D: 결과, E: 제목, F: 내용, G: 상태, H: 등록자)
   * @param keyword 검색 키워드
   * @param sendFailure 실패만 조회 여부 ("1": 실패만)
   * @param skip 스킵 건수
   * @param amount 조회 건수
   */
  List<MsgResult> selectSendHistory(
      @Param("regId") String regId,
      @Param("tables") List<String> tables,
      @Param("srhDateStart") LocalDateTime srhDateStart,
      @Param("srhDateEnd") LocalDateTime srhDateEnd,
      @Param("type") String type,
      @Param("keyword") String keyword,
      @Param("sendFailure") String sendFailure,
      @Param("skip") int skip,
      @Param("amount") int amount);

  /** 발송 이력 총 건수 조회 */
  int countSendHistory(
      @Param("regId") String regId,
      @Param("tables") List<String> tables,
      @Param("srhDateStart") LocalDateTime srhDateStart,
      @Param("srhDateEnd") LocalDateTime srhDateEnd,
      @Param("type") String type,
      @Param("keyword") String keyword,
      @Param("sendFailure") String sendFailure);

  /** 특정 테이블에서 단건 조회 */
  MsgResult findByMseq(@Param("tableName") String tableName, @Param("mseq") Integer mseq);

  /**
   * 이벤트와 사용자 시퀀스로 이전 발송 내용 조회 (재발송용) msg_queue + msg_result_yyyyMM 테이블에서 최신 발송 정보 조회 EXT_COL0 =
   * eventSeq, EXT_COL1 = userSeq
   */
  MsgResult selectPreviousSend(
      @Param("tables") List<String> tables,
      @Param("eventSeq") Integer eventSeq,
      @Param("userSeq") Integer userSeq);

  // ========== 발송 실패 환불 배치용 ==========

  /** REFUND_YN 컬럼 존재 여부 확인 */
  int checkRefundColumnExists(@Param("tableName") String tableName);

  /** REFUND_YN 컬럼 추가 */
  void addRefundColumn(@Param("tableName") String tableName);

  /**
   * 환불되지 않은 발송 실패 건 조회 - RESULT != '0' (실패) - REFUND_YN = 'N' (미환불) - EXT_COL2 IS NOT NULL
   * (txGroupId 존재) - STAT = 3 (결과 수신 완료)
   */
  List<MsgResult> selectUnrefundedFailures(
      @Param("tableName") String tableName, @Param("limit") int limit);

  /** 환불 처리 완료 후 REFUND_YN 업데이트 */
  void updateRefundStatus(
      @Param("tableName") String tableName,
      @Param("mseq") Integer mseq,
      @Param("refundYn") String refundYn);
}
