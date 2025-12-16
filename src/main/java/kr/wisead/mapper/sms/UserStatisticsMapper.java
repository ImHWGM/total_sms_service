package kr.wisead.mapper.sms;

import kr.wisead.domain.statistics.dto.UserMsgStatsResponse;
import kr.wisead.domain.statistics.dto.UserSurveyStatsResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 사용자별 통계 Mapper (SMS DB)
 */
@Mapper
public interface UserStatisticsMapper {

    /**
     * 사용자별 메시지 통계 조회 (일반 메시지)
     * - ext_col0 IS NULL 또는 '' : 일반 메시지
     */
    List<UserMsgStatsResponse> selectMsgStats(
            @Param("tables") List<String> tables,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("userId") String userId,
            @Param("userIds") List<String> userIds
    );

    /**
     * 사용자별 설문 통계 조회
     * - ext_col0 IS NOT NULL AND ext_col0 != '' : 설문 메시지
     */
    List<UserSurveyStatsResponse> selectSurveyStats(
            @Param("tables") List<String> tables,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("userId") String userId,
            @Param("userIds") List<String> userIds
    );
}
