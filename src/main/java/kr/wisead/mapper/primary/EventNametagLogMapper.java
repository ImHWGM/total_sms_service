package kr.wisead.mapper.primary;

import kr.wisead.domain.event.entity.EventNametagLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 명찰 출력 이력 Mapper (Primary DB)
 */
@Mapper
public interface EventNametagLogMapper {

    /**
     * 시퀀스로 조회
     */
    Optional<EventNametagLog> selectBySeq(@Param("seq") Long seq);

    /**
     * 참가자의 명찰 출력 이력 조회
     */
    List<EventNametagLog> selectByParticipantSeq(@Param("participantSeq") Long participantSeq);

    /**
     * 이벤트의 명찰 출력 이력 조회 (페이징)
     */
    List<EventNametagLog> selectByEventSeqWithPaging(Map<String, Object> params);

    /**
     * 이벤트의 명찰 출력 건수
     */
    int countByEventSeq(@Param("eventSeq") Integer eventSeq);

    /**
     * 참가자의 명찰 출력 여부 확인
     */
    boolean existsByParticipantSeq(@Param("participantSeq") Long participantSeq);

    /**
     * 명찰 출력 로그 등록
     */
    int insert(EventNametagLog nametagLog);

    /**
     * 명찰 출력 로그 삭제
     */
    int delete(@Param("seq") Long seq);

    /**
     * 참가자의 모든 명찰 출력 로그 삭제
     */
    int deleteByParticipantSeq(@Param("participantSeq") Long participantSeq);

    /**
     * 이벤트의 모든 명찰 출력 로그 삭제
     */
    int deleteByEventSeq(@Param("eventSeq") Integer eventSeq);

    /**
     * 이벤트의 명찰 출력 통계 (템플릿별)
     */
    List<Map<String, Object>> selectPrintStatsByEventSeq(@Param("eventSeq") Integer eventSeq);
}
