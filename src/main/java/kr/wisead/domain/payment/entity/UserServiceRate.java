package kr.wisead.domain.payment.entity;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 사용자별 서비스 단가 Entity
 * user_service_rate 테이블 매핑
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserServiceRate {

    private Long seq;                   // 시퀀스 (PK)
    private String userId;              // 사용자 ID
    private String serviceId;           // 서비스 ID
    private BigDecimal rate;            // 단가 (VAT 포함)
    private LocalDate startDate;        // 적용 시작일
    private LocalDate endDate;          // 적용 종료일 (null이면 현재 유효)
    private LocalDateTime regDate;      // 등록일시

    /**
     * 현재 유효한지 확인
     */
    public boolean isActive(LocalDate today) {
        boolean afterStart = !today.isBefore(startDate);
        boolean beforeEnd = endDate == null || !today.isAfter(endDate);
        return afterStart && beforeEnd;
    }

    /**
     * 신규 사용자 단가 생성
     */
    public static UserServiceRate create(String userId, String serviceId, BigDecimal rate, LocalDate startDate) {
        return UserServiceRate.builder()
                .userId(userId)
                .serviceId(serviceId)
                .rate(rate)
                .startDate(startDate)
                .build();
    }

    /**
     * 종료일 설정 (이력 관리)
     */
    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}