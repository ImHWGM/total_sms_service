package kr.wisead.domain.payment.dto;

import kr.wisead.domain.payment.entity.ChargeBonusEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 충전 보너스 이벤트 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargeBonusEventResponse {

    private Long eventSeq;
    private String eventName;
    private String eventType;           // PERCENTAGE, FIXED
    private String eventTypeDisplay;    // 비율, 고정금액

    private BigDecimal bonusRate;       // 보너스 비율
    private String bonusRateDisplay;    // "10%" 형태
    private BigDecimal bonusAmount;     // 고정 보너스 금액

    private BigDecimal minChargeAmount; // 최소 충전 금액
    private BigDecimal maxBonusAmount;  // 최대 보너스 한도

    private LocalDate startDate;
    private LocalDate endDate;
    private Integer bonusExpireDays;

    private String status;
    private String statusDisplay;       // 활성, 비활성

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Entity → Response 변환
     */
    public static ChargeBonusEventResponse from(ChargeBonusEvent entity) {
        if (entity == null) {
            return null;
        }

        return ChargeBonusEventResponse.builder()
                .eventSeq(entity.getEventSeq())
                .eventName(entity.getEventName())
                .eventType(entity.getEventType())
                .eventTypeDisplay(convertEventTypeDisplay(entity.getEventType()))
                .bonusRate(entity.getBonusRate())
                .bonusRateDisplay(convertBonusRateDisplay(entity.getBonusRate()))
                .bonusAmount(entity.getBonusAmount())
                .minChargeAmount(entity.getMinChargeAmount())
                .maxBonusAmount(entity.getMaxBonusAmount())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .bonusExpireDays(entity.getBonusExpireDays())
                .status(entity.getStatus())
                .statusDisplay(convertStatusDisplay(entity.getStatus()))
                .createdBy(entity.getCreatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private static String convertEventTypeDisplay(String eventType) {
        if (eventType == null) return "";
        return switch (eventType) {
            case "PERCENTAGE" -> "비율";
            case "FIXED" -> "고정금액";
            default -> eventType;
        };
    }

    private static String convertBonusRateDisplay(BigDecimal bonusRate) {
        if (bonusRate == null) return "";
        // 0.10 → "10%"
        return bonusRate.multiply(BigDecimal.valueOf(100))
                .stripTrailingZeros()
                .toPlainString() + "%";
    }

    private static String convertStatusDisplay(String status) {
        if (status == null) return "";
        return switch (status) {
            case "ACTIVE" -> "활성";
            case "INACTIVE" -> "비활성";
            default -> status;
        };
    }
}
