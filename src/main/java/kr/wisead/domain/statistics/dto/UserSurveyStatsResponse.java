package kr.wisead.domain.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 사용자별 설문 통계 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSurveyStatsResponse {

    /**
     * 사용자 ID
     */
    private String userId;

    /**
     * 사용자명
     */
    private String userName;

    /**
     * 설문 발송 총 건수
     */
    private int surveyTotal;

    /**
     * 설문 발송 성공 건수
     */
    private int surveySucc;

    /**
     * 성공률 (%)
     */
    public double getSuccessRate() {
        if (surveyTotal == 0) return 0.0;
        return Math.round((surveySucc * 100.0 / surveyTotal) * 100) / 100.0;
    }
}
