package kr.wisead.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 문자 요금 설정 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsPriceRequest {

    /**
     * 대상 사용자 ID
     */
    private String userId;

    /**
     * SMS 단가
     */
    private BigDecimal smsPrice;

    /**
     * LMS 단가
     */
    private BigDecimal lmsPrice;

    /**
     * MMS 단가
     */
    private BigDecimal mmsPrice;
}
