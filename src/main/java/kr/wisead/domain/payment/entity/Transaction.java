package kr.wisead.domain.payment.entity;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 거래 내역 Entity
 * transaction 테이블 매핑
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Transaction {

    private Long seq;                   // 시퀀스 (PK)
    private String txGroupId;           // 복합결제 그룹 ID
    private String userId;              // 사용자 ID
    private String currencyType;        // 통화 유형 (CASH, POINT, BONUS)
    private String txType;              // 거래 유형 (CHARGE, DEDUCT, REFUND)
    private BigDecimal amount;          // 거래 금액
    private BigDecimal balanceAfter;    // 거래 후 잔액

    // 서비스 사용 관련
    private String serviceId;           // 서비스 ID
    private BigDecimal unitPrice;       // 단가
    private BigDecimal quantity;        // 수량 (소수점 허용)

    // Lot 참조 (POINT/BONUS 사용 시)
    private Long lotSeq;                // 사용한 wallet_lot
    private LocalDate lotExpireDate;    // 해당 Lot의 만료일 (환불 검증용)

    // 환불 관련
    private Long refTxSeq;              // 환불 시 원거래 시퀀스

    private String comment;             // 비고
    private LocalDateTime regDate;      // 등록일시

    // 거래 유형 상수
    public static final String TX_TYPE_CHARGE = "CHARGE";
    public static final String TX_TYPE_DEDUCT = "DEDUCT";
    public static final String TX_TYPE_REFUND = "REFUND";

    // 통화 유형 상수
    public static final String CURRENCY_CASH = "CASH";
    public static final String CURRENCY_POINT = "POINT";
    public static final String CURRENCY_BONUS = "BONUS";

    /**
     * 충전 거래 생성
     */
    public static Transaction createCharge(String userId, BigDecimal amount, BigDecimal balanceAfter, String comment) {
        return Transaction.builder()
                .userId(userId)
                .currencyType(CURRENCY_CASH)
                .txType(TX_TYPE_CHARGE)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .comment(comment)
                .build();
    }

    /**
     * CASH 차감 거래 생성
     */
    public static Transaction createCashDeduct(String txGroupId, String userId, String serviceId,
                                                BigDecimal amount, BigDecimal unitPrice, BigDecimal quantity,
                                                BigDecimal balanceAfter, String comment) {
        return Transaction.builder()
                .txGroupId(txGroupId)
                .userId(userId)
                .currencyType(CURRENCY_CASH)
                .txType(TX_TYPE_DEDUCT)
                .amount(amount)
                .unitPrice(unitPrice)
                .quantity(quantity)
                .serviceId(serviceId)
                .balanceAfter(balanceAfter)
                .comment(comment)
                .build();
    }

    /**
     * Lot 차감 거래 생성 (POINT/BONUS)
     */
    public static Transaction createLotDeduct(String txGroupId, String userId, String currencyType,
                                               String serviceId, BigDecimal amount, BigDecimal unitPrice,
                                               BigDecimal quantity, Long lotSeq, LocalDate lotExpireDate,
                                               BigDecimal balanceAfter, String comment) {
        return Transaction.builder()
                .txGroupId(txGroupId)
                .userId(userId)
                .currencyType(currencyType)
                .txType(TX_TYPE_DEDUCT)
                .amount(amount)
                .unitPrice(unitPrice)
                .quantity(quantity)
                .lotSeq(lotSeq)
                .lotExpireDate(lotExpireDate)
                .serviceId(serviceId)
                .balanceAfter(balanceAfter)
                .comment(comment)
                .build();
    }

    /**
     * 환불 거래 생성
     */
    public static Transaction createRefund(String txGroupId, String userId, String currencyType,
                                            BigDecimal amount, BigDecimal balanceAfter,
                                            Long refTxSeq, String comment) {
        return Transaction.builder()
                .txGroupId(txGroupId)
                .userId(userId)
                .currencyType(currencyType)
                .txType(TX_TYPE_REFUND)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .refTxSeq(refTxSeq)
                .comment(comment)
                .build();
    }

    /**
     * 만료된 포인트/보너스 여부 확인
     */
    public boolean isExpiredLot(LocalDate today) {
        return lotExpireDate != null && lotExpireDate.isBefore(today);
    }

    /**
     * 환불 가능 여부 (CASH이거나 만료되지 않은 Lot)
     */
    public boolean isRefundable(LocalDate today) {
        return CURRENCY_CASH.equals(currencyType) || !isExpiredLot(today);
    }
}