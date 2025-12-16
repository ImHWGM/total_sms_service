package kr.wisead.domain.admin.service;

import jakarta.servlet.http.HttpServletRequest;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.admin.dto.ActionLogRequest;
import kr.wisead.domain.admin.dto.ActionLogResponse;
import kr.wisead.domain.admin.dto.ActionLogSearchRequest;
import kr.wisead.domain.admin.entity.ActionLog;
import kr.wisead.mapper.primary.ActionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 액션 로그 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActionLogService {

    private final ActionLogMapper actionLogMapper;

    /**
     * 액션 로그 목록 조회
     */
    @Transactional(readOnly = true)
    public PageResponse<ActionLogResponse> getLogList(ActionLogSearchRequest request) {
        List<ActionLog> logs = actionLogMapper.selectList(request);
        int totalCount = actionLogMapper.selectCount(request);

        List<ActionLogResponse> responses = logs.stream()
                .map(ActionLogResponse::from)
                .toList();

        return PageResponse.of(responses, request.getPage(), request.getSize(), totalCount);
    }

    /**
     * 액션 로그 상세 조회
     */
    @Transactional(readOnly = true)
    public ActionLogResponse getLog(Long seq) {
        ActionLog log = actionLogMapper.selectById(seq);
        return ActionLogResponse.from(log);
    }

    /**
     * 액션 로그 전체 조회 (엑셀 다운로드용)
     */
    @Transactional(readOnly = true)
    public List<ActionLogResponse> getLogsForExcel(ActionLogSearchRequest request) {
        List<ActionLog> logs = actionLogMapper.selectAllForDownload(request);
        return logs.stream()
                .map(ActionLogResponse::from)
                .toList();
    }

    /**
     * 액션 로그 기록
     */
    @Transactional
    public void logAction(String userId, String userName, ActionLogRequest request, HttpServletRequest httpRequest) {
        ActionLog actionLog = ActionLog.builder()
                .menuName(request.getMenuName())
                .actionType(request.getActionType())
                .menuUrl(request.getMenuUrl() != null ? request.getMenuUrl() : httpRequest.getRequestURI())
                .code(request.getCode() != null ? request.getCode() : "200")
                .referer(request.getReferer() != null ? request.getReferer() : httpRequest.getHeader("Referer"))
                .userId(userId)
                .userName(userName)
                .ip(getClientIp(httpRequest))
                .build();

        actionLogMapper.insert(actionLog);
        log.info("액션 로그 기록: userId={}, menuName={}, actionType={}",
                userId, request.getMenuName(), request.getActionType());
    }

    /**
     * 다운로드 액션 로그 기록 (사유 포함)
     */
    @Transactional
    public void logDownloadAction(String userId, String userName, String menuName,
                                  String actionType, String actionReason, HttpServletRequest httpRequest) {
        ActionLog actionLog = ActionLog.builder()
                .menuName(menuName)
                .actionType(actionType)
                .actionReason(actionReason)
                .menuUrl(httpRequest.getRequestURI())
                .code("200")
                .referer(httpRequest.getHeader("Referer"))
                .userId(userId)
                .userName(userName)
                .ip(getClientIp(httpRequest))
                .build();

        actionLogMapper.insertDownloadLog(actionLog);
        log.info("다운로드 로그 기록: userId={}, menuName={}, reason={}", userId, menuName, actionReason);
    }

    /**
     * 마스킹 해제 로그 기록
     */
    @Transactional
    public void logPhoneMasking(String userId, String userName, String action,
                                String reason, String pageNumber, HttpServletRequest httpRequest) {
        String menuName;
        String code;

        if ("UNMASK_FAIL".equals(action)) {
            menuName = String.format("개인정보취합 %s 페이지 마스킹해제 실패", pageNumber);
            code = "401";
        } else {
            menuName = String.format("개인정보취합 %s 페이지 마스킹해제", pageNumber);
            code = "200";
        }

        ActionLog actionLog = ActionLog.builder()
                .menuName(menuName)
                .actionType("R")
                .actionReason(reason)
                .menuUrl(httpRequest.getRequestURI())
                .code(code)
                .referer(httpRequest.getHeader("Referer"))
                .userId(userId)
                .userName(userName)
                .ip(getClientIp(httpRequest))
                .build();

        actionLogMapper.insertDownloadLog(actionLog);
        log.info("마스킹 해제 로그: userId={}, action={}, page={}", userId, action, pageNumber);
    }

    /**
     * 다운로드 로그 기록 (간편 버전 - HttpServletRequest 없이)
     */
    @Transactional
    public void logDownload(String userId, String menuName, String reason) {
        ActionLog actionLog = ActionLog.builder()
                .menuName(menuName)
                .actionType("R")
                .actionReason(reason)
                .menuUrl("/api/excel")
                .code("200")
                .userId(userId)
                .userName(userId)
                .build();

        actionLogMapper.insertDownloadLog(actionLog);
        log.info("다운로드 로그 기록: userId={}, menuName={}, reason={}", userId, menuName, reason);
    }

    /**
     * 에러 로그 기록
     */
    @Transactional
    public void logError(String userId, String userName, String menuName,
                         String errorMessage, HttpServletRequest httpRequest) {
        ActionLog actionLog = ActionLog.builder()
                .menuName(menuName)
                .actionType("E")
                .actionReason(errorMessage)
                .menuUrl(httpRequest.getRequestURI())
                .code("500")
                .referer(httpRequest.getHeader("Referer"))
                .userId(userId)
                .userName(userName)
                .ip(getClientIp(httpRequest))
                .build();

        actionLogMapper.insertDownloadLog(actionLog);
        log.info("에러 로그 기록: userId={}, menuName={}, error={}", userId, menuName, errorMessage);
    }

    // ==================== Private Methods ====================

    /**
     * 클라이언트 IP 추출
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 쉼표로 구분된 경우 첫 번째 IP 사용
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }
}
