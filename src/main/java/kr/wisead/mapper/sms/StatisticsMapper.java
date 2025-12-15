package kr.wisead.mapper.sms;

import kr.wisead.domain.statistics.dto.DailyStatsResponse;
import kr.wisead.domain.statistics.dto.UsageSummaryResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 통계 Mapper (SMS DB)
 * msg_result_YYYYMM 테이블 조회
 */
@Mapper
public interface StatisticsMapper {

    /**
     * 일별 메시지 통계 조회 (단일 월)
     */
    List<DailyStatsResponse> selectDailyStats(
            @Param("tableName") String tableName,
            @Param("userId") String userId,
            @Param("serviceType") String serviceType,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

    /**
     * 사용자별 메시지 통계 조회 (단일 월)
     */
    List<Map<String, Object>> selectUserStats(
            @Param("tableName") String tableName,
            @Param("userIds") List<String> userIds,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

    /**
     * 서비스 타입별 사용량 집계 (단일 월)
     */
    List<UsageSummaryResponse> selectUsageSummary(
            @Param("tableName") String tableName,
            @Param("userId") String userId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

    /**
     * 월별 총 건수 조회
     */
    Map<String, Object> selectMonthlyTotalCount(
            @Param("tableName") String tableName,
            @Param("userId") String userId
    );
}
