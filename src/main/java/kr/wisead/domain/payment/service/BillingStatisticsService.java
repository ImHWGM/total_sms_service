package kr.wisead.domain.payment.service;

import kr.wisead.domain.payment.dto.*;
import kr.wisead.domain.payment.entity.Balance;
import kr.wisead.mapper.primary.BalanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 과금 통계 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingStatisticsService {

    private final BalanceMapper balanceMapper;

    /**
     * 일별 과금 통계 조회
     */
    @Transactional(readOnly = true)
    public List<DailyBillingStatsResponse> getDailyBillingStats(BillingStatsSearchRequest request) {
        List<Map<String, Object>> rawStats = balanceMapper.selectDailyBillingStats(
                request.getUserId(),
                request.getStartDate(),
                request.getEndDate()
        );

        return rawStats.stream()
                .map(this::toDailyBillingStatsResponse)
                .collect(Collectors.toList());
    }

    /**
     * 월별 과금 통계 조회
     */
    @Transactional(readOnly = true)
    public List<MonthlyBillingStatsResponse> getMonthlyBillingStats(BillingStatsSearchRequest request) {
        List<Map<String, Object>> rawStats = balanceMapper.selectMonthlyBillingStats(
                request.getUserId(),
                request.getStartDate(),
                request.getEndDate()
        );

        return rawStats.stream()
                .map(this::toMonthlyBillingStatsResponse)
                .collect(Collectors.toList());
    }

    /**
     * 서비스 타입별 과금 통계 조회
     */
    @Transactional(readOnly = true)
    public List<ServiceTypeBillingStatsResponse> getBillingStatsByServiceType(BillingStatsSearchRequest request) {
        List<Map<String, Object>> rawStats = balanceMapper.selectBillingStatsByServiceType(
                request.getUserId(),
                request.getStartDate(),
                request.getEndDate()
        );

        return rawStats.stream()
                .map(this::toServiceTypeBillingStatsResponse)
                .collect(Collectors.toList());
    }

    /**
     * 사용자별 과금 통계 조회
     */
    @Transactional(readOnly = true)
    public List<UserBillingStatsResponse> getBillingStatsByUser(BillingStatsSearchRequest request) {
        List<Map<String, Object>> rawStats = balanceMapper.selectBillingStatsByUser(
                request.getUserIds(),
                request.getStartDate(),
                request.getEndDate()
        );

        // 사용자별로 그룹핑
        Map<String, UserBillingStatsResponse> userStatsMap = new LinkedHashMap<>();

        for (Map<String, Object> stat : rawStats) {
            String userId = (String) stat.get("userId");
            String operation = (String) stat.get("operation");
            int transactionCount = getIntValue(stat, "transactionCount");
            BigDecimal totalAmount = getBigDecimalValue(stat, "totalAmount");

            UserBillingStatsResponse userStats = userStatsMap.computeIfAbsent(userId,
                    k -> UserBillingStatsResponse.builder()
                            .userId(k)
                            .totalCharge(BigDecimal.ZERO)
                            .totalDeduct(BigDecimal.ZERO)
                            .totalRefund(BigDecimal.ZERO)
                            .build());

            switch (operation) {
                case "P" -> {
                    userStats.setTotalCharge(userStats.getTotalCharge().add(totalAmount));
                    userStats.setChargeCount(userStats.getChargeCount() + transactionCount);
                }
                case "M" -> {
                    userStats.setTotalDeduct(userStats.getTotalDeduct().add(totalAmount));
                    userStats.setDeductCount(userStats.getDeductCount() + transactionCount);
                }
                case "R" -> {
                    userStats.setTotalRefund(userStats.getTotalRefund().add(totalAmount));
                    userStats.setRefundCount(userStats.getRefundCount() + transactionCount);
                }
            }
        }

        // 현재 잔액 정보 추가
        List<String> userIds = new ArrayList<>(userStatsMap.keySet());
        if (!userIds.isEmpty()) {
            List<Map<String, Object>> balances = balanceMapper.selectCurrentBalanceByUsers(userIds);
            for (Map<String, Object> balance : balances) {
                String userId = (String) balance.get("userId");
                UserBillingStatsResponse userStats = userStatsMap.get(userId);
                if (userStats != null) {
                    userStats.setCurrentBalance(getBigDecimalValue(balance, "currentBalance"));
                    Object lastTxDate = balance.get("lastTransactionDate");
                    if (lastTxDate instanceof LocalDateTime) {
                        userStats.setLastTransactionDate((LocalDateTime) lastTxDate);
                    }
                }
            }
        }

        return new ArrayList<>(userStatsMap.values());
    }

    /**
     * 과금 총계 조회
     */
    @Transactional(readOnly = true)
    public BillingSummaryResponse getBillingSummary(BillingStatsSearchRequest request) {
        Map<String, Object> rawSummary = balanceMapper.selectBillingSummary(
                request.getUserId(),
                request.getStartDate(),
                request.getEndDate()
        );

        BigDecimal totalCharge = getBigDecimalValue(rawSummary, "totalCharge");
        BigDecimal totalDeduct = getBigDecimalValue(rawSummary, "totalDeduct");
        BigDecimal totalRefund = getBigDecimalValue(rawSummary, "totalRefund");
        int chargeCount = getIntValue(rawSummary, "chargeCount");
        int deductCount = getIntValue(rawSummary, "deductCount");
        int refundCount = getIntValue(rawSummary, "refundCount");

        BillingSummaryResponse response = BillingSummaryResponse.builder()
                .userId(request.getUserId())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .totalCharge(totalCharge)
                .chargeCount(chargeCount)
                .totalDeduct(totalDeduct)
                .deductCount(deductCount)
                .totalRefund(totalRefund)
                .refundCount(refundCount)
                .build();

        response.setNetAmount(response.calculateNetAmount());
        response.setTotalTransactionCount(response.calculateTotalTransactionCount());

        return response;
    }

    /**
     * 최근 거래 내역 조회
     */
    @Transactional(readOnly = true)
    public List<Balance> getRecentTransactions(String operation, int limit) {
        return balanceMapper.selectRecentTransactions(operation, limit);
    }

    /**
     * 특정 사용자의 과금 요약 조회
     */
    @Transactional(readOnly = true)
    public UserBillingStatsResponse getUserBillingSummary(String userId, String startDate, String endDate) {
        BillingStatsSearchRequest request = BillingStatsSearchRequest.builder()
                .userIds(List.of(userId))
                .startDate(startDate)
                .endDate(endDate)
                .build();

        List<UserBillingStatsResponse> stats = getBillingStatsByUser(request);
        if (stats.isEmpty()) {
            // 해당 기간 거래가 없어도 현재 잔액은 조회
            Balance latestBalance = balanceMapper.selectLatestBalance(userId);
            return UserBillingStatsResponse.builder()
                    .userId(userId)
                    .currentBalance(latestBalance != null ? latestBalance.getTotalBalance() : BigDecimal.ZERO)
                    .lastTransactionDate(latestBalance != null ? latestBalance.getRegDate() : null)
                    .totalCharge(BigDecimal.ZERO)
                    .totalDeduct(BigDecimal.ZERO)
                    .totalRefund(BigDecimal.ZERO)
                    .build();
        }
        return stats.get(0);
    }

    /**
     * 현재 월 과금 통계 조회 (대시보드용)
     */
    @Transactional(readOnly = true)
    public BillingSummaryResponse getCurrentMonthSummary(String userId) {
        LocalDate now = LocalDate.now();
        String startDate = now.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE);
        String endDate = now.format(DateTimeFormatter.ISO_DATE);

        return getBillingSummary(BillingStatsSearchRequest.builder()
                .userId(userId)
                .startDate(startDate)
                .endDate(endDate)
                .build());
    }

    /**
     * 이전 월 과금 통계 조회
     */
    @Transactional(readOnly = true)
    public BillingSummaryResponse getPreviousMonthSummary(String userId) {
        LocalDate now = LocalDate.now();
        LocalDate previousMonth = now.minusMonths(1);
        String startDate = previousMonth.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE);
        String endDate = previousMonth.withDayOfMonth(previousMonth.lengthOfMonth()).format(DateTimeFormatter.ISO_DATE);

        return getBillingSummary(BillingStatsSearchRequest.builder()
                .userId(userId)
                .startDate(startDate)
                .endDate(endDate)
                .build());
    }

    /**
     * 일별 과금 추이 조회 (최근 N일)
     */
    @Transactional(readOnly = true)
    public List<DailyBillingStatsResponse> getDailyTrend(String userId, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);

        return getDailyBillingStats(BillingStatsSearchRequest.builder()
                .userId(userId)
                .startDate(startDate.format(DateTimeFormatter.ISO_DATE))
                .endDate(endDate.format(DateTimeFormatter.ISO_DATE))
                .build());
    }

    /**
     * 월별 과금 추이 조회 (최근 N개월)
     */
    @Transactional(readOnly = true)
    public List<MonthlyBillingStatsResponse> getMonthlyTrend(String userId, int months) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(months - 1).withDayOfMonth(1);

        return getMonthlyBillingStats(BillingStatsSearchRequest.builder()
                .userId(userId)
                .startDate(startDate.format(DateTimeFormatter.ISO_DATE))
                .endDate(endDate.format(DateTimeFormatter.ISO_DATE))
                .build());
    }

    // ==================== Private Methods ====================

    private DailyBillingStatsResponse toDailyBillingStatsResponse(Map<String, Object> raw) {
        String operation = (String) raw.get("operation");
        Object statDateObj = raw.get("statDate");
        String statDate = statDateObj != null ? statDateObj.toString() : null;

        return DailyBillingStatsResponse.builder()
                .statDate(statDate)
                .operation(operation)
                .operationName(DailyBillingStatsResponse.getOperationName(operation))
                .transactionCount(getIntValue(raw, "transactionCount"))
                .totalAmount(getBigDecimalValue(raw, "totalAmount"))
                .build();
    }

    private MonthlyBillingStatsResponse toMonthlyBillingStatsResponse(Map<String, Object> raw) {
        String operation = (String) raw.get("operation");

        return MonthlyBillingStatsResponse.builder()
                .statMonth((String) raw.get("statMonth"))
                .operation(operation)
                .operationName(DailyBillingStatsResponse.getOperationName(operation))
                .transactionCount(getIntValue(raw, "transactionCount"))
                .totalAmount(getBigDecimalValue(raw, "totalAmount"))
                .build();
    }

    private ServiceTypeBillingStatsResponse toServiceTypeBillingStatsResponse(Map<String, Object> raw) {
        String serviceType = (String) raw.get("serviceType");

        return ServiceTypeBillingStatsResponse.builder()
                .serviceType(serviceType)
                .serviceTypeName(ServiceTypeBillingStatsResponse.getServiceTypeName(serviceType))
                .transactionCount(getIntValue(raw, "transactionCount"))
                .totalAmount(getBigDecimalValue(raw, "totalAmount"))
                .build();
    }

    private int getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private BigDecimal getBigDecimalValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
