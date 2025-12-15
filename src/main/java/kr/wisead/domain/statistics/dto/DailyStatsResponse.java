package kr.wisead.domain.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 일별 통계 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyStatsResponse {

    private String dtStats;         // 통계 일자 (YYYY-MM-DD)
    private String serviceType;     // 서비스 타입
    private int inCnt;              // 접수 건수
    private int succCnt;            // 성공 건수
    private int errorCnt;           // 에러 건수
    private int failCnt;            // 실패 건수
    private int ingCnt;             // 진행중 건수
    private int waitCnt;            // 대기 건수

    /**
     * 총 건수
     */
    public int getTotalCnt() {
        return inCnt;
    }

    /**
     * 성공률 (%)
     */
    public double getSuccessRate() {
        if (inCnt == 0) return 0.0;
        return Math.round((double) succCnt / inCnt * 10000) / 100.0;
    }

    /**
     * 실패률 (%)
     */
    public double getFailRate() {
        if (inCnt == 0) return 0.0;
        return Math.round((double) (errorCnt + failCnt) / inCnt * 10000) / 100.0;
    }
}
