package kr.wisead.mapper.sms;

import kr.wisead.domain.statistics.dto.DailyStatsResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 기간별 통계 Mapper (SMS DB)
 */
@Mapper
public interface PeriodStatisticsMapper {

    /**
     * 일별 통계 조회 (msg_result_YYYYMM 테이블)
     */
    DailyStatsResponse countOneDayStats(
            @Param("tableName") String tableName,
            @Param("msgType") String msgType,
            @Param("serviceType") String serviceType,
            @Param("date") String date,
            @Param("userId") String userId,
            @Param("userIds") List<String> userIds
    );

    /**
     * 대기 건수 조회 (msg_queue 테이블 - 예약 발송)
     */
    int countWaitStats(
            @Param("date") String date,
            @Param("msgType") String msgType,
            @Param("serviceType") String serviceType,
            @Param("userId") String userId,
            @Param("userIds") List<String> userIds
    );

    /**
     * 진행중 건수 조회 (msg_queue 테이블 - 발송 진행중)
     */
    int countInProgressStats(
            @Param("date") String date,
            @Param("msgType") String msgType,
            @Param("serviceType") String serviceType,
            @Param("userId") String userId,
            @Param("userIds") List<String> userIds
    );
}
