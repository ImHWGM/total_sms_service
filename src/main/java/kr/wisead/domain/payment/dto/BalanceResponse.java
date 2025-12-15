package kr.wisead.domain.payment.dto;

import kr.wisead.domain.payment.entity.Balance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 잔액 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceResponse {

    private Long seq;
    private String userId;
    private BigDecimal balance;
    private BigDecimal totalBalance;
    private String operation;
    private String operationName;           // 작업유형명
    private String comment;
    private BigDecimal subtractUnitPrice;
    private BigDecimal smsPrice;
    private BigDecimal lmsPrice;
    private BigDecimal mmsPrice;
    private LocalDateTime regDate;

    public static BalanceResponse from(Balance entity) {
        String opName = switch (entity.getOperation()) {
            case "P" -> "충전";
            case "M" -> "차감";
            case "R" -> "환불";
            default -> entity.getOperation();
        };

        return BalanceResponse.builder()
                .seq(entity.getSeq())
                .userId(entity.getUserId())
                .balance(entity.getBalance())
                .totalBalance(entity.getTotalBalance())
                .operation(entity.getOperation())
                .operationName(opName)
                .comment(entity.getComment())
                .subtractUnitPrice(entity.getSubtractUnitPrice())
                .smsPrice(entity.getSmsPrice())
                .lmsPrice(entity.getLmsPrice())
                .mmsPrice(entity.getMmsPrice())
                .regDate(entity.getRegDate())
                .build();
    }
}
