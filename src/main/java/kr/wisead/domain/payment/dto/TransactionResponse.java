package kr.wisead.domain.payment.dto;

import kr.wisead.domain.payment.entity.Transaction;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 거래 내역 응답 DTO
 */
@Getter
@Builder
public class TransactionResponse {

    private Long seq;
    private String txGroupId;
    private Integer userSeq;
    private String currencyType;
    private String txType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private String serviceId;
    private BigDecimal unitPrice;
    private BigDecimal quantity;
    private Long lotSeq;
    private LocalDate lotExpireDate;
    private String comment;
    private LocalDateTime regDate;

    /**
     * Entity -> Response 변환
     */
    public static TransactionResponse from(Transaction tx) {
        return TransactionResponse.builder()
                .seq(tx.getSeq())
                .txGroupId(tx.getTxGroupId())
                .userSeq(tx.getUserSeq())
                .currencyType(tx.getCurrencyType())
                .txType(tx.getTxType())
                .amount(tx.getAmount())
                .balanceAfter(tx.getBalanceAfter())
                .serviceId(tx.getServiceId())
                .unitPrice(tx.getUnitPrice())
                .quantity(tx.getQuantity())
                .lotSeq(tx.getLotSeq())
                .lotExpireDate(tx.getLotExpireDate())
                .comment(tx.getComment())
                .regDate(tx.getRegDate())
                .build();
    }
}