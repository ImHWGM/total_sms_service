package kr.wisead.domain.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 충전 요청 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargeRequest {

  private Integer userSeq; // 사용자 시퀀스
  private BigDecimal amount; // 충전 금액
  private String comment; // 비고
  private String tradeId; // 거래 ID (PG 연동시)

  /** 충전 유형 (CASH, BONUS, POINT) - 미지정 시 기본값: CASH */
  private String currencyType;

  /** 만료일 (BONUS, POINT 충전 시 사용) - 미지정 시 기본값: 1년 후 */
  private LocalDate expireDate;

  /** 충전 유형 반환 (기본값: CASH) */
  public String getCurrencyType() {
    return currencyType == null || currencyType.isBlank() ? "CASH" : currencyType.toUpperCase();
  }

  /** 만료일 반환 (기본값: 1년 후) */
  public LocalDate getExpireDate() {
    return expireDate == null ? LocalDate.now().plusYears(1) : expireDate;
  }
}
