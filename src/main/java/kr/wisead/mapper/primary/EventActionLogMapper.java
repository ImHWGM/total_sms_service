package kr.wisead.mapper.primary;

import kr.wisead.domain.event.entity.EventActionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 행사 액션 로그 Mapper (Primary DB)
 */
@Mapper
public interface EventActionLogMapper {

    /**
     * 시퀀스로 조회
     */
    Optional<EventActionLog> selectBySeq(@Param("seq") Long seq);

    /**
     * 참가자의 액션 로그 목록 조회
     */
    List<EventActionLog> selectByParticipantSeq(@Param("participantSeq") Long participantSeq);

    /**
     * 참가자의 특정 액션 로그 조회
     */
    List<EventActionLog> selectByParticipantSeqAndActionTypeSeq(@Param("participantSeq") Long participantSeq,
                                                                  @Param("actionTypeSeq") Long actionTypeSeq);

    /**
     * 참가자가 특정 액션을 수행했는지 확인
     */
    boolean existsByParticipantSeqAndActionTypeSeq(@Param("participantSeq") Long participantSeq,
                                                    @Param("actionTypeSeq") Long actionTypeSeq);

    /**
     * 이벤트의 전체 액션 로그 조회 (페이징)
     */
    List<EventActionLog> selectByEventSeqWithPaging(Map<String, Object> params);

    /**
     * 이벤트의 전체 액션 로그 수
     */
    int countByEventSeq(@Param("eventSeq") Integer eventSeq);

    /**
     * 이벤트의 특정 액션 로그 조회
     */
    List<EventActionLog> selectByEventSeqAndActionCode(Map<String, Object> params);

    /**
     * 이벤트의 특정 액션 수행 인원 수
     */
    int countByEventSeqAndActionTypeSeq(@Param("eventSeq") Integer eventSeq,
                                         @Param("actionTypeSeq") Long actionTypeSeq);

    /**
     * 액션 로그 등록
     */
    int insert(EventActionLog actionLog);

    /**
     * 액션 로그 삭제
     */
    int delete(@Param("seq") Long seq);

    /**
     * 참가자의 모든 액션 로그 삭제
     */
    int deleteByParticipantSeq(@Param("participantSeq") Long participantSeq);

    /**
     * 이벤트의 모든 액션 로그 삭제
     */
    int deleteByEventSeq(@Param("eventSeq") Integer eventSeq);

    /**
     * 참가자의 액션 현황 조회 (액션 유형별 완료 여부)
     */
    List<Map<String, Object>> selectActionStatusByParticipantSeq(@Param("participantSeq") Long participantSeq,
                                                                   @Param("eventSeq") Integer eventSeq);

    /**
     * 이벤트 액션별 통계 조회
     */
    List<Map<String, Object>> selectActionStatsByEventSeq(@Param("eventSeq") Integer eventSeq);
}
