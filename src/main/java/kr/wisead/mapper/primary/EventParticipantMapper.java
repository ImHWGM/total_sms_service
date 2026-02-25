package kr.wisead.mapper.primary;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.wisead.domain.event.entity.EventParticipant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 행사 참가자 Mapper (Primary DB) */
@Mapper
public interface EventParticipantMapper {

  /** 시퀀스로 조회 */
  Optional<EventParticipant> selectBySeq(@Param("seq") Long seq);

  /** 체크코드로 조회 */
  Optional<EventParticipant> selectByCheckCode(@Param("checkCode") String checkCode);

  /** SURVEY_USER 시퀀스로 조회 */
  Optional<EventParticipant> selectBySurveyUserSeq(@Param("surveyUserSeq") Integer surveyUserSeq);

  /** 이벤트의 참가자 목록 조회 */
  List<EventParticipant> selectByEventSeq(@Param("eventSeq") Integer eventSeq);

  /** 이벤트의 참가자 목록 조회 (페이징) */
  List<EventParticipant> selectByEventSeqWithPaging(Map<String, Object> params);

  /** 이벤트의 참가자 수 조회 */
  int countByEventSeq(@Param("eventSeq") Integer eventSeq);

  /** 참가자 검색 (이름, 소속, 연락처 등) */
  List<EventParticipant> searchParticipants(Map<String, Object> params);

  /** 참가자 검색 수 */
  int countSearchParticipants(Map<String, Object> params);

  /** 참가자 등록 */
  int insert(EventParticipant participant);

  /** 참가자 일괄 등록 */
  int insertBatch(List<EventParticipant> participants);

  /** 참가자 수정 */
  int update(EventParticipant participant);

  /** 명찰 출력 여부 변경 */
  int updateNametagPrinted(@Param("seq") Long seq, @Param("nametagPrinted") String nametagPrinted);

  /** 참가자 삭제 */
  int delete(@Param("seq") Long seq);

  /** 이벤트의 모든 참가자 삭제 */
  int deleteByEventSeq(@Param("eventSeq") Integer eventSeq);

  /** 체크코드 존재 여부 확인 */
  boolean existsByCheckCode(@Param("checkCode") String checkCode);

  /** 참가자 상세 조회 (SURVEY_USER, SURVEY_MASTER JOIN) */
  Optional<EventParticipant> selectDetailBySeq(@Param("seq") Long seq);

  /** 참가자 상세 조회 by 체크코드 (SURVEY_USER, SURVEY_MASTER JOIN) */
  Optional<EventParticipant> selectDetailByCheckCode(@Param("checkCode") String checkCode);

  /** 이벤트의 참가자 유형별 통계 */
  List<Map<String, Object>> selectParticipantTypeStats(@Param("eventSeq") Integer eventSeq);

  /** 이벤트의 명찰 출력 통계 */
  Map<String, Object> selectNametagStats(@Param("eventSeq") Integer eventSeq);

  /** 이벤트의 체크인 통계 (CHECK_IN 액션 기준) */
  Map<String, Object> selectCheckInStats(@Param("eventSeq") Integer eventSeq);

  /** 이벤트의 등록구분별 통계 (registType 기준) */
  Map<String, Object> selectRegistTypeStats(@Param("eventSeq") Integer eventSeq);

  /** 이벤트 참가자 목록 (엑셀용 - 전체 데이터) */
  List<EventParticipant> selectAllForExcel(@Param("eventSeq") Integer eventSeq);

  /** 이벤트 + 이름으로 참가자 조회 (인증용 1차 필터링) */
  List<EventParticipant> selectByEventSeqAndUserName(
      @Param("eventSeq") Integer eventSeq, @Param("userName") String userName);

  /** 이벤트 + 이름 + 암호화된 전화번호로 중복 체크 */
  boolean existsByEventSeqAndNameAndPhone(
      @Param("eventSeq") Integer eventSeq,
      @Param("userName") String userName,
      @Param("encryptedPhone") String encryptedPhone);

  /** 이벤트 + 암호화된 전화번호로 참여자 조회 (문자 발송용 자동등록) */
  Optional<EventParticipant> selectByEventSeqAndPhone(
      @Param("eventSeq") Integer eventSeq, @Param("encryptedPhone") String encryptedPhone);

  /** 참석시간 업데이트 (체크인 시 사용) */
  int updateAttendTime(@Param("seq") Long seq, @Param("attendTime") String attendTime);
}
