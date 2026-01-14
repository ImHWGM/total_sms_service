package kr.wisead.domain.payment.entity;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 지갑 Entity (CASH 전용)
 * wallet 테이블 매핑
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Wallet {

    private String userId;              // 사용자 ID (PK)
    private String currencyType;        // 통화 유형 (PK) - CASH
    private BigDecimal balance;         // 현재 잔액
    private LocalDateTime uptDate;      // 수정일시

    /**
     * 잔액 증가 (충전/환불)
     */
    public void addBalance(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    /**
     * 잔액 차감
     */
    public void subtractBalance(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalStateException("잔액이 부족합니다.");
        }
        this.balance = this.balance.subtract(amount);
    }

    /**
     * 잔액 충분 여부 확인
     */
    public boolean hasEnoughBalance(BigDecimal amount) {
        return this.balance.compareTo(amount) >= 0;
    }

    /**
     * 신규 지갑 생성
     */
    public static Wallet createCashWallet(String userId) {
        return Wallet.builder()
                .userId(userId)
                .currencyType("CASH")
                .balance(BigDecimal.ZERO)
                .build();
    }

    /**
     * 초기 잔액으로 지갑 생성
     */
    public static Wallet createCashWallet(String userId, BigDecimal initialBalance) {
        return Wallet.builder()
                .userId(userId)
                .currencyType("CASH")
                .balance(initialBalance)
                .build();
    }
}