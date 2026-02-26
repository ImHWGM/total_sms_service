package kr.wisead.domain.payment.controller;

import java.util.List;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.payment.dto.BillingStatsSearchRequest;
import kr.wisead.domain.payment.dto.BillingSummaryResponse;
import kr.wisead.domain.payment.dto.DailyBillingStatsResponse;
import kr.wisead.domain.payment.dto.MonthlyBillingStatsResponse;
import kr.wisead.domain.payment.dto.ServiceTypeBillingStatsResponse;
import kr.wisead.domain.payment.dto.TransactionResponse;
import kr.wisead.domain.payment.dto.UserBillingStatsResponse;
import kr.wisead.domain.payment.service.BillingStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/** 과금 통계 Controller */
@Slf4j
@RestController
@RequestMapping("/api/billing/statistics")
@RequiredArgsConstructor
public class BillingStatisticsController {

  private final BillingStatisticsService billingStatisticsService;
  private final AdminService adminService;
  private final UserIdResolver userIdResolver;

  /**
   * 일별 과금 통계 조회 GET
   * /api/billing/statistics/daily?userId=xxx&startDate=2024-01-01&endDate=2024-01-31
   */
  @GetMapping("/daily")
  public ApiResponse<List<DailyBillingStatsResponse>> getDailyStats(
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate) {

    BillingStatsSearchRequest request =
        BillingStatsSearchRequest.builder()
            .userId(userId)
            .startDate(startDate)
            .endDate(endDate)
            .build();

    List<DailyBillingStatsResponse> response =
        billingStatisticsService.getDailyBillingStats(request);
    return ApiResponse.success(response);
  }

  /**
   * 월별 과금 통계 조회 GET
   * /api/billing/statistics/monthly?userId=xxx&startDate=2024-01-01&endDate=2024-12-31
   */
  @GetMapping("/monthly")
  public ApiResponse<List<MonthlyBillingStatsResponse>> getMonthlyStats(
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate) {

    BillingStatsSearchRequest request =
        BillingStatsSearchRequest.builder()
            .userId(userId)
            .startDate(startDate)
            .endDate(endDate)
            .build();

    List<MonthlyBillingStatsResponse> response =
        billingStatisticsService.getMonthlyBillingStats(request);
    return ApiResponse.success(response);
  }

  /**
   * 서비스 타입별 과금 통계 조회 GET
   * /api/billing/statistics/service-type?userId=xxx&startDate=2024-01-01&endDate=2024-12-31
   *
   * <p>userId가 없으면 권한에 따라 조회 범위 결정: - 90 이상: 전체 유저 합계 - 50-89: 관리하는 계정들 합계 - 50 미만: 본인만
   */
  @GetMapping("/service-type")
  public ApiResponse<List<ServiceTypeBillingStatsResponse>> getStatsByServiceType(
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @AuthenticationPrincipal UserDetails userDetails) {

    BillingStatsSearchRequest request =
        buildRequestWithPermission(userId, startDate, endDate, userDetails);

    List<ServiceTypeBillingStatsResponse> response =
        billingStatisticsService.getBillingStatsByServiceType(request);
    return ApiResponse.success(response);
  }

  /** 사용자별 과금 통계 조회 POST /api/billing/statistics/users */
  @PostMapping("/users")
  public ApiResponse<List<UserBillingStatsResponse>> getStatsByUser(
      @RequestBody BillingStatsSearchRequest request) {

    List<UserBillingStatsResponse> response =
        billingStatisticsService.getBillingStatsByUser(request);
    return ApiResponse.success(response);
  }

  /**
   * 과금 총계 조회 GET /api/billing/statistics/summary?userId=xxx&startDate=2024-01-01&endDate=2024-12-31
   *
   * <p>userId가 없으면 권한에 따라 조회 범위 결정: - 90 이상: 전체 유저 합계 - 50-89: 관리하는 계정들 합계 - 50 미만: 본인만
   */
  @GetMapping("/summary")
  public ApiResponse<BillingSummaryResponse> getSummary(
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @AuthenticationPrincipal UserDetails userDetails) {

    BillingStatsSearchRequest request =
        buildRequestWithPermission(userId, startDate, endDate, userDetails);

    BillingSummaryResponse response = billingStatisticsService.getBillingSummary(request);
    return ApiResponse.success(response);
  }

  /** 현재 월 과금 요약 조회 (대시보드용) GET /api/billing/statistics/current-month */
  @GetMapping("/current-month")
  public ApiResponse<BillingSummaryResponse> getCurrentMonthSummary(
      @RequestParam(required = false) String userId,
      @AuthenticationPrincipal UserDetails userDetails) {

    String targetUserId = resolveUserId(userId, userDetails);
    BillingSummaryResponse response = billingStatisticsService.getCurrentMonthSummary(targetUserId);
    return ApiResponse.success(response);
  }

  /** 이전 월 과금 요약 조회 GET /api/billing/statistics/previous-month */
  @GetMapping("/previous-month")
  public ApiResponse<BillingSummaryResponse> getPreviousMonthSummary(
      @RequestParam(required = false) String userId,
      @AuthenticationPrincipal UserDetails userDetails) {

    String targetUserId = resolveUserId(userId, userDetails);
    BillingSummaryResponse response =
        billingStatisticsService.getPreviousMonthSummary(targetUserId);
    return ApiResponse.success(response);
  }

  /** 특정 사용자의 과금 요약 조회 GET /api/billing/statistics/user/{userId} */
  @GetMapping("/user/{userId}")
  public ApiResponse<UserBillingStatsResponse> getUserSummary(
      @PathVariable String userId,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate) {

    UserBillingStatsResponse response =
        billingStatisticsService.getUserBillingSummary(userId, startDate, endDate);
    return ApiResponse.success(response);
  }

  /** 내 과금 요약 조회 (로그인 사용자) GET /api/billing/statistics/my */
  @GetMapping("/my")
  public ApiResponse<UserBillingStatsResponse> getMySummary(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate) {

    String userId = extractUserId(userDetails);
    UserBillingStatsResponse response =
        billingStatisticsService.getUserBillingSummary(userId, startDate, endDate);
    return ApiResponse.success(response);
  }

  /** 일별 추이 조회 (최근 N일) GET /api/billing/statistics/trend/daily?userId=xxx&days=30 */
  @GetMapping("/trend/daily")
  public ApiResponse<List<DailyBillingStatsResponse>> getDailyTrend(
      @RequestParam(required = false) String userId, @RequestParam(defaultValue = "30") int days) {

    List<DailyBillingStatsResponse> response = billingStatisticsService.getDailyTrend(userId, days);
    return ApiResponse.success(response);
  }

  /** 월별 추이 조회 (최근 N개월) GET /api/billing/statistics/trend/monthly?userId=xxx&months=12 */
  @GetMapping("/trend/monthly")
  public ApiResponse<List<MonthlyBillingStatsResponse>> getMonthlyTrend(
      @RequestParam(required = false) String userId,
      @RequestParam(defaultValue = "12") int months) {

    List<MonthlyBillingStatsResponse> response =
        billingStatisticsService.getMonthlyTrend(userId, months);
    return ApiResponse.success(response);
  }

  /** 최근 거래 내역 조회 GET /api/billing/statistics/recent?txType=CHARGE&limit=20 */
  @GetMapping("/recent")
  public ApiResponse<List<TransactionResponse>> getRecentTransactions(
      @RequestParam(required = false) String txType, @RequestParam(defaultValue = "20") int limit) {

    List<TransactionResponse> response =
        billingStatisticsService.getRecentTransactionsAsResponse(txType, limit);
    return ApiResponse.success(response);
  }

  // ==================== Private Methods ====================

  /** userId가 지정되면 해당 값, 없으면 JWT에서 실제 userId 추출 */
  private String resolveUserId(String userId, UserDetails userDetails) {
    if (userId != null) {
      return userId;
    }
    return extractUserId(userDetails);
  }

  /** UserDetails에서 실제 userId 추출 (JWT subject는 userSeq) */
  private String extractUserId(UserDetails userDetails) {
    if (userDetails == null) {
      return null;
    }
    Integer userSeq = userIdResolver.fromJwtUsername(userDetails.getUsername());
    return userIdResolver.toUserId(userSeq);
  }

  /**
   * 권한 기반 BillingStatsSearchRequest 생성
   *
   * <p>userId가 지정된 경우: 해당 유저만 조회 userId가 없는 경우: 권한에 따라 조회 범위 결정 - 90 이상: 전체 (userIds = null) -
   * 50-89: 관리하는 계정들 (userIds 설정) - 50 미만: 본인만 (userId 설정)
   */
  private BillingStatsSearchRequest buildRequestWithPermission(
      String userId, String startDate, String endDate, UserDetails userDetails) {

    // userId가 명시적으로 지정된 경우 해당 유저만 조회
    if (userId != null && !userId.isBlank()) {
      return BillingStatsSearchRequest.builder()
          .userId(userId)
          .startDate(startDate)
          .endDate(endDate)
          .build();
    }

    // 로그인 정보 없으면 빈 결과
    if (userDetails == null) {
      return BillingStatsSearchRequest.builder()
          .userId("__NONE__") // 존재하지 않는 ID로 빈 결과 반환
          .startDate(startDate)
          .endDate(endDate)
          .build();
    }

    String currentUserId = extractUserId(userDetails);
    Integer userLevel = adminService.getUserLevel(currentUserId);

    // determineQueryUserIds 결과: "ALL", "userId1,userId2", 또는 단일 userId
    String queryUserIds = adminService.determineQueryUserIds(currentUserId, userLevel);

    if ("ALL".equals(queryUserIds)) {
      // 전체 조회 (userId = null)
      return BillingStatsSearchRequest.builder().startDate(startDate).endDate(endDate).build();
    } else if (queryUserIds.contains(",")) {
      // 복수 유저 조회
      return BillingStatsSearchRequest.builder()
          .userIds(List.of(queryUserIds.split(",")))
          .startDate(startDate)
          .endDate(endDate)
          .build();
    } else {
      // 단일 유저 조회
      return BillingStatsSearchRequest.builder()
          .userId(queryUserIds)
          .startDate(startDate)
          .endDate(endDate)
          .build();
    }
  }
}
