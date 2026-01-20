package kr.wisead.domain.payment.dto;

import kr.wisead.domain.payment.entity.StandardRate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 표준 요금 응답 DTO
 */
public record StandardRateResponse(
        List<StandardRateInfo> rates
) {
    private static final BigDecimal VAT_RATE = BigDecimal.valueOf(1.1);

    /**
     * 개별 표준 요금 정보
     */
    public record StandardRateInfo(
            Integer serviceSeq,
            String serviceId,
            String serviceName,
            BigDecimal rate,
            BigDecimal rateWithVat,
            String description
    ) {
        public static StandardRateInfo from(StandardRate entity) {
            BigDecimal rateWithVat = entity.getServiceRate()
                    .multiply(VAT_RATE)
                    .setScale(1, RoundingMode.HALF_UP);

            return new StandardRateInfo(
                    entity.getServiceSeq(),
                    entity.getServiceId(),
                    entity.getServiceName(),
                    entity.getServiceRate(),
                    rateWithVat,
                    entity.getServiceDescription()
            );
        }
    }

    public static StandardRateResponse from(List<StandardRate> entities) {
        List<StandardRateInfo> rates = entities.stream()
                .map(StandardRateInfo::from)
                .toList();
        return new StandardRateResponse(rates);
    }
}
