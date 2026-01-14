package kr.wisead.domain.payment.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 환불 미리보기 응답 DTO
 */
@Getter
@Builder
public class RefundPreviewResponse {

    private BigDecimal totalPaid;           // 총 결제 금액
    private BigDecimal totalRefundable;     // 환불 가능 금액
    private BigDecimal totalExpired;        // 만료로 환불 불가 금액
    private boolean hasExpiredItems;        // 만료 항목 존재 여부
    private List<RefundItem> items;         // 환불 항목 상세

    /**
     * 환불 항목 상세
     */
    @Getter
    @Builder
    public static class RefundItem {
        private String currencyType;        // 통화 유형
        private BigDecimal amount;          // 금액
        private LocalDate expireDate;       // 만료일
        private boolean refundable;         // 환불 가능 여부
        private String reason;              // 환불 불가 사유 (있는 경우)
    }

    /**
     * 환불 가능한 항목만 있는지 확인
     */
    public boolean isFullyRefundable() {
        return !hasExpiredItems && totalExpired.compareTo(BigDecimal.ZERO) == 0;
    }
}