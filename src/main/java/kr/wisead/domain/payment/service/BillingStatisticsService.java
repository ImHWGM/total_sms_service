package kr.wisead.domain.payment.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.payment.dto.BillingStatsSearchRequest;
import kr.wisead.domain.payment.dto.BillingSummaryResponse;
import kr.wisead.domain.payment.dto.DailyBillingStatsResponse;
import kr.wisead.domain.payment.dto.MonthlyBillingStatsResponse;
import kr.wisead.domain.payment.dto.ServiceTypeBillingStatsResponse;
import kr.wisead.domain.payment.dto.TransactionResponse;
import kr.wisead.domain.payment.dto.UserBillingStatsResponse;
import kr.wisead.domain.payment.dto.WalletSummaryResponse;
import kr.wisead.domain.payment.entity.Transaction;
import kr.wisead.domain.statistics.dto.StatsSearchRequest;
import kr.wisead.domain.statistics.dto.UserMsgStatsResponse;
import kr.wisead.domain.statistics.dto.UserQrStatsResponse;
import kr.wisead.domain.statistics.dto.UserSurveyStatsResponse;
import kr.wisead.domain.statistics.service.UserStatisticsService;
import kr.wisead.mapper.primary.TransactionMapper;
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
  private final UserIdResolver userIdResolver;
  private final UserStatisticsService userStatisticsService;
  private final StandardRateService standardRateService;
  private final UserServiceRateService userServiceRateService;

  /** QR코드 추가과금 단위 (방문횟수 기준) */
  private static final int QR_VISITS_PER_BLOCK = 3000;

  /** 일별 과금 통계 조회 */
  @Transactional(readOnly = true)
  public List<DailyBillingStatsResponse> getDailyBillingStats(BillingStatsSearchRequest request) {
    String[] dates = resolveDefaultDates(request.getStartDate(), request.getEndDate());
    Integer userSeq = userIdResolver.toUserSeq(request.getUserId());

    List<Map<String, Object>> rawStats =
        transactionMapper.selectDailyStats(userSeq, dates[0], dates[1]);

    return rawStats.stream().map(this::toDailyBillingStatsResponse).toList();
  }

  /** 월별 과금 통계 조회 */
  @Transactional(readOnly = true)
  public List<MonthlyBillingStatsResponse> getMonthlyBillingStats(
      BillingStatsSearchRequest request) {
    String[] dates = resolveDefaultDates(request.getStartDate(), request.getEndDate());
    Integer userSeq = userIdResolver.toUserSeq(request.getUserId());

    List<Map<String, Object>> rawStats =
        transactionMapper.selectMonthlyStats(userSeq, dates[0], dates[1]);

    return rawStats.stream().map(this::toMonthlyBillingStatsResponse).toList();
  }

  private static final String[] ALL_SERVICE_IDS =
      {"msg_sms", "msg_lms", "msg_mms", "survey", "qr_code", "qr_code_extra"};

  /** 서비스 타입별 과금 통계 조회 (SMS DB 기반) - 실제 발송 성공 건수 기준 - 유저별 단가 적용하여 사용료 계산 */
  @Transactional(readOnly = true)
  public List<ServiceTypeBillingStatsResponse> getBillingStatsByServiceType(
      BillingStatsSearchRequest request) {
    String[] dates = resolveDefaultDates(request.getStartDate(), request.getEndDate());

    // SMS DB에서 실제 발송 통계 조회
    List<UserMsgStatsResponse> msgStats =
        userStatisticsService.findMsgStats(buildStatsRequest(dates, request, "M"));
    List<UserSurveyStatsResponse> surveyStats =
        userStatisticsService.findSurveyStats(buildStatsRequest(dates, request, "S"));
    List<UserQrStatsResponse> qrStats =
        userStatisticsService.findQrStats(buildStatsRequest(dates, request, "Q"));

    // 유저별 단가 캐시 구축 (N+1 방지)
    Map<String, Map<String, BigDecimal>> rateCache =
        buildRateCache(msgStats, surveyStats, qrStats);

    // 유저별 성공건수 × 유저별 단가로 사용료 합산
    Map<String, Integer> countMap = new LinkedHashMap<>();
    Map<String, BigDecimal> amountMap = new LinkedHashMap<>();

    for (UserMsgStatsResponse stat : msgStats) {
      Map<String, BigDecimal> rates = rateCache.getOrDefault(stat.getUserId(), Map.of());
      countMap.merge("SMS", stat.getSmsSucc(), Integer::sum);
      countMap.merge("LMS", stat.getLmsSucc(), Integer::sum);
      countMap.merge("MMS", stat.getMmsSucc(), Integer::sum);
      amountMap.merge(
          "SMS",
          rates.getOrDefault("msg_sms", BigDecimal.ZERO)
              .multiply(BigDecimal.valueOf(stat.getSmsSucc())),
          BigDecimal::add);
      amountMap.merge(
          "LMS",
          rates.getOrDefault("msg_lms", BigDecimal.ZERO)
              .multiply(BigDecimal.valueOf(stat.getLmsSucc())),
          BigDecimal::add);
      amountMap.merge(
          "MMS",
          rates.getOrDefault("msg_mms", BigDecimal.ZERO)
              .multiply(BigDecimal.valueOf(stat.getMmsSucc())),
          BigDecimal::add);
    }

    for (UserSurveyStatsResponse stat : surveyStats) {
      Map<String, BigDecimal> rates = rateCache.getOrDefault(stat.getUserId(), Map.of());
      countMap.merge("설문", stat.getSurveySucc(), Integer::sum);
      amountMap.merge(
          "설문",
          rates.getOrDefault("survey", BigDecimal.ZERO)
              .multiply(BigDecimal.valueOf(stat.getSurveySucc())),
          BigDecimal::add);
    }

    // QR 통계를 userId별로 합산 후 추가과금 계산
    Map<String, int[]> qrByUser = new LinkedHashMap<>();
    for (UserQrStatsResponse qr : qrStats) {
      qrByUser.merge(
          qr.getUserId(),
          new int[] {qr.getEventCount(), qr.getVisitCount()},
          (a, b) -> new int[] {a[0] + b[0], a[1] + b[1]});
    }
    for (Map.Entry<String, int[]> entry : qrByUser.entrySet()) {
      Map<String, BigDecimal> rates = rateCache.getOrDefault(entry.getKey(), Map.of());
      int eventCount = entry.getValue()[0];
      int visitCount = entry.getValue()[1];
      int extraBlocks =
          visitCount <= 0
              ? 0
              : ((visitCount + QR_VISITS_PER_BLOCK - 1) / QR_VISITS_PER_BLOCK - 1);
      countMap.merge("QR코드", eventCount, Integer::sum);
      countMap.merge("QR코드 추가과금", Math.max(extraBlocks, 0), Integer::sum);
      amountMap.merge(
          "QR코드",
          rates.getOrDefault("qr_code", BigDecimal.ZERO)
              .multiply(BigDecimal.valueOf(eventCount)),
          BigDecimal::add);
      amountMap.merge(
          "QR코드 추가과금",
          rates.getOrDefault("qr_code_extra", BigDecimal.ZERO)
              .multiply(BigDecimal.valueOf(Math.max(extraBlocks, 0))),
          BigDecimal::add);
    }

    // 표준 단가 조회
    Map<String, BigDecimal> stdRateMap = new LinkedHashMap<>();
    stdRateMap.put("SMS", standardRateService.getStandardRateWithVat("msg_sms"));
    stdRateMap.put("LMS", standardRateService.getStandardRateWithVat("msg_lms"));
    stdRateMap.put("MMS", standardRateService.getStandardRateWithVat("msg_mms"));
    stdRateMap.put("설문", standardRateService.getStandardRateWithVat("survey"));
    stdRateMap.put("QR코드", standardRateService.getStandardRateWithVat("qr_code"));
    stdRateMap.put("QR코드 추가과금", standardRateService.getStandardRateWithVat("qr_code_extra"));

    // 응답 생성
    String[][] categories = {
      {"SMS", ""}, {"LMS", ""}, {"MMS", ""},
      {"설문", ""},
      {"QR코드", "기본조회 3,000건, 이후 3,000건당 추가과금"},
      {"QR코드 추가과금", ""},
    };
    List<ServiceTypeBillingStatsResponse> result = new ArrayList<>();
    for (String[] cat : categories) {
      result.add(
          ServiceTypeBillingStatsResponse.builder()
              .serviceType(cat[0])
              .serviceTypeName(cat[0])
              .transactionCount(countMap.getOrDefault(cat[0], 0))
              .unitPrice(stdRateMap.getOrDefault(cat[0], BigDecimal.ZERO))
              .totalAmount(amountMap.getOrDefault(cat[0], BigDecimal.ZERO))
              .remarks(cat[1])
              .build());
    }
    return result;
  }

  /** StatsSearchRequest 빌더 헬퍼 */
  private StatsSearchRequest buildStatsRequest(
      String[] dates, BillingStatsSearchRequest request, String serviceType) {
    return StatsSearchRequest.builder()
        .startDate(dates[0])
        .endDate(dates[1])
        .serviceType(serviceType)
        .userId(request.getUserId())
        .userIds(request.getUserIds())
        .build();
  }

  /** 유저별 서비스 단가 캐시 구축 (N+1 방지) */
  private Map<String, Map<String, BigDecimal>> buildRateCache(
      List<UserMsgStatsResponse> msgStats,
      List<UserSurveyStatsResponse> surveyStats,
      List<UserQrStatsResponse> qrStats) {
    Set<String> allUserIds = new LinkedHashSet<>();
    for (UserMsgStatsResponse s : msgStats) allUserIds.add(s.getUserId());
    for (UserSurveyStatsResponse s : surveyStats) allUserIds.add(s.getUserId());
    for (UserQrStatsResponse s : qrStats) allUserIds.add(s.getUserId());

    Map<String, Map<String, BigDecimal>> cache = new LinkedHashMap<>();
    for (String uid : allUserIds) {
      Integer userSeq = userIdResolver.toUserSeq(uid);
      Map<String, BigDecimal> rates = new LinkedHashMap<>();
      for (String sid : ALL_SERVICE_IDS) {
        rates.put(
            sid,
            userSeq != null
                ? userServiceRateService.getEffectiveRate(userSeq, sid)
                : standardRateService.getStandardRateWithVat(sid));
      }
      cache.put(uid, rates);
    }
    return cache;
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
      Integer userSeq = userIdResolver.toUserSeq(userId);
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
    Integer userSeq = userIdResolver.toUserSeq(request.getUserId());
    List<Integer> userSeqs = userIdResolver.toUserSeqs(request.getUserIds());

    Map<String, Object> rawSummary =
        transactionMapper.selectSummary(userSeq, userSeqs, dates[0], dates[1]);

    BigDecimal totalCharge = getBigDecimalValue(rawSummary, "totalCharge");
    BigDecimal totalDeduct = getBigDecimalValue(rawSummary, "totalDeduct");
    BigDecimal totalRefund = getBigDecimalValue(rawSummary, "totalRefund");
    int chargeCount = getIntValue(rawSummary, "chargeCount");
    int deductCount = getIntValue(rawSummary, "deductCount");
    int refundCount = getIntValue(rawSummary, "refundCount");

    // currency_type별 차감 금액
    BigDecimal deductCash = getBigDecimalValue(rawSummary, "deductCash");
    BigDecimal deductPoint = getBigDecimalValue(rawSummary, "deductPoint");
    BigDecimal deductBonus = getBigDecimalValue(rawSummary, "deductBonus");

    BillingSummaryResponse response =
        BillingSummaryResponse.builder()
            .userId(request.getUserId())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .totalCharge(totalCharge)
            .chargeCount(chargeCount)
            .totalDeduct(totalDeduct)
            .deductCount(deductCount)
            .deductCash(deductCash)
            .deductPoint(deductPoint)
            .deductBonus(deductBonus)
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
      Integer userSeq = userIdResolver.toUserSeq(userId);
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
    Integer userSeq = userIdResolver.toUserSeq(userId);
    return walletService.getWalletSummary(userSeq);
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
