package kr.wisead.domain.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.admin.dto.*;
import kr.wisead.domain.admin.service.ActionLogService;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.user.dto.UserResponse;
import kr.wisead.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ActionLogService actionLogService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 관리자 계정 생성
     * POST /api/admin/account
     */
    @PostMapping("/account")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> createAdminAccount(
            @Valid @RequestBody AdminAccountRequest request,
            @RequestHeader("Authorization") String token) {

        String creatorId = jwtTokenProvider.getUserId(token.replace("Bearer ", ""));
        UserResponse response = adminService.createAdminAccount(request, creatorId);
        return ApiResponse.success(response, "관리자 계정이 생성되었습니다.");
    }

    /**
     * 액션 로그 목록 조회
     * GET /api/admin/logs
     */
    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<ActionLogResponse>> getActionLogs(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String searchField,
            @RequestParam(required = false) String searchKeyword,
            @RequestParam(required = false) String actionType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        ActionLogSearchRequest request = ActionLogSearchRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .searchField(searchField)
                .searchKeyword(searchKeyword)
                .actionType(actionType)
                .page(page)
                .size(size)
                .build();

        PageResponse<ActionLogResponse> response = actionLogService.getLogList(request);
        return ApiResponse.success(response);
    }

    /**
     * 액션 로그 상세 조회
     * GET /api/admin/logs/{seq}
     */
    @GetMapping("/logs/{seq}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ActionLogResponse> getActionLog(@PathVariable Long seq) {
        ActionLogResponse response = actionLogService.getLog(seq);
        return ApiResponse.success(response);
    }

    /**
     * 전화번호 마스킹 해제 로그 기록
     * POST /api/admin/logs/phone-masking
     */
    @PostMapping("/logs/phone-masking")
    public ApiResponse<Void> logPhoneMaskingAction(
            @Valid @RequestBody PhoneMaskingLogRequest request,
            @RequestHeader("Authorization") String token,
            HttpServletRequest httpRequest) {

        String accessToken = token.replace("Bearer ", "");
        String userId = jwtTokenProvider.getUserId(accessToken);
        String userName = jwtTokenProvider.getUserName(accessToken);

        try {
            actionLogService.logPhoneMasking(
                    userId, userName,
                    request.getAction(), request.getReason(), request.getPageNumber(),
                    httpRequest
            );
            return ApiResponse.success("로그가 기록되었습니다.");
        } catch (Exception e) {
            log.error("마스킹 해제 로그 기록 실패", e);
            // 에러 로그도 기록
            actionLogService.logError(
                    userId, userName,
                    "마스킹해제 로그 기록 실패",
                    e.getMessage(),
                    httpRequest
            );
            return ApiResponse.error(ErrorCode.INTERNAL_ERROR, "로그 기록에 실패했습니다.");
        }
    }

    /**
     * 다운로드 액션 로그 기록
     * POST /api/admin/logs/download
     */
    @PostMapping("/logs/download")
    public ApiResponse<Void> logDownloadAction(
            @RequestParam String menuName,
            @RequestParam String reason,
            @RequestHeader("Authorization") String token,
            HttpServletRequest httpRequest) {

        String accessToken = token.replace("Bearer ", "");
        String userId = jwtTokenProvider.getUserId(accessToken);
        String userName = jwtTokenProvider.getUserName(accessToken);

        actionLogService.logDownloadAction(
                userId, userName, menuName, "D", reason, httpRequest
        );

        return ApiResponse.success("로그가 기록되었습니다.");
    }

    /**
     * 권한 레벨 확인
     * GET /api/admin/level
     */
    @GetMapping("/level")
    public ApiResponse<AdminLevelResponse> getUserLevel(
            @RequestHeader("Authorization") String token) {

        String userId = jwtTokenProvider.getUserId(token.replace("Bearer ", ""));
        Integer level = adminService.getUserLevel(userId);
        String levelName = adminService.getUserLevelName(level);

        AdminLevelResponse response = AdminLevelResponse.builder()
                .userId(userId)
                .userLevel(level)
                .userLevelName(levelName)
                .isAdmin(level >= 90)
                .isSuperAdmin(level >= 99)
                .build();

        return ApiResponse.success(response);
    }
}
