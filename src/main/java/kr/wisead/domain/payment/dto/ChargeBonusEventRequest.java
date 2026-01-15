package kr.wisead.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 충전 보너스 이벤트 생성/수정 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargeBonusEventRequest {

    @NotBlank(message = "이벤트명은 필수입니다")
    private String eventName;

    @NotBlank(message = "이벤트 유형은 필수입니다")
    private String eventType;       // PERCENTAGE, FIXED

    private BigDecimal bonusRate;   // 보너스 비율 (0.10 = 10%)
    private BigDecimal bonusAmount; // 고정 보너스 금액

    private BigDecimal minChargeAmount; // 최소 충전 금액
    private BigDecimal maxBonusAmount;  // 최대 보너스 한도

    @NotNull(message = "시작일은 필수입니다")
    private LocalDate startDate;

    private LocalDate endDate;

    @Builder.Default
    private Integer bonusExpireDays = 90; // 기본 90일

    @Builder.Default
    private String status = "ACTIVE";
}
