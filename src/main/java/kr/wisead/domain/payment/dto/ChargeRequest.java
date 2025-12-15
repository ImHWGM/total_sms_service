package kr.wisead.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 충전 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargeRequest {

    private String userId;          // 사용자 ID
    private BigDecimal amount;      // 충전 금액
    private String comment;         // 비고
    private String tradeId;         // 거래 ID (PG 연동시)
}
