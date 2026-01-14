package kr.wisead.domain.payment.dto;

import kr.wisead.domain.payment.entity.WalletLot;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 지갑 Lot 응답 DTO
 */
@Getter
@Builder
public class WalletLotResponse {

    private Long lotSeq;
    private String userId;
    private String currencyType;
    private BigDecimal amount;
    private BigDecimal remaining;
    private LocalDate expireDate;
    private String status;
    private String source;
    private LocalDateTime regDate;
    private long daysUntilExpire;       // 만료까지 남은 일수

    /**
     * Entity -> Response 변환
     */
    public static WalletLotResponse from(WalletLot lot) {
        LocalDate today = LocalDate.now();
        long daysUntilExpire = java.time.temporal.ChronoUnit.DAYS.between(today, lot.getExpireDate());

        return WalletLotResponse.builder()
                .lotSeq(lot.getLotSeq())
                .userId(lot.getUserId())
                .currencyType(lot.getCurrencyType())
                .amount(lot.getAmount())
                .remaining(lot.getRemaining())
                .expireDate(lot.getExpireDate())
                .status(lot.getStatus())
                .source(lot.getSource())
                .regDate(lot.getRegDate())
                .daysUntilExpire(Math.max(0, daysUntilExpire))
                .build();
    }

    /**
     * 만료 임박 여부 (7일 이내)
     */
    public boolean isExpiringSoon() {
        return daysUntilExpire <= 7 && daysUntilExpire > 0;
    }
}