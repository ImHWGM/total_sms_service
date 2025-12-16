package kr.wisead.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 일별 과금 통계 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyBillingStatsResponse {

    private String statDate;            // 통계 일자 (yyyy-MM-dd)
    private String operation;           // 작업 유형
    private String operationName;       // 작업 유형명
    private int transactionCount;       // 거래 건수
    private BigDecimal totalAmount;     // 총 금액

    /**
     * 작업 유형 -> 작업 유형명 변환
     */
    public static String getOperationName(String operation) {
        if (operation == null) return "기타";
        return switch (operation) {
            case "P" -> "충전";
            case "M" -> "차감";
            case "R" -> "환불";
            default -> "기타";
        };
    }
}
