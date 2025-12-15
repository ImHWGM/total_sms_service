package kr.wisead.domain.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 사용량 요약 (청구) 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageSummaryResponse {

    private String serviceTypeName;     // 서비스 타입명 (SMS, LMS, MMS, 카카오 등)
    private String category;            // 카테고리 (발송, 충전 등)
    private int count;                  // 건수
    private BigDecimal unitPrice;       // 단가
    private BigDecimal amount;          // 금액 (건수 * 단가)
    private String remarks;             // 비고

    /**
     * 금액 계산
     */
    public BigDecimal calculateAmount() {
        if (unitPrice == null) return BigDecimal.ZERO;
        return unitPrice.multiply(BigDecimal.valueOf(count));
    }

    /**
     * 포맷된 금액 문자열
     */
    public String getFormattedAmount() {
        BigDecimal amt = amount != null ? amount : calculateAmount();
        return String.format("%,d", amt.longValue());
    }
}
