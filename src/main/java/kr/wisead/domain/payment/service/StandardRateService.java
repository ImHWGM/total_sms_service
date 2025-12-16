package kr.wisead.domain.payment.service;

import kr.wisead.domain.payment.entity.StandardRate;
import kr.wisead.mapper.primary.StandardRateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 기준 단가 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StandardRateService {

    private final StandardRateMapper standardRateMapper;

    // 부가세율
    private static final BigDecimal VAT_RATE = BigDecimal.valueOf(1.1);

    /**
     * 서비스 ID로 기준 단가 조회
     * @param serviceId 서비스 ID (survey, msg_sms, msg_lms, msg_mms 등)
     * @return 기준 단가
     */
    public BigDecimal getStandardRateByServiceId(String serviceId) {
        BigDecimal rate = standardRateMapper.selectStandardRateByServiceId(serviceId);
        if (rate == null) {
            log.warn("기준 단가를 찾을 수 없습니다. serviceId: {}", serviceId);
            return BigDecimal.ZERO;
        }
        return rate;
    }

    /**
     * 서비스 ID로 VAT 포함 단가 조회
     * @param serviceId 서비스 ID
     * @return VAT 포함 단가 (정수로 변환)
     */
    public BigDecimal getStandardRateWithVat(String serviceId) {
        BigDecimal rate = getStandardRateByServiceId(serviceId);
        // VAT 적용 후 정수로 변환 (소수점 버림)
        return rate.multiply(VAT_RATE).setScale(0, RoundingMode.DOWN);
    }

    /**
     * 전체 기준 단가 목록 조회
     */
    public List<StandardRate> getStandardRates() {
        return standardRateMapper.selectStandardRates();
    }
}
