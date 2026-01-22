package kr.wisead.domain.payment.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 과금 총계 응답 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingSummaryResponse {

  private String userId; // 사용자 ID (개별 조회 시)
  private String startDate; // 조회 시작일
  private String endDate; // 조회 종료일

  // 충전
  private BigDecimal totalCharge; // 총 충전 금액
  private int chargeCount; // 충전 건수

  // 차감
  private BigDecimal totalDeduct; // 총 차감 금액
  private int deductCount; // 차감 건수

  // 차감 상세 (currency_type별)
  private BigDecimal deductCash; // CASH 차감 금액
  private BigDecimal deductPoint; // POINT 차감 금액
  private BigDecimal deductBonus; // BONUS 차감 금액

  // 환불
  private BigDecimal totalRefund; // 총 환불 금액
  private int refundCount; // 환불 건수

  // 계산 필드
  private BigDecimal netAmount; // 순 금액 (충전 - 차감 - 환불)
  private int totalTransactionCount; // 총 거래 건수

  /** 순 금액 계산 */
  public BigDecimal calculateNetAmount() {
    BigDecimal charge = totalCharge != null ? totalCharge : BigDecimal.ZERO;
    BigDecimal deduct = totalDeduct != null ? totalDeduct : BigDecimal.ZERO;
    BigDecimal refund = totalRefund != null ? totalRefund : BigDecimal.ZERO;
    return charge.subtract(deduct).subtract(refund);
  }

  /** 총 거래 건수 계산 */
  public int calculateTotalTransactionCount() {
    return chargeCount + deductCount + refundCount;
  }
}
