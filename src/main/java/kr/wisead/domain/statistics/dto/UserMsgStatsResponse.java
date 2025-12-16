package kr.wisead.domain.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 사용자별 메시지 통계 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMsgStatsResponse {

    /**
     * 사용자 ID
     */
    private String userId;

    /**
     * 사용자명
     */
    private String userName;

    /**
     * SMS 총 건수
     */
    private int smsTotal;

    /**
     * SMS 성공 건수
     */
    private int smsSucc;

    /**
     * LMS 총 건수
     */
    private int lmsTotal;

    /**
     * LMS 성공 건수
     */
    private int lmsSucc;

    /**
     * MMS 총 건수
     */
    private int mmsTotal;

    /**
     * MMS 성공 건수
     */
    private int mmsSucc;

    /**
     * 전체 총 건수
     */
    public int getTotalCount() {
        return smsTotal + lmsTotal + mmsTotal;
    }

    /**
     * 전체 성공 건수
     */
    public int getTotalSucc() {
        return smsSucc + lmsSucc + mmsSucc;
    }

    /**
     * 성공률 (%)
     */
    public double getSuccessRate() {
        int total = getTotalCount();
        if (total == 0) return 0.0;
        return Math.round((getTotalSucc() * 100.0 / total) * 100) / 100.0;
    }
}
