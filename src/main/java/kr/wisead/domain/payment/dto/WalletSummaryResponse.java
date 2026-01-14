package kr.wisead.domain.payment.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 지갑 요약 응답 DTO
 */
@Getter
@Builder
public class WalletSummaryResponse {

    private String userId;
    private BigDecimal cash;        // 캐시 잔액
    private BigDecimal point;       // 포인트 잔액 (유효한 것만)
    private BigDecimal bonus;       // 보너스 잔액 (유효한 것만)
    private BigDecimal total;       // 총 잔액

    /**
     * 총 잔액 계산
     */
    public static WalletSummaryResponse of(String userId, BigDecimal cash, BigDecimal point, BigDecimal bonus) {
        return WalletSummaryResponse.builder()
                .userId(userId)
                .cash(cash != null ? cash : BigDecimal.ZERO)
                .point(point != null ? point : BigDecimal.ZERO)
                .bonus(bonus != null ? bonus : BigDecimal.ZERO)
                .total((cash != null ? cash : BigDecimal.ZERO)
                        .add(point != null ? point : BigDecimal.ZERO)
                        .add(bonus != null ? bonus : BigDecimal.ZERO))
                .build();
    }

    /**
     * 잔액 충분 여부 확인
     */
    public boolean hasEnoughBalance(BigDecimal amount) {
        return total.compareTo(amount) >= 0;
    }
}