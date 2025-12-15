package kr.wisead.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 충전 내역 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargeHistoryResponse {

    private LocalDateTime chargedAt;    // 충전 일시
    private String reason;              // 사유
    private int count;                  // 건수
    private BigDecimal amount;          // 금액
    private BigDecimal balance;         // 잔액
}
