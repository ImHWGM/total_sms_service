package kr.wisead.domain.payment.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.wisead.domain.payment.dto.BillingStatsSearchRequest;
import kr.wisead.domain.payment.dto.BillingSummaryResponse;
import kr.wisead.domain.payment.dto.DailyBillingStatsResponse;
import kr.wisead.domain.payment.dto.MonthlyBillingStatsResponse;
import kr.wisead.domain.payment.dto.ServiceTypeBillingStatsResponse;
import kr.wisead.domain.payment.dto.TransactionResponse;
import kr.wisead.domain.payment.dto.UserBillingStatsResponse;
import kr.wisead.domain.payment.dto.WalletSummaryResponse;
import kr.wisead.domain.payment.entity.Transaction;
import kr.wisead.mapper.primary.TransactionMapper;
import kr.wisead.mapper.primary.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 과금 통계 서비스 (리팩토링 버전) - TransactionMapper 사용 - WalletService 연동 - 외부 API 호환: userId(String) → 내부:
 * userSeq(Integer) 변환
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingStatisticsService {

  private final TransactionMapper transactionMapper;
  private final WalletService walletService;
  private final UserMapper userMapper;

  /** 일별 과금 통계 조회 */
  @Transactional(readOnly = true)
  public List<DailyBillingStatsResponse> getDailyBillingStats(BillingStatsSearchRequest request) {
    String[] dates = resolveDefaultDates(request.getStartDate(), request.getEndDate());
    Integer userSeq = toUserSeq(request.getUserId());

    List<Map<String, Object>> rawStats =
        transactionMapper.selectDailyStats(userSeq, dates[0], dates[1]);

    return rawStats.stream().map(this::toDailyBillingStatsResponse).toList();
  }

  /** 월별 과금 통계 조회 */
  @Transactional(readOnly = true)
  public List<MonthlyBillingStatsResponse> getMonthlyBillingStats(
      BillingStatsSearchRequest request) {
    String[] dates = resolveDefaultDates(request.getStartDate(), request.getEndDate());
    Integer userSeq = toUserSeq(request.getUserId());

    List<Map<String, Object>> rawStats =
        transactionMapper.selectMonthlyStats(userSeq, dates[0], dates[1]);

    return rawStats.stream().map(this::toMonthlyBillingStatsResponse).toList();
  }

  /** 서비스 타입별 과금 통계 조회 */
  @Transactional(readOnly = true)
  public List<ServiceTypeBillingStatsResponse> getBillingStatsByServiceType(
      BillingStatsSearchRequest request) {
    String[] dates = resolveDefaultDates(request.getStartDate(), request.getEndDate());
    Integer userSeq = toUserSeq(request.getUserId());
    List<Integer> userSeqs = toUserSeqs(request.getUserIds());

    List<Map<String, Object>> rawStats =
        transactionMapper.selectStatsByServiceId(userSeq, userSeqs, dates[0], dates[1]);

    return rawStats.stream().map(this::toServiceTypeBillingStatsResponse).toList();
  }

  /** 사용자별 과금 통계 조회 */
  @Transactional(readOnly = true)
  public List<UserBillingStatsResponse> getBillingStatsByUser(BillingStatsSearchRequest request) {
    List<String> userIds = request.getUserIds();
    if (userIds == null || userIds.isEmpty()) {
      return Collections.emptyList();
    }

    // userId → userSeq 변환 및 매핑 유지
    Map<Integer, String> seqToUserIdMap = new LinkedHashMap<>();
    for (String userId : userIds) {
      Integer userSeq = toUserSeq(userId);
      if (userSeq != null) {
        seqToUserIdMap.put(userSeq, userId);
      }
    }
    List<Integer> userSeqs = new ArrayList<>(seqToUserIdMap.keySet());

    if (userSeqs.isEmpty()) {
      return Collections.emptyList();
    }

    List<Map<String, Object>> rawStats =
        transactionMapper.selectStatsByUsers(
            userSeqs, request.getStartDate(), request.getEndDate());

    // 사용자별로 그룹핑 (userSeq → userId 변환하여 응답)
    Map<String, UserBillingStatsResponse> userStatsMap = new LinkedHashMap<>();

    for (Map<String, Object> stat : rawStats) {
      Integer userSeq = getIntValue(stat, "userSeq");
      String userId = seqToUserIdMap.get(userSeq);
      if (userId == null) continue;

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
      List<Map<String, Object>> balances = transactionMapper.selectUserBalanceSummary(userSeqs);
      for (Map<String, Object> balance : balances) {
        Integer userSeq = getIntValue(balance, "userSeq");
        String userId = seqToUserIdMap.get(userSeq);
        if (userId == null) continue;

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
      for (Map.Entry<Integer, String> entry : seqToUserIdMap.entrySet()) {
        String userId = entry.getValue();
        Integer userSeq = entry.getKey();
        UserBillingStatsResponse userStats = userStatsMap.get(userId);
        if (userStats != null && userStats.getCurrentBalance() == null) {
          try {
            WalletSummaryResponse summary = walletService.getWalletSummary(userSeq);
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
    String[] dates = resolveDefaultDates(request.getStartDate(), request.getEndDate());
    Integer userSeq = toUserSeq(request.getUserId());
    List<Integer> userSeqs = toUserSeqs(request.getUserIds());

    Map<String, Object> rawSummary =
        transactionMapper.selectSummary(userSeq, userSeqs, dates[0], dates[1]);

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
        .toList();
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
      Integer userSeq = toUserSeq(userId);
      WalletSummaryResponse summary = walletService.getWalletSummary(userSeq);
      LocalDateTime lastTxDate = transactionMapper.selectLastTransactionDate(userSeq);

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
    Integer userSeq = toUserSeq(userId);
    return walletService.getWalletSummary(userSeq);
  }

  // ==================== Private Methods ====================

  /** userId → userSeq 변환 */
  private Integer toUserSeq(String userId) {
    if (userId == null || userId.isBlank()) {
      return null;
    }
    return userMapper.findSeqByUserId(userId);
  }

  /** userIds → userSeqs 변환 */
  private List<Integer> toUserSeqs(List<String> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return Collections.emptyList();
    }
    return userIds.stream().map(userMapper::findSeqByUserId).filter(seq -> seq != null).toList();
  }

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
