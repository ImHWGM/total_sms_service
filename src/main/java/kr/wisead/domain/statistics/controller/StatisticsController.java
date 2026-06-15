package kr.wisead.domain.statistics.controller;

import java.util.List;
import kr.wisead.common.annotation.AccessLog;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.statistics.dto.*;
import kr.wisead.domain.statistics.service.PeriodStatisticsService;
import kr.wisead.domain.statistics.service.StatisticsService;
import kr.wisead.domain.statistics.service.UserStatisticsService;
import kr.wisead.security.jwt.CurrentUser;
import kr.wisead.security.jwt.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 통계 Controller */
@Slf4j
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

  private final StatisticsService statisticsService;
  private final PeriodStatisticsService periodStatisticsService;
  private final UserStatisticsService userStatisticsService;

  /** 일별 통계 조회 GET /api/statistics/daily?startDate=2025-01-01&endDate=2025-01-31&serviceType=SMS */
  @GetMapping("/daily")
  public ApiResponse<List<DailyStatsResponse>> getDailyStats(
      @CurrentUser JwtPrincipal user,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String serviceType) {

    StatsSearchRequest request =
        StatsSearchRequest.builder()
            .userId(user.userId())
            .startDate(startDate)
            .endDate(endDate)
            .serviceType(serviceType)
            .build();

    List<DailyStatsResponse> stats = statisticsService.getDailyStats(request);
    return ApiResponse.success(stats);
  }

  /** 사용자별 통계 조회 (관리자용) GET /api/statistics/user?startDate=2025-01-01&endDate=2025-01-31 */
  @AccessLog(menuName = "사용자별 통계 조회")
  @GetMapping("/user")
  public ApiResponse<List<UserStatsResponse>> getUserStats(
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) List<String> userIds) {

    StatsSearchRequest request =
        StatsSearchRequest.builder().userIds(userIds).startDate(startDate).endDate(endDate).build();

    List<UserStatsResponse> stats = statisticsService.getUserStats(request);
    return ApiResponse.success(stats);
  }

  /** 사용량 요약 조회 (청구용) GET /api/statistics/usage-summary?startDate=2025-01-01&endDate=2025-01-31 */
  @GetMapping("/usage-summary")
  public ApiResponse<List<UsageSummaryResponse>> getUsageSummary(
      @CurrentUser JwtPrincipal user,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate) {

    StatsSearchRequest request =
        StatsSearchRequest.builder()
            .userId(user.userId())
            .startDate(startDate)
            .endDate(endDate)
            .build();

    List<UsageSummaryResponse> summary = statisticsService.getUsageSummary(request);
    return ApiResponse.success(summary);
  }

  /** 월별 통계 조회 GET /api/statistics/monthly?startDate=2025-01-01&endDate=2025-12-31 */
  @GetMapping("/monthly")
  public ApiResponse<List<MonthlyStatsResponse>> getMonthlyStats(
      @CurrentUser JwtPrincipal user,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String serviceType) {

    StatsSearchRequest request =
        StatsSearchRequest.builder()
            .userId(user.userId())
            .startDate(startDate)
            .endDate(endDate)
            .serviceType(serviceType)
            .build();

    List<MonthlyStatsResponse> stats = statisticsService.getMonthlyStats(request);
    return ApiResponse.success(stats);
  }

  /**
   * 기간별 통계 조회 (관리자용 - 전체 사용자) GET /api/statistics/period?startDate=2025-01-01&endDate=2025-01-31
   */
  @GetMapping("/period")
  public ApiResponse<List<DailyStatsResponse>> getPeriodStats(
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String serviceType) {

    StatsSearchRequest request =
        StatsSearchRequest.builder()
            .startDate(startDate)
            .endDate(endDate)
            .serviceType(serviceType)
            .build();

    List<DailyStatsResponse> stats = statisticsService.getDailyStats(request);
    return ApiResponse.success(stats);
  }

  // ==================== 기간별 상세 통계 (PeriodStatisticsService) ====================

  /**
   * 기간별 일별 통계 조회 (루프 방식 - 대기/진행중 포함) GET
   * /api/statistics/period/daily?startDate=2025-01-01&endDate=2025-01-31
   */
  @GetMapping("/period/daily")
  public ApiResponse<List<DailyStatsResponse>> getPeriodDailyStats(
      @CurrentUser JwtPrincipal user,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String msgType,
      @RequestParam(required = false) String serviceType) {

    StatsSearchRequest request =
        StatsSearchRequest.builder()
            .userId(user.userId())
            .startDate(startDate)
            .endDate(endDate)
            .msgType(msgType)
            .serviceType(serviceType)
            .build();

    List<DailyStatsResponse> stats = periodStatisticsService.getDailyStatsByLoop(request);
    return ApiResponse.success(stats);
  }

  /**
   * 기간별 일별 통계 조회 (관리자용 - 전체/특정 사용자) GET
   * /api/statistics/period/daily/admin?startDate=2025-01-01&endDate=2025-01-31&userId=xxx
   */
  @GetMapping("/period/daily/admin")
  public ApiResponse<List<DailyStatsResponse>> getPeriodDailyStatsAdmin(
      @RequestParam String startDate,
      @RequestParam String endDate,
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) List<String> userIds,
      @RequestParam(required = false) String msgType,
      @RequestParam(required = false) String serviceType) {

    StatsSearchRequest request =
        StatsSearchRequest.builder()
            .userId(userId)
            .userIds(userIds)
            .startDate(startDate)
            .endDate(endDate)
            .msgType(msgType)
            .serviceType(serviceType)
            .build();

    List<DailyStatsResponse> stats = periodStatisticsService.getDailyStatsByLoop(request);
    return ApiResponse.success(stats);
  }

  /** 특정 일자 상세 통계 조회 GET /api/statistics/period/day/2025-01-15 */
  @GetMapping("/period/day/{date}")
  public ApiResponse<DailyStatsResponse> getDayStats(
      @PathVariable String date,
      @CurrentUser JwtPrincipal user,
      @RequestParam(required = false) String msgType,
      @RequestParam(required = false) String serviceType) {

    StatsSearchRequest request =
        StatsSearchRequest.builder()
            .userId(user.userId())
            .msgType(msgType)
            .serviceType(serviceType)
            .build();

    DailyStatsResponse stats = periodStatisticsService.getDayStats(date, request);
    return ApiResponse.success(stats);
  }

  /** 기간 합계 통계 조회 GET /api/statistics/period/total?startDate=2025-01-01&endDate=2025-01-31 */
  @GetMapping("/period/total")
  public ApiResponse<DailyStatsResponse> getPeriodTotalStats(
      @CurrentUser JwtPrincipal user,
      @RequestParam String startDate,
      @RequestParam String endDate,
      @RequestParam(required = false) String msgType,
      @RequestParam(required = false) String serviceType) {

    StatsSearchRequest request =
        StatsSearchRequest.builder()
            .userId(user.userId())
            .startDate(startDate)
            .endDate(endDate)
            .msgType(msgType)
            .serviceType(serviceType)
            .build();

    DailyStatsResponse stats = periodStatisticsService.getPeriodTotalStats(request);
    return ApiResponse.success(stats);
  }

  /**
   * 기간 합계 통계 조회 (관리자용) GET
   * /api/statistics/period/total/admin?startDate=2025-01-01&endDate=2025-01-31
   */
  @GetMapping("/period/total/admin")
  public ApiResponse<DailyStatsResponse> getPeriodTotalStatsAdmin(
      @RequestParam String startDate,
      @RequestParam String endDate,
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) List<String> userIds,
      @RequestParam(required = false) String msgType,
      @RequestParam(required = false) String serviceType) {

    StatsSearchRequest request =
        StatsSearchRequest.builder()
            .userId(userId)
            .userIds(userIds)
            .startDate(startDate)
            .endDate(endDate)
            .msgType(msgType)
            .serviceType(serviceType)
            .build();

    DailyStatsResponse stats = periodStatisticsService.getPeriodTotalStats(request);
    return ApiResponse.success(stats);
  }

  // ==================== 사용자별 통계 (UserStatisticsService) ====================

  /**
   * 사용자별 통계 조회 (서비스 타입별) GET
   * /api/statistics/user-stats?startDate=2025-01-01&endDate=2025-01-31&serviceType=M serviceType:
   * M(일반메시지), S(설문), Q(QR)
   */
  @AccessLog(menuName = "사용자별 서비스 통계 조회")
  @GetMapping("/user-stats")
  public ApiResponse<List<?>> getUserStatsByServiceType(
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false, defaultValue = "M") String serviceType,
      @RequestParam(required = false) List<String> userIds) {

    StatsSearchRequest request =
        StatsSearchRequest.builder()
            .userIds(userIds)
            .startDate(startDate)
            .endDate(endDate)
            .serviceType(serviceType)
            .build();

    List<?> stats = userStatisticsService.findUserStats(request);
    return ApiResponse.success(stats);
  }

  /**
   * 사용자별 메시지 통계 조회 (SMS/LMS/MMS) GET
   * /api/statistics/user-stats/msg?startDate=2025-01-01&endDate=2025-01-31
   */
  @AccessLog(menuName = "사용자별 메시지 통계 조회")
  @GetMapping("/user-stats/msg")
  public ApiResponse<List<UserMsgStatsResponse>> getUserMsgStats(
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) List<String> userIds) {

    StatsSearchRequest request =
        StatsSearchRequest.builder()
            .userId(userId)
            .userIds(userIds)
            .startDate(startDate)
            .endDate(endDate)
            .build();

    List<UserMsgStatsResponse> stats = userStatisticsService.findMsgStats(request);
    return ApiResponse.success(stats);
  }

  /** 사용자별 설문 통계 조회 GET /api/statistics/user-stats/survey?startDate=2025-01-01&endDate=2025-01-31 */
  @AccessLog(menuName = "사용자별 설문 통계 조회")
  @GetMapping("/user-stats/survey")
  public ApiResponse<List<UserSurveyStatsResponse>> getUserSurveyStats(
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) List<String> userIds) {

    StatsSearchRequest request =
        StatsSearchRequest.builder()
            .userId(userId)
            .userIds(userIds)
            .startDate(startDate)
            .endDate(endDate)
            .build();

    List<UserSurveyStatsResponse> stats = userStatisticsService.findSurveyStats(request);
    return ApiResponse.success(stats);
  }

  /** 사용자별 QR 통계 조회 GET /api/statistics/user-stats/qr?startDate=2025-01-01&endDate=2025-01-31 */
  @AccessLog(menuName = "사용자별 QR 통계 조회")
  @GetMapping("/user-stats/qr")
  public ApiResponse<List<UserQrStatsResponse>> getUserQrStats(
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) List<String> userIds) {

    StatsSearchRequest request =
        StatsSearchRequest.builder()
            .userId(userId)
            .userIds(userIds)
            .startDate(startDate)
            .endDate(endDate)
            .build();

    List<UserQrStatsResponse> stats = userStatisticsService.findQrStats(request);
    return ApiResponse.success(stats);
  }
}
