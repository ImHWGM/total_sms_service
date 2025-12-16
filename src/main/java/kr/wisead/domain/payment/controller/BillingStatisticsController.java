package kr.wisead.domain.payment.controller;

import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.payment.dto.*;
import kr.wisead.domain.payment.entity.Balance;
import kr.wisead.domain.payment.service.BillingStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 과금 통계 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/billing/statistics")
@RequiredArgsConstructor
public class BillingStatisticsController {

    private final BillingStatisticsService billingStatisticsService;

    /**
     * 일별 과금 통계 조회
     * GET /api/billing/statistics/daily?userId=xxx&startDate=2024-01-01&endDate=2024-01-31
     */
    @GetMapping("/daily")
    public ApiResponse<List<DailyBillingStatsResponse>> getDailyStats(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        BillingStatsSearchRequest request = BillingStatsSearchRequest.builder()
                .userId(userId)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        List<DailyBillingStatsResponse> response = billingStatisticsService.getDailyBillingStats(request);
        return ApiResponse.success(response);
    }

    /**
     * 월별 과금 통계 조회
     * GET /api/billing/statistics/monthly?userId=xxx&startDate=2024-01-01&endDate=2024-12-31
     */
    @GetMapping("/monthly")
    public ApiResponse<List<MonthlyBillingStatsResponse>> getMonthlyStats(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        BillingStatsSearchRequest request = BillingStatsSearchRequest.builder()
                .userId(userId)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        List<MonthlyBillingStatsResponse> response = billingStatisticsService.getMonthlyBillingStats(request);
        return ApiResponse.success(response);
    }

    /**
     * 서비스 타입별 과금 통계 조회
     * GET /api/billing/statistics/service-type?userId=xxx&startDate=2024-01-01&endDate=2024-12-31
     */
    @GetMapping("/service-type")
    public ApiResponse<List<ServiceTypeBillingStatsResponse>> getStatsByServiceType(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        BillingStatsSearchRequest request = BillingStatsSearchRequest.builder()
                .userId(userId)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        List<ServiceTypeBillingStatsResponse> response = billingStatisticsService.getBillingStatsByServiceType(request);
        return ApiResponse.success(response);
    }

    /**
     * 사용자별 과금 통계 조회
     * POST /api/billing/statistics/users
     */
    @PostMapping("/users")
    public ApiResponse<List<UserBillingStatsResponse>> getStatsByUser(
            @RequestBody BillingStatsSearchRequest request) {

        List<UserBillingStatsResponse> response = billingStatisticsService.getBillingStatsByUser(request);
        return ApiResponse.success(response);
    }

    /**
     * 과금 총계 조회
     * GET /api/billing/statistics/summary?userId=xxx&startDate=2024-01-01&endDate=2024-12-31
     */
    @GetMapping("/summary")
    public ApiResponse<BillingSummaryResponse> getSummary(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        BillingStatsSearchRequest request = BillingStatsSearchRequest.builder()
                .userId(userId)
                .startDate(startDate)
                .endDate(endDate)
                .build();

        BillingSummaryResponse response = billingStatisticsService.getBillingSummary(request);
        return ApiResponse.success(response);
    }

    /**
     * 현재 월 과금 요약 조회 (대시보드용)
     * GET /api/billing/statistics/current-month
     */
    @GetMapping("/current-month")
    public ApiResponse<BillingSummaryResponse> getCurrentMonthSummary(
            @RequestParam(required = false) String userId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String targetUserId = userId != null ? userId :
                (userDetails != null ? userDetails.getUsername() : null);

        BillingSummaryResponse response = billingStatisticsService.getCurrentMonthSummary(targetUserId);
        return ApiResponse.success(response);
    }

    /**
     * 이전 월 과금 요약 조회
     * GET /api/billing/statistics/previous-month
     */
    @GetMapping("/previous-month")
    public ApiResponse<BillingSummaryResponse> getPreviousMonthSummary(
            @RequestParam(required = false) String userId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String targetUserId = userId != null ? userId :
                (userDetails != null ? userDetails.getUsername() : null);

        BillingSummaryResponse response = billingStatisticsService.getPreviousMonthSummary(targetUserId);
        return ApiResponse.success(response);
    }

    /**
     * 특정 사용자의 과금 요약 조회
     * GET /api/billing/statistics/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ApiResponse<UserBillingStatsResponse> getUserSummary(
            @PathVariable String userId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        UserBillingStatsResponse response = billingStatisticsService.getUserBillingSummary(userId, startDate, endDate);
        return ApiResponse.success(response);
    }

    /**
     * 내 과금 요약 조회 (로그인 사용자)
     * GET /api/billing/statistics/my
     */
    @GetMapping("/my")
    public ApiResponse<UserBillingStatsResponse> getMySummary(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        String userId = userDetails != null ? userDetails.getUsername() : null;
        UserBillingStatsResponse response = billingStatisticsService.getUserBillingSummary(userId, startDate, endDate);
        return ApiResponse.success(response);
    }

    /**
     * 일별 추이 조회 (최근 N일)
     * GET /api/billing/statistics/trend/daily?userId=xxx&days=30
     */
    @GetMapping("/trend/daily")
    public ApiResponse<List<DailyBillingStatsResponse>> getDailyTrend(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "30") int days) {

        List<DailyBillingStatsResponse> response = billingStatisticsService.getDailyTrend(userId, days);
        return ApiResponse.success(response);
    }

    /**
     * 월별 추이 조회 (최근 N개월)
     * GET /api/billing/statistics/trend/monthly?userId=xxx&months=12
     */
    @GetMapping("/trend/monthly")
    public ApiResponse<List<MonthlyBillingStatsResponse>> getMonthlyTrend(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "12") int months) {

        List<MonthlyBillingStatsResponse> response = billingStatisticsService.getMonthlyTrend(userId, months);
        return ApiResponse.success(response);
    }

    /**
     * 최근 거래 내역 조회
     * GET /api/billing/statistics/recent?operation=P&limit=20
     */
    @GetMapping("/recent")
    public ApiResponse<List<Balance>> getRecentTransactions(
            @RequestParam(required = false) String operation,
            @RequestParam(defaultValue = "20") int limit) {

        List<Balance> response = billingStatisticsService.getRecentTransactions(operation, limit);
        return ApiResponse.success(response);
    }
}
