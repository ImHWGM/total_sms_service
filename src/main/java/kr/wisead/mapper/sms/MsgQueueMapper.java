package kr.wisead.mapper.sms;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import kr.wisead.domain.message.entity.MsgQueue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 메시지 발송 큐 Mapper (SMS DB - msg_queue) */
@Mapper
public interface MsgQueueMapper {

  /** 발송 큐 조회 (by MSEQ) */
  Optional<MsgQueue> findByMseq(@Param("mseq") Integer mseq);

  /** userKey(배치ID)로 조회 - 일반 문자 발송 */
  List<MsgQueue> findByUserKey(@Param("userKey") String userKey);

  /** 등록자별 대기 중인 발송 목록 조회 */
  List<MsgQueue> findPendingByRegId(@Param("regId") String regId);

  /** 설문별 대기 중인 발송 목록 조회 */
  List<MsgQueue> findPendingByEventSeq(@Param("eventSeq") Integer eventSeq);

  /** 예약 발송 대기 목록 조회 (특정 시간 이전) */
  List<MsgQueue> findPendingBeforeTime(@Param("requestTime") LocalDateTime requestTime);

  // ========== 설문 발송용 ==========

  /** 설문 문자 발송 큐 등록 EXT_COL0=eventSeq, EXT_COL1=userSeq, EXT_COL2=발송타입, EXT_COL3=regId */
  int insertForSurvey(MsgQueue msgQueue);

  // ========== 일반 문자 발송용 ==========

  /** SMS 발송 큐 등록 EXT_COL0=NULL, EXT_COL1=userKey, EXT_COL2=발송타입, EXT_COL3=regId */
  int insertSms(MsgQueue msgQueue);

  /** LMS 발송 큐 등록 */
  int insertLms(MsgQueue msgQueue);

  /** MMS 발송 큐 등록 */
  int insertMms(MsgQueue msgQueue);

  // ========== 공통 ==========

  /** 발송 상태 업데이트 */
  int updateStat(@Param("mseq") Integer mseq, @Param("stat") String stat);

  /** 발송 큐 삭제 (대기 상태만) */
  int delete(@Param("mseq") Integer mseq);

  /** userKey(배치) 전체 삭제 */
  int deleteByUserKey(@Param("userKey") String userKey);

  /** 등록자별 대기 건수 조회 */
  long countPendingByRegId(@Param("regId") String regId);

  /** 등록자별 대기 중인 발송 목록 조회 (페이징) */
  List<MsgQueue> findPendingByRegIdPaging(
      @Param("regId") String regId, @Param("offset") int offset, @Param("limit") int limit);

  /** 행사별 대기 중인 발송 건수 (발송 전/송신중/처리중) */
  int countPendingByEventSeq(@Param("eventSeq") Integer eventSeq);
}
