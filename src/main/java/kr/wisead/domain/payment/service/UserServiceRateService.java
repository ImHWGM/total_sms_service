package kr.wisead.domain.payment.service;

import kr.wisead.domain.payment.dto.StandardRateResponse;
import kr.wisead.domain.payment.dto.UserServiceRateRequest;
import kr.wisead.domain.payment.dto.UserServiceRateResponse;
import kr.wisead.domain.payment.entity.StandardRate;
import kr.wisead.domain.payment.entity.UserServiceRate;
import kr.wisead.mapper.primary.StandardRateMapper;
import kr.wisead.mapper.primary.UserServiceRateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 사용자별 서비스 요금 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceRateService {

    private final UserServiceRateMapper userServiceRateMapper;
    private final StandardRateMapper standardRateMapper;

    private static final BigDecimal VAT_RATE = BigDecimal.valueOf(1.1);

    /**
     * 사용자별 요금 설정
     * - vatIncluded=false면 rate * 1.1 계산
     * - endDate 없으면 기존 요금 종료 후 새 요금 영구 적용
     * - endDate 있으면 기간 특별요금으로 등록 (기존 요금 유지)
     */
    @Transactional
    public void updateUserRates(UserServiceRateRequest request) {
        String userId = request.userId();
        boolean vatIncluded = request.vatIncluded();
        LocalDate today = LocalDate.now();

        for (UserServiceRateRequest.RateEntry entry : request.rates()) {
            BigDecimal rate = entry.rate();

            // 부가세 미포함이면 1.1 곱하기
            if (!vatIncluded) {
                rate = rate.multiply(VAT_RATE).setScale(1, RoundingMode.HALF_UP);
            }

            LocalDate startDate = entry.startDate() != null ? entry.startDate() : today;
            LocalDate endDate = entry.endDate();

            if (endDate == null) {
                // 영구 요금: 기존 영구 요금 종료 후 새 요금 등록
                terminateCurrentPermanentRate(userId, entry.serviceId(), startDate.minusDays(1));
            }
            // 기간 특별요금: 기존 요금 그대로 유지

            // 새 요금 등록
            UserServiceRate newRate = UserServiceRate.builder()
                    .userId(userId)
                    .serviceId(entry.serviceId())
                    .rate(rate)
                    .startDate(startDate)
                    .endDate(endDate)
                    .build();

            userServiceRateMapper.insert(newRate);
            log.info("사용자 요금 설정: userId={}, serviceId={}, rate={}, startDate={}, endDate={}",
                    userId, entry.serviceId(), rate, startDate, endDate);
        }
    }

    /**
     * 현재 영구 요금 종료 처리
     */
    private void terminateCurrentPermanentRate(String userId, String serviceId, LocalDate endDate) {
        Optional<UserServiceRate> currentRate = userServiceRateMapper.selectLatestPermanentRate(userId, serviceId);
        if (currentRate.isPresent()) {
            userServiceRateMapper.updateEndDate(currentRate.get().getSeq(), endDate);
            log.info("기존 영구 요금 종료: userId={}, serviceId={}, endDate={}",
                    userId, serviceId, endDate);
        }
    }

    /**
     * 사용자별 현재 적용 요금 조회
     * - 우선순위: 기간 특별요금 > 사용자 기본요금 > 표준요금
     */
    @Transactional(readOnly = true)
    public UserServiceRateResponse getUserRates(String userId) {
        LocalDate today = LocalDate.now();

        // 모든 표준 요금 조회
        List<StandardRate> standardRates = standardRateMapper.selectStandardRates();

        // 사용자의 모든 활성 요금 조회
        List<UserServiceRate> userRates = userServiceRateMapper.selectAllActiveRates(userId, today);
        Map<String, UserServiceRate> userRateMap = userRates.stream()
                .collect(Collectors.toMap(
                        UserServiceRate::getServiceId,
                        Function.identity(),
                        (r1, r2) -> {
                            // 기간 특별요금(endDate != null) 우선
                            if (r1.getEndDate() != null && r2.getEndDate() == null) {
                                return r1;
                            }
                            if (r2.getEndDate() != null && r1.getEndDate() == null) {
                                return r2;
                            }
                            // 둘 다 같은 타입이면 최신(startDate) 우선
                            return r1.getStartDate().isAfter(r2.getStartDate()) ? r1 : r2;
                        }
                ));

        // 응답 생성
        List<UserServiceRateResponse.ServiceRateInfo> rateInfoList = new ArrayList<>();
        for (StandardRate stdRate : standardRates) {
            BigDecimal standardRateWithVat = stdRate.getServiceRate()
                    .multiply(VAT_RATE)
                    .setScale(1, RoundingMode.HALF_UP);

            UserServiceRate userRate = userRateMap.get(stdRate.getServiceId());
            boolean hasCustomRate = userRate != null;

            UserServiceRateResponse.ServiceRateInfo info = new UserServiceRateResponse.ServiceRateInfo(
                    stdRate.getServiceId(),
                    stdRate.getServiceName(),
                    standardRateWithVat,
                    hasCustomRate ? userRate.getRate() : standardRateWithVat,
                    hasCustomRate ? userRate.getStartDate() : null,
                    hasCustomRate ? userRate.getEndDate() : null,
                    hasCustomRate
            );
            rateInfoList.add(info);
        }

        return UserServiceRateResponse.of(rateInfoList);
    }

    /**
     * 표준 요금 목록 조회
     */
    @Transactional(readOnly = true)
    public StandardRateResponse getStandardRates() {
        List<StandardRate> rates = standardRateMapper.selectStandardRates();
        return StandardRateResponse.from(rates);
    }

    /**
     * 특정 서비스의 현재 적용 요금 조회 (차감 시 사용)
     * 우선순위: 기간 특별요금 → 사용자 기본요금 → 표준요금
     */
    @Transactional(readOnly = true)
    public BigDecimal getEffectiveRate(String userId, String serviceId) {
        LocalDate today = LocalDate.now();

        // 1. 기간 특별요금 조회
        Optional<UserServiceRate> periodRate = userServiceRateMapper.selectPeriodRate(userId, serviceId, today);
        if (periodRate.isPresent()) {
            log.debug("기간 특별요금 적용: userId={}, serviceId={}, rate={}",
                    userId, serviceId, periodRate.get().getRate());
            return periodRate.get().getRate();
        }

        // 2. 사용자 기본요금(영구) 조회
        Optional<UserServiceRate> permanentRate = userServiceRateMapper.selectLatestPermanentRate(userId, serviceId);
        if (permanentRate.isPresent() && permanentRate.get().isActive(today)) {
            log.debug("사용자 기본요금 적용: userId={}, serviceId={}, rate={}",
                    userId, serviceId, permanentRate.get().getRate());
            return permanentRate.get().getRate();
        }

        // 3. 표준요금 (VAT 포함)
        BigDecimal standardRate = standardRateMapper.selectStandardRateByServiceId(serviceId);
        if (standardRate != null) {
            BigDecimal rateWithVat = standardRate.multiply(VAT_RATE).setScale(1, RoundingMode.HALF_UP);
            log.debug("표준요금 적용: userId={}, serviceId={}, rate={}",
                    userId, serviceId, rateWithVat);
            return rateWithVat;
        }

        log.warn("요금 정보 없음: userId={}, serviceId={}", userId, serviceId);
        return BigDecimal.ZERO;
    }
}
