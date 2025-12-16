package kr.wisead.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 월별 과금 통계 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyBillingStatsResponse {

    private String statMonth;           // 통계 월 (yyyy-MM)
    private String operation;           // 작업 유형
    private String operationName;       // 작업 유형명
    private int transactionCount;       // 거래 건수
    private BigDecimal totalAmount;     // 총 금액
}
