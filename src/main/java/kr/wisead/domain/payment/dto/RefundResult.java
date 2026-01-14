package kr.wisead.domain.payment.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 환불 결과 DTO
 */
@Getter
@Builder
public class RefundResult {

    private String txGroupId;               // 원 결제 그룹 ID
    private String refundTxGroupId;         // 환불 거래 그룹 ID
    private BigDecimal requestedAmount;     // 환불 요청 금액
    private BigDecimal refundedAmount;      // 실제 환불 금액
    private BigDecimal expiredAmount;       // 만료로 환불 불가 금액
    private int refundedCount;              // 환불된 거래 건수
    private List<RefundDetail> details;     // 환불 상세
    private String message;                 // 결과 메시지

    /**
     * 환불 상세
     */
    @Getter
    @Builder
    public static class RefundDetail {
        private String currencyType;
        private BigDecimal amount;
        private boolean refunded;
        private String reason;              // 환불 불가 사유
    }

    /**
     * 완전 환불 여부
     */
    public boolean isFullyRefunded() {
        return expiredAmount.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * 부분 환불 여부
     */
    public boolean isPartiallyRefunded() {
        return refundedAmount.compareTo(BigDecimal.ZERO) > 0
                && expiredAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 결과 메시지 생성
     */
    public static String buildMessage(BigDecimal refunded, BigDecimal expired) {
        if (expired.compareTo(BigDecimal.ZERO) == 0) {
            return String.format("총 %s원이 환불되었습니다.", refunded.toPlainString());
        } else {
            BigDecimal total = refunded.add(expired);
            return String.format("총 %s원 중 %s원이 환불되었습니다. %s원은 유효기간 만료로 환불되지 않았습니다.",
                    total.toPlainString(), refunded.toPlainString(), expired.toPlainString());
        }
    }
}