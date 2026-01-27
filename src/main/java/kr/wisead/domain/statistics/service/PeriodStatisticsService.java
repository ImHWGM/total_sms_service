package kr.wisead.domain.statistics.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import kr.wisead.domain.statistics.dto.DailyStatsResponse;
import kr.wisead.domain.statistics.dto.StatsSearchRequest;
import kr.wisead.mapper.sms.PeriodStatisticsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기간별 통계 서비스 - 일별 루프를 통한 정확한 통계 조회 - 과거/오늘/미래 데이터 구분 처리 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PeriodStatisticsService {

  private final PeriodStatisticsMapper periodStatisticsMapper;

  private static final DateTimeFormatter YYYYMM_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
  private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_DATE;

  /**
   * 기간별 일별 통계 조회 (루프 방식) - 과거: msg_result 테이블에서 조회 - 오늘: msg_result + msg_queue 테이블 조회 - 미래:
   * msg_queue 테이블에서 예약 건수 조회
   */
  @Transactional(readOnly = true)
  public List<DailyStatsResponse> getDailyStatsByLoop(StatsSearchRequest request) {
    LocalDate today = LocalDate.now();
    LocalDate startDate = resolveStartDate(request.getStartDate(), today);
    LocalDate endDate = resolveEndDate(request.getEndDate(), today);

    String userId = request.getUserId() != null ? String.valueOf(request.getUserId()) : null;
    List<String> userIds =
        request.getUserIds() != null
            ? request.getUserIds().stream().map(String::valueOf).toList()
            : null;

    List<DailyStatsResponse> result = new ArrayList<>();

    for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
      String tableName = "msg_result_" + date.format(YYYYMM_FORMATTER);
      String dateStr = date.format(ISO_DATE_FORMATTER);

      try {
        DailyStatsResponse stats;

        if (date.isBefore(today)) {
          // 과거: 결과 테이블에서만 조회
          stats =
              queryPastDayStats(
                  tableName,
                  dateStr,
                  request.getMsgType(),
                  request.getServiceType(),
                  userId,
                  userIds);

        } else if (date.isEqual(today)) {
          // 오늘: 결과 테이블 + 큐 테이블 조회
          stats =
              queryTodayStats(
                  tableName,
                  dateStr,
                  request.getMsgType(),
                  request.getServiceType(),
                  userId,
                  userIds);

        } else {
          // 미래: 큐 테이블에서 예약 건수만 조회
          stats =
              queryFutureDayStats(
                  dateStr, request.getMsgType(), request.getServiceType(), userId, userIds);
        }

        if (stats != null) {
          result.add(stats);
        }

      } catch (Exception e) {
        log.warn("일별 통계 조회 실패: date={}, error={}", dateStr, e.getMessage());
        // 조회 실패 시 빈 데이터 추가
        result.add(createEmptyStats(dateStr));
      }
    }

    // 날짜 내림차순 정렬
    result.sort(Comparator.comparing(DailyStatsResponse::getDtStats).reversed());
    return result;
  }

  /** 과거 일자 통계 조회 */
  private DailyStatsResponse queryPastDayStats(
      String tableName,
      String date,
      String msgType,
      String serviceType,
      String userId,
      List<String> userIds) {
    DailyStatsResponse stats =
        periodStatisticsMapper.countOneDayStats(
            tableName, msgType, serviceType, date, userId, userIds);

    if (stats == null) {
      stats = createEmptyStats(date, serviceType);
    } else {
      stats.setWaitCnt(0);
      stats.setIngCnt(0);
      stats.setServiceType(serviceType);
    }

    return stats;
  }

  /** 오늘 일자 통계 조회 */
  private DailyStatsResponse queryTodayStats(
      String tableName,
      String date,
      String msgType,
      String serviceType,
      String userId,
      List<String> userIds) {
    // 결과 테이블 조회
    DailyStatsResponse stats =
        periodStatisticsMapper.countOneDayStats(
            tableName, msgType, serviceType, date, userId, userIds);

    if (stats == null) {
      stats = createEmptyStats(date, serviceType);
    } else {
      stats.setServiceType(serviceType);
    }

    // 대기 건수 조회
    int waitCnt =
        periodStatisticsMapper.countWaitStats(date, msgType, serviceType, userId, userIds);

    // 진행중 건수 조회
    int ingCnt =
        periodStatisticsMapper.countInProgressStats(date, msgType, serviceType, userId, userIds);

    stats.setWaitCnt(waitCnt);
    stats.setIngCnt(ingCnt);
    stats.setInCnt(stats.getInCnt() + waitCnt + ingCnt);

    return stats;
  }

  /** 미래 일자 통계 조회 (예약 건수만) */
  private DailyStatsResponse queryFutureDayStats(
      String date, String msgType, String serviceType, String userId, List<String> userIds) {
    // 대기 건수만 조회
    int waitCnt =
        periodStatisticsMapper.countWaitStats(date, msgType, serviceType, userId, userIds);

    DailyStatsResponse stats = createEmptyStats(date, serviceType);
    stats.setWaitCnt(waitCnt);
    stats.setInCnt(waitCnt);

    return stats;
  }

  /** 빈 통계 데이터 생성 */
  private DailyStatsResponse createEmptyStats(String date) {
    return createEmptyStats(date, null);
  }

  /** 빈 통계 데이터 생성 (serviceType 포함) */
  private DailyStatsResponse createEmptyStats(String date, String serviceType) {
    return DailyStatsResponse.builder()
        .dtStats(date)
        .serviceType(serviceType)
        .inCnt(0)
        .succCnt(0)
        .errorCnt(0)
        .failCnt(0)
        .ingCnt(0)
        .waitCnt(0)
        .build();
  }

  /** 특정 일자 상세 통계 조회 */
  @Transactional(readOnly = true)
  public DailyStatsResponse getDayStats(String date, StatsSearchRequest request) {
    LocalDate targetDate = LocalDate.parse(date);
    LocalDate today = LocalDate.now();

    String tableName = "msg_result_" + targetDate.format(YYYYMM_FORMATTER);
    String userId = request.getUserId() != null ? String.valueOf(request.getUserId()) : null;
    List<String> userIds =
        request.getUserIds() != null
            ? request.getUserIds().stream().map(String::valueOf).toList()
            : null;

    if (targetDate.isBefore(today)) {
      return queryPastDayStats(
          tableName, date, request.getMsgType(), request.getServiceType(), userId, userIds);
    } else if (targetDate.isEqual(today)) {
      return queryTodayStats(
          tableName, date, request.getMsgType(), request.getServiceType(), userId, userIds);
    } else {
      return queryFutureDayStats(
          date, request.getMsgType(), request.getServiceType(), userId, userIds);
    }
  }

  /** 기간 합계 통계 조회 */
  @Transactional(readOnly = true)
  public DailyStatsResponse getPeriodTotalStats(StatsSearchRequest request) {
    LocalDate today = LocalDate.now();
    String startDateStr =
        resolveStartDate(request.getStartDate(), today).format(ISO_DATE_FORMATTER);
    String endDateStr = resolveEndDate(request.getEndDate(), today).format(ISO_DATE_FORMATTER);

    List<DailyStatsResponse> dailyStats = getDailyStatsByLoop(request);

    int totalIn = 0, totalSucc = 0, totalError = 0, totalFail = 0, totalIng = 0, totalWait = 0;

    for (DailyStatsResponse stats : dailyStats) {
      totalIn += stats.getInCnt();
      totalSucc += stats.getSuccCnt();
      totalError += stats.getErrorCnt();
      totalFail += stats.getFailCnt();
      totalIng += stats.getIngCnt();
      totalWait += stats.getWaitCnt();
    }

    return DailyStatsResponse.builder()
        .dtStats(startDateStr + " ~ " + endDateStr)
        .serviceType(request.getServiceType())
        .inCnt(totalIn)
        .succCnt(totalSucc)
        .errorCnt(totalError)
        .failCnt(totalFail)
        .ingCnt(totalIng)
        .waitCnt(totalWait)
        .build();
  }

  /** 시작일 파라미터 처리 - null 또는 빈 값이면 현재 월 1일 반환 */
  private LocalDate resolveStartDate(String startDate, LocalDate today) {
    if (startDate == null || startDate.isBlank()) {
      return today.withDayOfMonth(1);
    }
    return LocalDate.parse(startDate);
  }

  /** 종료일 파라미터 처리 - null 또는 빈 값이면 오늘 반환 */
  private LocalDate resolveEndDate(String endDate, LocalDate today) {
    if (endDate == null || endDate.isBlank()) {
      return today;
    }
    return LocalDate.parse(endDate);
  }
}
