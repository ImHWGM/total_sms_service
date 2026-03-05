package kr.wisead.mapper.sms;

import kr.wisead.domain.schedule.entity.ScheduledMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 예약 메시지 Mapper (SMS DB)
 */
@Mapper
public interface ScheduledMessageMapper {

    /**
     * 예약 메시지 목록 조회
     */
    List<ScheduledMessage> selectScheduledMessages(
            @Param("userId") String userId,
            @Param("msgType") String msgType,
            @Param("searchText") String searchText,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 예약 메시지 총 건수
     */
    int countScheduledMessages(
            @Param("userId") String userId,
            @Param("msgType") String msgType,
            @Param("searchText") String searchText);

    /**
     * 예약 메시지 상세 조회
     */
    ScheduledMessage selectMessageById(
            @Param("mSeq") int mSeq,
            @Param("userId") String userId);

    /**
     * 그룹 예약 시간 변경
     */
    int updateGroupRequestTime(
            @Param("userId") String userId,
            @Param("msgType") String msgType,
            @Param("insertTime") LocalDateTime insertTime,
            @Param("newRequestTime") LocalDateTime newRequestTime);

    /**
     * 그룹 예약 메시지 삭제
     */
    int deleteScheduledMessageGroup(
            @Param("userId") String userId,
            @Param("msgType") String msgType,
            @Param("insertTime") LocalDateTime insertTime);

    /**
     * 삭제 대상 메시지 건수 조회
     */
    int countMessagesForCancellation(
            @Param("userId") String userId,
            @Param("msgType") String msgType,
            @Param("insertTime") LocalDateTime insertTime);

    /**
     * 예약 메시지 전체 조회 (다운로드용)
     */
    List<ScheduledMessage> selectScheduledMessagesForDownload(
        @Param("userId") String userId,
        @Param("msgType") String msgType,
        @Param("searchText") String searchText);
}
