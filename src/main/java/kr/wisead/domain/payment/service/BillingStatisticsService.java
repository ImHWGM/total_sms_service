package kr.wisead.domain.payment.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import kr.wisead.domain.payment.dto.*;
import kr.wisead.domain.payment.entity.Transaction;
import kr.wisead.mapper.primary.TransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 과금 통계 서비스 (리팩토링 버전) - TransactionMapper 사용 - WalletService 연동 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingStatisticsService {

  private final TransactionMapper transactionMapper;
  private final WalletService walletService;

  /** 일별 과금 통계 조회 */
  @Transactional(readOnly = true)
  public List<DailyBillingStatsResponse> getDailyBillingStats(BillingStatsSearchRequest request) {
    String[] dates = resolveDefaultDates(request.getStartDate(), request.getEndDate());

    List<Map<String, Object>> rawStats =
        transactionMapper.selectDailyStats(request.getUserId(), dates[0], dates[1]);

    return rawStats.stream().map(this::toDailyBillingStatsResponse).collect(Collectors.toList());
  }

  /** 월별 과금 통계 조회 */
  @Transactional(readOnly = true)
  public List<MonthlyBillingStatsResponse> getMonthlyBillingStats(
      BillingStatsSearchRequest request) {
    String[] dates = resolveDefaultDates(request.getStartDate(), request.getEndDate());

    List<Map<String, Object>> rawStats =
        transactionMapper.selectMonthlyStats(request.getUserId(), dates[0], dates[1]);

    return rawStats.stream().map(this::toMonthlyBillingStatsResponse).collect(Collectors.toList());
  }

  /** 서비스 타입별 과금 통계 조회 */
  @Transactional(readOnly = true)
  public List<ServiceTypeBillingStatsResponse> getBillingStatsByServiceType(
      BillingStatsSearchRequest request) {
    String[] dates = resolveDefaultDates(request.getStartDate(), request.getEndDate());

    List<Map<String, Object>> rawStats =
        transactionMapper.selectStatsByServiceId(request.getUserId(), dates[0], dates[1]);

    return rawStats.stream()
        .map(this::toServiceTypeBillingStatsResponse)
        .collect(Collectors.toList());
  }

  /** 사용자별 과금 통계 조회 */
  @Transactional(readOnly = true)
  public List<UserBillingStatsResponse> getBillingStatsByUser(BillingStatsSearchRequest request) {
    List<String> userIds = request.getUserIds();
    if (userIds == null || userIds.isEmpty()) {
      return Collections.emptyList();
    }

    List<Map<String, Object>> rawStats =
        transactionMapper.selectStatsByUsers(userIds, request.getStartDate(), request.getEndDate());

    // 사용자별로 그룹핑
    Map<String, UserBillingStatsResponse> userStatsMap = new LinkedHashMap<>();

    for (Map<String, Object> stat : rawStats) {
      String userId = (String) stat.get("userId");
      String txType = (String) stat.get("txType");
      int transactionCount = getIntValue(stat, "transactionCount");
      BigDecimal totalAmount = getBigDecimalValue(stat, "totalAmount");

      UserBillingStatsResponse userStats =
          userStatsMap.computeIfAbsent(
              userId,
              k ->
                  UserBillingStatsResponse.builder()
                      .userId(k)
                      .totalCharge(BigDecimal.ZERO)
                      .totalDeduct(BigDecimal.ZERO)
                      .totalRefund(BigDecimal.ZERO)
                      .build());

      switch (txType) {
        case "CHARGE" -> {
          userStats.setTotalCharge(userStats.getTotalCharge().add(totalAmount));
          userStats.setChargeCount(userStats.getChargeCount() + transactionCount);
        }
        case "DEDUCT" -> {
          userStats.setTotalDeduct(userStats.getTotalDeduct().add(totalAmount));
          userStats.setDeductCount(userStats.getDeductCount() + transactionCount);
        }
        case "REFUND" -> {
          userStats.setTotalRefund(userStats.getTotalRefund().add(totalAmount));
          userStats.setRefundCount(userStats.getRefundCount() + transactionCount);
        }
      }
    }

    // 현재 잔액 정보 추가
    if (!userStatsMap.isEmpty()) {
      List<Map<String, Object>> balances = transactionMapper.selectUserBalanceSummary(userIds);
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

      // 잔액 정보가 없는 사용자 처리 (새 wallet 기반)
      for (String userId : userIds) {
        UserBillingStatsResponse userStats = userStatsMap.get(userId);
        if (userStats != null && userStats.getCurrentBalance() == null) {
          try {
            WalletSummaryResponse summary = walletService.getWalletSummary(userId);
            userStats.setCurrentBalance(summary.getTotal());
          } catch (Exception e) {
            userStats.setCurrentBalance(BigDecimal.ZERO);
          }
        }
      }
    }

    return new ArrayList<>(userStatsMap.values());
  }

  /** 과금 총계 조회 */
  @Transactional(readOnly = true)
  public BillingSummaryResponse getBillingSummary(BillingStatsSearchRequest request) {
    Map<String, Object> rawSummary =
        transactionMapper.selectSummary(
            request.getUserId(), request.getStartDate(), request.getEndDate());

    BigDecimal totalCharge = getBigDecimalValue(rawSummary, "totalCharge");
    BigDecimal totalDeduct = getBigDecimalValue(rawSummary, "totalDeduct");
    BigDecimal totalRefund = getBigDecimalValue(rawSummary, "totalRefund");
    int chargeCount = getIntValue(rawSummary, "chargeCount");
    int deductCount = getIntValue(rawSummary, "deductCount");
    int refundCount = getIntValue(rawSummary, "refundCount");

    BillingSummaryResponse response =
        BillingSummaryResponse.builder()
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

  /** 최근 거래 내역 조회 */
  @Transactional(readOnly = true)
  public List<Transaction> getRecentTransactions(String txType, int limit) {
    return transactionMapper.selectRecent(txType, limit);
  }

  /** 최근 거래 내역 조회 (Response 변환) */
  @Transactional(readOnly = true)
  public List<TransactionResponse> getRecentTransactionsAsResponse(String txType, int limit) {
    return transactionMapper.selectRecent(txType, limit).stream()
        .map(TransactionResponse::from)
        .collect(Collectors.toList());
  }

  /** 특정 사용자의 과금 요약 조회 */
  @Transactional(readOnly = true)
  public UserBillingStatsResponse getUserBillingSummary(
      String userId, String startDate, String endDate) {
    BillingStatsSearchRequest request =
        BillingStatsSearchRequest.builder()
            .userIds(List.of(userId))
            .startDate(startDate)
            .endDate(endDate)
            .build();

    List<UserBillingStatsResponse> stats = getBillingStatsByUser(request);
    if (stats.isEmpty()) {
      // 해당 기간 거래가 없어도 현재 잔액은 조회
      WalletSummaryResponse summary = walletService.getWalletSummary(userId);
      LocalDateTime lastTxDate = transactionMapper.selectLastTransactionDate(userId);

      return UserBillingStatsResponse.builder()
          .userId(userId)
          .currentBalance(summary.getTotal())
          .lastTransactionDate(lastTxDate)
          .totalCharge(BigDecimal.ZERO)
          .totalDeduct(BigDecimal.ZERO)
          .totalRefund(BigDecimal.ZERO)
          .build();
    }
    return stats.get(0);
  }

  /** 현재 월 과금 통계 조회 (대시보드용) */
  @Transactional(readOnly = true)
  public BillingSummaryResponse getCurrentMonthSummary(String userId) {
    LocalDate now = LocalDate.now();
    String startDate = now.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE);
    String endDate = now.format(DateTimeFormatter.ISO_DATE);

    return getBillingSummary(
        BillingStatsSearchRequest.builder()
            .userId(userId)
            .startDate(startDate)
            .endDate(endDate)
            .build());
  }

  /** 이전 월 과금 통계 조회 */
  @Transactional(readOnly = true)
  public BillingSummaryResponse getPreviousMonthSummary(String userId) {
    LocalDate now = LocalDate.now();
    LocalDate previousMonth = now.minusMonths(1);
    String startDate = previousMonth.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE);
    String endDate =
        previousMonth
            .withDayOfMonth(previousMonth.lengthOfMonth())
            .format(DateTimeFormatter.ISO_DATE);

    return getBillingSummary(
        BillingStatsSearchRequest.builder()
            .userId(userId)
            .startDate(startDate)
            .endDate(endDate)
            .build());
  }

  /** 일별 과금 추이 조회 (최근 N일) */
  @Transactional(readOnly = true)
  public List<DailyBillingStatsResponse> getDailyTrend(String userId, int days) {
    LocalDate endDate = LocalDate.now();
    LocalDate startDate = endDate.minusDays(days - 1);

    return getDailyBillingStats(
        BillingStatsSearchRequest.builder()
            .userId(userId)
            .startDate(startDate.format(DateTimeFormatter.ISO_DATE))
            .endDate(endDate.format(DateTimeFormatter.ISO_DATE))
            .build());
  }

  /** 월별 과금 추이 조회 (최근 N개월) */
  @Transactional(readOnly = true)
  public List<MonthlyBillingStatsResponse> getMonthlyTrend(String userId, int months) {
    LocalDate endDate = LocalDate.now();
    LocalDate startDate = endDate.minusMonths(months - 1).withDayOfMonth(1);

    return getMonthlyBillingStats(
        BillingStatsSearchRequest.builder()
            .userId(userId)
            .startDate(startDate.format(DateTimeFormatter.ISO_DATE))
            .endDate(endDate.format(DateTimeFormatter.ISO_DATE))
            .build());
  }

  /** 통화 유형별 잔액 조회 (신규) */
  @Transactional(readOnly = true)
  public WalletSummaryResponse getWalletSummary(String userId) {
    return walletService.getWalletSummary(userId);
  }

  // ==================== Private Methods ====================

  /** 날짜 기본값 처리 (null이면 현재 월 1일 ~ 오늘) */
  private String[] resolveDefaultDates(String startDate, String endDate) {
    if (startDate == null || startDate.isBlank() || endDate == null || endDate.isBlank()) {
      LocalDate now = LocalDate.now();
      return new String[] {
        now.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE),
        now.format(DateTimeFormatter.ISO_DATE)
      };
    }
    return new String[] {startDate, endDate};
  }

  private DailyBillingStatsResponse toDailyBillingStatsResponse(Map<String, Object> raw) {
    String txType = (String) raw.get("txType");
    Object statDateObj = raw.get("statDate");
    String statDate = statDateObj != null ? statDateObj.toString() : null;

    // txType을 operation으로 변환
    String operation = convertTxTypeToOperation(txType);

    return DailyBillingStatsResponse.builder()
        .statDate(statDate)
        .operation(operation)
        .operationName(DailyBillingStatsResponse.getOperationName(operation))
        .transactionCount(getIntValue(raw, "transactionCount"))
        .totalAmount(getBigDecimalValue(raw, "totalAmount"))
        .build();
  }

  private MonthlyBillingStatsResponse toMonthlyBillingStatsResponse(Map<String, Object> raw) {
    String txType = (String) raw.get("txType");
    String operation = convertTxTypeToOperation(txType);

    return MonthlyBillingStatsResponse.builder()
        .statMonth((String) raw.get("statMonth"))
        .operation(operation)
        .operationName(DailyBillingStatsResponse.getOperationName(operation))
        .transactionCount(getIntValue(raw, "transactionCount"))
        .totalAmount(getBigDecimalValue(raw, "totalAmount"))
        .build();
  }

  private ServiceTypeBillingStatsResponse toServiceTypeBillingStatsResponse(
      Map<String, Object> raw) {
    String serviceId = (String) raw.get("serviceId");

    return ServiceTypeBillingStatsResponse.builder()
        .serviceType(serviceId)
        .serviceTypeName(ServiceTypeBillingStatsResponse.getServiceTypeName(serviceId))
        .transactionCount(getIntValue(raw, "transactionCount"))
        .totalAmount(getBigDecimalValue(raw, "totalAmount"))
        .build();
  }

  private String convertTxTypeToOperation(String txType) {
    if (txType == null) return "E";
    return switch (txType) {
      case "CHARGE" -> "P";
      case "DEDUCT" -> "M";
      case "REFUND" -> "R";
      default -> "E";
    };
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
