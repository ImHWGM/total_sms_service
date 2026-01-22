package kr.wisead.domain.payment.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/** 지갑 요약 응답 DTO */
@Getter
@Builder
public class WalletSummaryResponse {

  private Integer userSeq;
  private BigDecimal cash; // 캐시 잔액
  private BigDecimal point; // 포인트 잔액 (유효한 것만)
  private BigDecimal bonus; // 보너스 잔액 (유효한 것만)
  private BigDecimal total; // 총 잔액

  /** 총 잔액 계산 */
  public static WalletSummaryResponse of(
      Integer userSeq, BigDecimal cash, BigDecimal point, BigDecimal bonus) {
    BigDecimal safeCash = cash != null ? cash : BigDecimal.ZERO;
    BigDecimal safePoint = point != null ? point : BigDecimal.ZERO;
    BigDecimal safeBonus = bonus != null ? bonus : BigDecimal.ZERO;

    return WalletSummaryResponse.builder()
        .userSeq(userSeq)
        .cash(safeCash)
        .point(safePoint)
        .bonus(safeBonus)
        .total(safeCash.add(safePoint).add(safeBonus))
        .build();
  }

  /** 잔액 충분 여부 확인 */
  public boolean hasEnoughBalance(BigDecimal amount) {
    return total.compareTo(amount) >= 0;
  }
}
