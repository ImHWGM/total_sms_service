package kr.wisead.domain.payment.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 잔액 Entity
 * TB_BALANCE 테이블 매핑
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Balance {

    private Long seq;                       // 시퀀스
    private String userId;                  // 사용자 ID
    private BigDecimal balance;             // 변동 금액
    private BigDecimal totalBalance;        // 총 잔액
    private String operation;               // 작업 유형 (P: 충전, M: 차감, R: 환불)
    private String comment;                 // 비고
    private BigDecimal subtractUnitPrice;   // 설문조사 건당 단가
    private BigDecimal smsPrice;            // SMS 건당 단가
    private BigDecimal lmsPrice;            // LMS 건당 단가
    private BigDecimal mmsPrice;            // MMS 건당 단가
    private String refund;                  // 환불 여부
    private LocalDateTime regDate;          // 등록일시
    private String regId;                   // 등록자 ID
}
