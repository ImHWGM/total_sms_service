package kr.wisead.domain.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 월별 통계 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyStatsResponse {

    private String yearMonth;           // 년월 (YYYY-MM)
    private String serviceType;         // 서비스 타입

    // 건수 통계
    private int totalCnt;               // 총 건수
    private int succCnt;                // 성공 건수
    private int failCnt;                // 실패 건수

    // 금액 통계
    private BigDecimal totalAmount;     // 총 금액
    private BigDecimal chargeAmount;    // 충전 금액
    private BigDecimal usedAmount;      // 사용 금액

    /**
     * 성공률 (%)
     */
    public double getSuccessRate() {
        if (totalCnt == 0) return 0.0;
        return Math.round((double) succCnt / totalCnt * 10000) / 100.0;
    }

    /**
     * 잔액
     */
    public BigDecimal getBalance() {
        if (chargeAmount == null || usedAmount == null) return BigDecimal.ZERO;
        return chargeAmount.subtract(usedAmount);
    }
}
