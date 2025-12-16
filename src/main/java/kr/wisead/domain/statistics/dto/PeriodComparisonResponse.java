package kr.wisead.domain.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 기간 비교 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodComparisonResponse {

    private String periodType;          // 기간 유형 (DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY)
    private String serviceType;         // 서비스 타입

    // 현재 기간
    private String currentPeriodStart;  // 현재 기간 시작일
    private String currentPeriodEnd;    // 현재 기간 종료일
    private int currentTotalCnt;        // 현재 기간 총 건수
    private int currentSuccCnt;         // 현재 기간 성공 건수
    private int currentFailCnt;         // 현재 기간 실패 건수
    private BigDecimal currentAmount;   // 현재 기간 금액

    // 이전 기간
    private String previousPeriodStart; // 이전 기간 시작일
    private String previousPeriodEnd;   // 이전 기간 종료일
    private int previousTotalCnt;       // 이전 기간 총 건수
    private int previousSuccCnt;        // 이전 기간 성공 건수
    private int previousFailCnt;        // 이전 기간 실패 건수
    private BigDecimal previousAmount;  // 이전 기간 금액

    // 변화량
    private int totalCntChange;         // 총 건수 변화
    private int succCntChange;          // 성공 건수 변화
    private int failCntChange;          // 실패 건수 변화
    private BigDecimal amountChange;    // 금액 변화

    // 변화율 (%)
    private double totalCntChangeRate;  // 총 건수 변화율
    private double succCntChangeRate;   // 성공 건수 변화율
    private double failCntChangeRate;   // 실패 건수 변화율
    private double amountChangeRate;    // 금액 변화율

    /**
     * 변화량 및 변화율 계산
     */
    public void calculateChanges() {
        // 건수 변화량
        this.totalCntChange = this.currentTotalCnt - this.previousTotalCnt;
        this.succCntChange = this.currentSuccCnt - this.previousSuccCnt;
        this.failCntChange = this.currentFailCnt - this.previousFailCnt;

        // 금액 변화량
        if (this.currentAmount != null && this.previousAmount != null) {
            this.amountChange = this.currentAmount.subtract(this.previousAmount);
        }

        // 변화율 계산
        this.totalCntChangeRate = calculateRate(this.previousTotalCnt, this.currentTotalCnt);
        this.succCntChangeRate = calculateRate(this.previousSuccCnt, this.currentSuccCnt);
        this.failCntChangeRate = calculateRate(this.previousFailCnt, this.currentFailCnt);

        if (this.previousAmount != null && this.previousAmount.compareTo(BigDecimal.ZERO) != 0 && this.currentAmount != null) {
            this.amountChangeRate = this.currentAmount.subtract(this.previousAmount)
                    .divide(this.previousAmount, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }
    }

    private double calculateRate(int previous, int current) {
        if (previous == 0) {
            return current > 0 ? 100.0 : 0.0;
        }
        return Math.round((double) (current - previous) / previous * 10000) / 100.0;
    }

    /**
     * 현재 기간 성공률
     */
    public double getCurrentSuccessRate() {
        if (currentTotalCnt == 0) return 0.0;
        return Math.round((double) currentSuccCnt / currentTotalCnt * 10000) / 100.0;
    }

    /**
     * 이전 기간 성공률
     */
    public double getPreviousSuccessRate() {
        if (previousTotalCnt == 0) return 0.0;
        return Math.round((double) previousSuccCnt / previousTotalCnt * 10000) / 100.0;
    }
}
