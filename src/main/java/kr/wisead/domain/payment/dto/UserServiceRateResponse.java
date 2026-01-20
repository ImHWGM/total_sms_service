package kr.wisead.domain.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 사용자별 서비스 요금 응답 DTO
 */
public record UserServiceRateResponse(
        List<ServiceRateInfo> rates
) {
    /**
     * 개별 서비스 요금 정보
     */
    public record ServiceRateInfo(
            String serviceId,
            String serviceName,
            BigDecimal standardRate,
            BigDecimal userRate,
            LocalDate startDate,
            LocalDate endDate,
            boolean hasCustomRate
    ) {}

    public static UserServiceRateResponse of(List<ServiceRateInfo> rates) {
        return new UserServiceRateResponse(rates);
    }
}
