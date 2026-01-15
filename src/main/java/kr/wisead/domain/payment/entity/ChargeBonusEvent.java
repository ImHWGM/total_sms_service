package kr.wisead.domain.payment.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 충전 보너스 이벤트 엔티티
 * - 결제 시 일정 비율의 포인트를 보너스로 적립하는 이벤트 설정
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargeBonusEvent {

    private Long eventSeq;              // 이벤트 순번
    private String eventName;           // 이벤트명 (예: "10% 포인트 추가 적립")
    private String eventType;           // 이벤트 유형: PERCENTAGE, FIXED
    private BigDecimal bonusRate;       // 보너스 비율 (PERCENTAGE: 0.10 = 10%)
    private BigDecimal bonusAmount;     // 고정 보너스 금액 (FIXED 타입용)
    private BigDecimal minChargeAmount; // 최소 충전 금액 (이상 충전 시 적용)
    private BigDecimal maxBonusAmount;  // 최대 보너스 한도
    private LocalDate startDate;        // 이벤트 시작일
    private LocalDate endDate;          // 이벤트 종료일
    private Integer bonusExpireDays;    // 보너스 포인트 유효기간 (일)
    private String status;              // 상태: ACTIVE, INACTIVE
    private String createdBy;           // 생성자
    private LocalDateTime createdAt;    // 생성일시
    private LocalDateTime updatedAt;    // 수정일시

    // 이벤트 유형 상수
    public static final String TYPE_PERCENTAGE = "PERCENTAGE";  // 비율 기반 (충전금액 × bonusRate)
    public static final String TYPE_FIXED = "FIXED";            // 고정 금액

    // 상태 상수
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    /**
     * 보너스 금액 계산
     */
    public BigDecimal calculateBonus(BigDecimal chargeAmount) {
        if (chargeAmount == null || chargeAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        // 최소 충전 금액 미달 시 보너스 없음
        if (minChargeAmount != null && chargeAmount.compareTo(minChargeAmount) < 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal bonus;
        if (TYPE_PERCENTAGE.equals(eventType)) {
            // 비율 기반: 충전금액 × 보너스율
            bonus = chargeAmount.multiply(bonusRate).setScale(0, java.math.RoundingMode.DOWN);
        } else if (TYPE_FIXED.equals(eventType)) {
            // 고정 금액
            bonus = bonusAmount != null ? bonusAmount : BigDecimal.ZERO;
        } else {
            return BigDecimal.ZERO;
        }

        // 최대 보너스 한도 적용
        if (maxBonusAmount != null && bonus.compareTo(maxBonusAmount) > 0) {
            bonus = maxBonusAmount;
        }

        return bonus;
    }

    /**
     * 이벤트 활성 여부 확인
     */
    public boolean isActive(LocalDate today) {
        if (!STATUS_ACTIVE.equals(status)) {
            return false;
        }
        if (startDate != null && today.isBefore(startDate)) {
            return false;
        }
        if (endDate != null && today.isAfter(endDate)) {
            return false;
        }
        return true;
    }

    /**
     * 보너스 포인트 만료일 계산
     */
    public LocalDate calculateExpireDate(LocalDate today) {
        int days = bonusExpireDays != null ? bonusExpireDays : 90; // 기본 90일
        return today.plusDays(days);
    }
}
