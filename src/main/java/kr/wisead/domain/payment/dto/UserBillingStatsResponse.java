package kr.wisead.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 사용자별 과금 통계 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBillingStatsResponse {

    private String userId;              // 사용자 ID
    private String userName;            // 사용자명

    // 현재 잔액
    private BigDecimal currentBalance;  // 현재 잔액
    private LocalDateTime lastTransactionDate; // 최근 거래일

    // 충전
    private BigDecimal totalCharge;     // 총 충전 금액
    private int chargeCount;            // 충전 건수

    // 차감
    private BigDecimal totalDeduct;     // 총 차감 금액
    private int deductCount;            // 차감 건수

    // 환불
    private BigDecimal totalRefund;     // 총 환불 금액
    private int refundCount;            // 환불 건수

    // 계산 필드
    public BigDecimal getNetAmount() {
        BigDecimal charge = totalCharge != null ? totalCharge : BigDecimal.ZERO;
        BigDecimal deduct = totalDeduct != null ? totalDeduct : BigDecimal.ZERO;
        BigDecimal refund = totalRefund != null ? totalRefund : BigDecimal.ZERO;
        return charge.subtract(deduct).subtract(refund);
    }

    public int getTotalTransactionCount() {
        return chargeCount + deductCount + refundCount;
    }
}
