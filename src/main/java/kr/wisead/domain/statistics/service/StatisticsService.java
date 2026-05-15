package kr.wisead.domain.statistics.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.statistics.dto.*;
import kr.wisead.mapper.sms.StatisticsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 통계 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {

  private final StatisticsMapper statisticsMapper;
  private final UserIdResolver userIdResolver;

  private static final String TABLE_PREFIX = "msg_result_";
  private static final DateTimeFormatter YEAR_MONTH_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMM");

  /** 기간별 일별 통계 조회 기간에 걸쳐있는 여러 월의 테이블을 조회하여 합산 */
  public List<DailyStatsResponse> getDailyStats(StatsSearchRequest request) {
    List<String> tableNames = getTableNames(request.getStartDate(), request.getEndDate());
    List<DailyStatsResponse> allStats = new ArrayList<>();

    for (String tableName : tableNames) {
      try {
        List<DailyStatsResponse> monthStats =
            statisticsMapper.selectDailyStats(
                tableName,
                request.getUserId() != null ? String.valueOf(request.getUserId()) : null,
                request.getServiceType(),
                request.getStartDate(),
                request.getEndDate());
        if (monthStats != null) {
          allStats.addAll(monthStats);
        }
      } catch (Exception e) {
        log.warn("테이블 조회 실패: {} - {}", tableName, e.getMessage());
      }
    }

    // 일자별로 그룹핑하여 합산
    Map<String, DailyStatsResponse> groupedStats = new LinkedHashMap<>();
    for (DailyStatsResponse stat : allStats) {
      String key =
          stat.getDtStats() + "_" + (stat.getServiceType() != null ? stat.getServiceType() : "ALL");
      groupedStats.merge(key, stat, this::mergeDailyStats);
    }

    return new ArrayList<>(groupedStats.values());
  }

  /** 사용자별 통계 조회 */
  public List<UserStatsResponse> getUserStats(StatsSearchRequest request) {
    List<String> tableNames = getTableNames(request.getStartDate(), request.getEndDate());
    Map<Integer, UserStatsResponse> userStatsMap = new HashMap<>();

    List<String> userIdStrings =
        (request.getUserIds() != null && !request.getUserIds().isEmpty())
            ? request.getUserIds().stream().map(String::valueOf).toList()
            : null;

    for (String tableName : tableNames) {
      try {
        List<Map<String, Object>> monthStats =
            statisticsMapper.selectUserStats(
                tableName, userIdStrings, request.getStartDate(), request.getEndDate());

        if (monthStats != null) {
          for (Map<String, Object> stat : monthStats) {
            String userIdStr = (String) stat.get("userId");
            if (userIdStr == null) continue;

            Integer userSeq = userIdResolver.toUserSeq(userIdStr);
            if (userSeq == null) continue;

            String serviceType = (String) stat.get("serviceType");
            int totalCnt = getIntValue(stat, "totalCnt");
            int succCnt = getIntValue(stat, "succCnt");
            int failCnt = getIntValue(stat, "failCnt");

            UserStatsResponse userStats =
                userStatsMap.computeIfAbsent(
                    userSeq, k -> UserStatsResponse.builder().userSeq(k).build());

            updateUserStatsByServiceType(userStats, serviceType, totalCnt, succCnt, failCnt);
          }
        }
      } catch (Exception e) {
        log.warn("테이블 조회 실패: {} - {}", tableName, e.getMessage());
      }
    }

    return new ArrayList<>(userStatsMap.values());
  }

  /** 사용량 요약 조회 (청구용) */
  public List<UsageSummaryResponse> getUsageSummary(StatsSearchRequest request) {
    List<String> tableNames = getTableNames(request.getStartDate(), request.getEndDate());
    Map<String, UsageSummaryResponse> summaryMap = new LinkedHashMap<>();

    for (String tableName : tableNames) {
      try {
        List<UsageSummaryResponse> monthSummary =
            statisticsMapper.selectUsageSummary(
                tableName,
                request.getUserId() != null ? String.valueOf(request.getUserId()) : null,
                request.getStartDate(),
                request.getEndDate());

        if (monthSummary != null) {
          for (UsageSummaryResponse summary : monthSummary) {
            String key = summary.getServiceTypeName() + "_" + summary.getCategory();
            summaryMap.merge(key, summary, this::mergeUsageSummary);
          }
        }
      } catch (Exception e) {
        log.warn("테이블 조회 실패: {} - {}", tableName, e.getMessage());
      }
    }

    return new ArrayList<>(summaryMap.values());
  }

  /** 월별 통계 조회 */
  public List<MonthlyStatsResponse> getMonthlyStats(StatsSearchRequest request) {
    List<String> tableNames = getTableNames(request.getStartDate(), request.getEndDate());
    List<MonthlyStatsResponse> monthlyStats = new ArrayList<>();

    for (String tableName : tableNames) {
      try {
        String yearMonth = tableName.replace(TABLE_PREFIX, "");
        String formattedYearMonth = yearMonth.substring(0, 4) + "-" + yearMonth.substring(4, 6);

        Map<String, Object> monthTotal =
            statisticsMapper.selectMonthlyTotalCount(
                tableName,
                request.getUserId() != null ? String.valueOf(request.getUserId()) : null);

        if (monthTotal != null) {
          MonthlyStatsResponse stats =
              MonthlyStatsResponse.builder()
                  .yearMonth(formattedYearMonth)
                  .serviceType(request.getServiceType())
                  .totalCnt(getIntValue(monthTotal, "totalCnt"))
                  .succCnt(getIntValue(monthTotal, "succCnt"))
                  .failCnt(getIntValue(monthTotal, "failCnt"))
                  .build();
          monthlyStats.add(stats);
        }
      } catch (Exception e) {
        log.warn("테이블 조회 실패: {} - {}", tableName, e.getMessage());
      }
    }

    return monthlyStats;
  }

  // ==================== Private Methods ====================

  /** 시작일과 종료일 사이의 테이블명 목록 생성 */
  private List<String> getTableNames(String startDate, String endDate) {
    List<String> tableNames = new ArrayList<>();

    if (startDate == null || startDate.isBlank() || endDate == null || endDate.isBlank()) {
      // 기본값: 현재 월
      String currentMonth = LocalDate.now().format(YEAR_MONTH_FORMATTER);
      tableNames.add(TABLE_PREFIX + currentMonth);
      return tableNames;
    }

    try {
      LocalDate start = LocalDate.parse(startDate);
      LocalDate end = LocalDate.parse(endDate);

      YearMonth startYm = YearMonth.from(start);
      YearMonth endYm = YearMonth.from(end);

      YearMonth current = startYm;
      while (!current.isAfter(endYm)) {
        tableNames.add(TABLE_PREFIX + current.format(YEAR_MONTH_FORMATTER));
        current = current.plusMonths(1);
      }
    } catch (Exception e) {
      log.error("날짜 파싱 오류: startDate={}, endDate={}", startDate, endDate, e);
      String currentMonth = LocalDate.now().format(YEAR_MONTH_FORMATTER);
      tableNames.add(TABLE_PREFIX + currentMonth);
    }

    return tableNames;
  }

  /** 일별 통계 병합 */
  private DailyStatsResponse mergeDailyStats(
      DailyStatsResponse existing, DailyStatsResponse newStats) {
    return DailyStatsResponse.builder()
        .dtStats(existing.getDtStats())
        .serviceType(existing.getServiceType())
        .inCnt(existing.getInCnt() + newStats.getInCnt())
        .succCnt(existing.getSuccCnt() + newStats.getSuccCnt())
        .errorCnt(existing.getErrorCnt() + newStats.getErrorCnt())
        .failCnt(existing.getFailCnt() + newStats.getFailCnt())
        .ingCnt(existing.getIngCnt() + newStats.getIngCnt())
        .waitCnt(existing.getWaitCnt() + newStats.getWaitCnt())
        .build();
  }

  /** 사용량 요약 병합 */
  private UsageSummaryResponse mergeUsageSummary(
      UsageSummaryResponse existing, UsageSummaryResponse newSummary) {
    return UsageSummaryResponse.builder()
        .serviceTypeName(existing.getServiceTypeName())
        .category(existing.getCategory())
        .count(existing.getCount() + newSummary.getCount())
        .unitPrice(existing.getUnitPrice())
        .amount(existing.getAmount())
        .remarks(existing.getRemarks())
        .build();
  }

  /** 서비스 타입별 사용자 통계 업데이트 */
  private void updateUserStatsByServiceType(
      UserStatsResponse userStats, String serviceType, int totalCnt, int succCnt, int failCnt) {
    if (serviceType == null) return;

    switch (serviceType.toUpperCase()) {
      case "SMS" -> {
        userStats.setSmsTotalCnt(userStats.getSmsTotalCnt() + totalCnt);
        userStats.setSmsSuccCnt(userStats.getSmsSuccCnt() + succCnt);
        userStats.setSmsFailCnt(userStats.getSmsFailCnt() + failCnt);
      }
      case "LMS" -> {
        userStats.setLmsTotalCnt(userStats.getLmsTotalCnt() + totalCnt);
        userStats.setLmsSuccCnt(userStats.getLmsSuccCnt() + succCnt);
        userStats.setLmsFailCnt(userStats.getLmsFailCnt() + failCnt);
      }
      case "MMS" -> {
        userStats.setMmsTotalCnt(userStats.getMmsTotalCnt() + totalCnt);
        userStats.setMmsSuccCnt(userStats.getMmsSuccCnt() + succCnt);
        userStats.setMmsFailCnt(userStats.getMmsFailCnt() + failCnt);
      }
      case "ATA", "FTA", "FTI" -> {
        userStats.setKakaoTotalCnt(userStats.getKakaoTotalCnt() + totalCnt);
        userStats.setKakaoSuccCnt(userStats.getKakaoSuccCnt() + succCnt);
        userStats.setKakaoFailCnt(userStats.getKakaoFailCnt() + failCnt);
      }
      default -> log.debug("알 수 없는 서비스 타입: {}", serviceType);
    }
  }

  /** Map에서 int 값 추출 */
  private int getIntValue(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String str) {
      try {
        return Integer.parseInt(str);
      } catch (NumberFormatException e) {
        return 0;
      }
    }
    return 0;
  }
}
