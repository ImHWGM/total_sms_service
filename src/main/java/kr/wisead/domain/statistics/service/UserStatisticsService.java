package kr.wisead.domain.statistics.service;

import kr.wisead.domain.statistics.dto.*;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import kr.wisead.mapper.sms.UserStatisticsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 사용자별 통계 서비스
 * - 일반 메시지 통계 (SMS/LMS/MMS)
 * - 설문 메시지 통계
 * - QR 코드 통계
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserStatisticsService {

    private final UserStatisticsMapper userStatisticsMapper;
    private final SurveyMasterMapper surveyMasterMapper;

    private static final DateTimeFormatter YM_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 사용자별 통계 조회 (서비스 타입별)
     *
     * @param request 검색 조건
     * @return 서비스 타입에 따른 통계 목록
     */
    @Transactional(readOnly = true)
    public List<?> findUserStats(StatsSearchRequest request) {
        String serviceType = Optional.ofNullable(request.getServiceType()).orElse("M");

        switch (serviceType) {
            case "S":
                return findSurveyStats(request);
            case "Q":
                return findQrStats(request);
            default:
                return findMsgStats(request);
        }
    }

    /**
     * 사용자별 메시지 통계 조회 (일반 메시지: SMS/LMS/MMS)
     */
    @Transactional(readOnly = true)
    public List<UserMsgStatsResponse> findMsgStats(StatsSearchRequest request) {
        List<String> tables = buildTableList(request.getStartDate(), request.getEndDate(), true);

        String userId = request.getUserId() != null ? String.valueOf(request.getUserId()) : null;
        List<String> userIds = request.getUserIds() != null ?
                request.getUserIds().stream().map(String::valueOf).collect(Collectors.toList()) : null;

        List<UserMsgStatsResponse> stats = userStatisticsMapper.selectMsgStats(
                tables,
                request.getStartDate(),
                request.getEndDate(),
                userId,
                userIds
        );

        return stats != null ? stats : new ArrayList<>();
    }

    /**
     * 사용자별 설문 통계 조회
     */
    @Transactional(readOnly = true)
    public List<UserSurveyStatsResponse> findSurveyStats(StatsSearchRequest request) {
        List<String> tables = buildTableList(request.getStartDate(), request.getEndDate(), true);

        String userId = request.getUserId() != null ? String.valueOf(request.getUserId()) : null;
        List<String> userIds = request.getUserIds() != null ?
                request.getUserIds().stream().map(String::valueOf).collect(Collectors.toList()) : null;

        List<UserSurveyStatsResponse> stats = userStatisticsMapper.selectSurveyStats(
                tables,
                request.getStartDate(),
                request.getEndDate(),
                userId,
                userIds
        );

        return stats != null ? stats : new ArrayList<>();
    }

    /**
     * 사용자별 QR 통계 조회
     */
    @Transactional(readOnly = true)
    public List<UserQrStatsResponse> findQrStats(StatsSearchRequest request) {
        Long userSeq = request.getUserId() != null ? Long.valueOf(request.getUserId()) : null;
        List<Long> userSeqs = request.getUserIds() != null ?
                request.getUserIds().stream().map(Long::valueOf).collect(Collectors.toList()) : null;

        List<UserQrStatsResponse> stats = surveyMasterMapper.selectQrStatsByUser(
                request.getStartDate(),
                request.getEndDate(),
                userSeq,
                userSeqs
        );

        return stats != null ? stats : new ArrayList<>();
    }

    /**
     * 조회 대상 테이블 목록 생성
     * - 기간 내 월별 msg_result_YYYYMM 테이블 목록 생성
     * - 현재 월 포함 시 msg_queue 테이블 추가 (진행중 건수 포함)
     *
     * @param startDate 시작일 (YYYY-MM-DD)
     * @param endDate 종료일 (YYYY-MM-DD)
     * @param addQueueIfCurrent 현재 월 포함 시 큐 테이블 추가 여부
     * @return 테이블 목록
     */
    private List<String> buildTableList(String startDate, String endDate, boolean addQueueIfCurrent) {
        if (startDate == null || endDate == null) {
            // 기본값: 이번 달
            LocalDate now = LocalDate.now();
            startDate = now.withDayOfMonth(1).toString();
            endDate = now.toString();
        }

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        List<String> tables = new ArrayList<>();

        // 월별 테이블 추가
        LocalDate current = start.withDayOfMonth(1);
        while (!current.isAfter(end)) {
            String tableName = "msg_result_" + current.format(YM_FORMATTER);
            if (!tables.contains(tableName)) {
                tables.add(tableName);
            }
            current = current.plusMonths(1);
        }

        // 현재 월이 포함된 경우 msg_queue 테이블 추가
        if (addQueueIfCurrent) {
            String nowYm = LocalDate.now().format(YM_FORMATTER);
            if (tables.contains("msg_result_" + nowYm) && !tables.contains("msg_queue")) {
                tables.add("msg_queue");
            }
        }

        log.debug("조회 대상 테이블: {}", tables);
        return tables;
    }
}
