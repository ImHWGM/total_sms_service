package kr.wisead.domain.admin.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.admin.dto.*;
import kr.wisead.domain.admin.service.ActionLogService;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.excel.service.ExcelService;
import kr.wisead.domain.user.dto.UserResponse;
import kr.wisead.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** 관리자 Controller */
@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

  private static final String BEARER_PREFIX = "Bearer ";

  private final AdminService adminService;
  private final ActionLogService actionLogService;
  private final ExcelService excelService;
  private final JwtTokenProvider jwtTokenProvider;
  private final UserIdResolver userIdResolver;

  /** 관리자 계정 생성 POST /api/admin/account */
  @PostMapping("/account")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<UserResponse> createAdminAccount(
      @Valid @RequestBody AdminAccountRequest request,
      @RequestHeader("Authorization") String token) {

    String creatorId = jwtTokenProvider.getUserId(extractToken(token));
    UserResponse response = adminService.createAdminAccount(request, creatorId);
    return ApiResponse.success(response, "관리자 계정이 생성되었습니다.");
  }

  /** 액션 로그 목록 조회 GET /api/admin/logs */
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

    ActionLogSearchRequest request =
        ActionLogSearchRequest.builder()
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

  /** 액션 로그 상세 조회 GET /api/admin/logs/{seq} */
  @GetMapping("/logs/{seq}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<ActionLogResponse> getActionLog(@PathVariable Long seq) {
    ActionLogResponse response = actionLogService.getLog(seq);
    return ApiResponse.success(response);
  }

  /** 액션 로그 엑셀 다운로드 GET /api/admin/logs/download */
  @GetMapping("/logs/download")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<byte[]> downloadActionLogsExcel(
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String searchField,
      @RequestParam(required = false) String searchKeyword,
      @RequestParam(required = false) String actionType,
      @RequestHeader("Authorization") String token,
      HttpServletRequest httpRequest) {

    String accessToken = extractToken(token);
    String userId = userIdResolver.resolveUserId(jwtTokenProvider.getUserId(accessToken));
    String userName = CryptoUtils.decryptName(jwtTokenProvider.getUserName(accessToken));

    // 다운로드 로그 기록
    actionLogService.logDownloadAction(userId, userName, "로그관리 엑셀다운로드", "D", "업무용", httpRequest);

    ActionLogSearchRequest request =
        ActionLogSearchRequest.builder()
            .startDate(startDate)
            .endDate(endDate)
            .searchField(searchField)
            .searchKeyword(searchKeyword)
            .actionType(actionType)
            .page(1)
            .size(Integer.MAX_VALUE)
            .build();

    List<ActionLogResponse> logs = actionLogService.getLogsForExcel(request);

    try (SXSSFWorkbook workbook = excelService.createWorkbook()) {
      Sheet sheet = excelService.createSheet(workbook, "로그관리");

      CellStyle headerStyle = excelService.createHeaderStyle(workbook, 11, true, 192, 192, 192);

      List<String> headers =
          Arrays.asList("NO", "아이디", "기업명", "이름", "메뉴명", "메뉴URL", "Referer", "코드", "IP", "등록일시");
      excelService.createHeaderRow(sheet, 0, headers, headerStyle);

      int rowNum = 1;
      int totalCount = logs.size();
      DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

      for (ActionLogResponse logItem : logs) {
        excelService.createDataRow(
            sheet,
            rowNum++,
            Arrays.asList(
                totalCount--,
                logItem.getUserId(),
                logItem.getCorpName(), // 기업명 데이터 추가
                logItem.getUserName(),
                logItem.getMenuName(),
                logItem.getMenuUrl(),
                logItem.getReferer(),
                logItem.getCode(),
                logItem.getIp(),
                logItem.getRegDate() != null ? logItem.getRegDate().format(formatter) : ""),
            null);
      }

      byte[] content = excelService.toByteArray(workbook);

      String fileName =
          "로그관리_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";
      String encodedFileName =
          URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

      return ResponseEntity.ok()
          .header(
              HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
          .contentType(
              MediaType.parseMediaType(
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .body(content);

    } catch (Exception e) {
      log.error("액션 로그 엑셀 다운로드 실패", e);
      throw new RuntimeException("엑셀 파일 생성에 실패했습니다.");
    }
  }

  /** 전화번호 마스킹 해제 로그 기록 POST /api/admin/logs/phone-masking */
  @PostMapping("/logs/phone-masking")
  public ApiResponse<Void> logPhoneMaskingAction(
      @Valid @RequestBody PhoneMaskingLogRequest request,
      @RequestHeader("Authorization") String token,
      HttpServletRequest httpRequest) {

    String accessToken = extractToken(token);
    String userId = userIdResolver.resolveUserId(jwtTokenProvider.getUserId(accessToken));
    String userName = CryptoUtils.decryptName(jwtTokenProvider.getUserName(accessToken));

    try {
      actionLogService.logPhoneMasking(
          userId,
          userName,
          request.getAction(),
          request.getReason(),
          request.getPageNumber(),
          httpRequest);
      return ApiResponse.success("로그가 기록되었습니다.");
    } catch (Exception e) {
      log.error("마스킹 해제 로그 기록 실패", e);
      // 에러 로그도 기록
      actionLogService.logError(userId, userName, "마스킹해제 로그 기록 실패", e.getMessage(), httpRequest);
      return ApiResponse.error(ErrorCode.INTERNAL_ERROR, "로그 기록에 실패했습니다.");
    }
  }

  /** 다운로드 액션 로그 기록 POST /api/admin/logs/download */
  @PostMapping("/logs/download")
  public ApiResponse<Void> logDownloadAction(
      @RequestParam String menuName,
      @RequestParam String reason,
      @RequestHeader("Authorization") String token,
      HttpServletRequest httpRequest) {

    String accessToken = extractToken(token);
    String userId = userIdResolver.resolveUserId(jwtTokenProvider.getUserId(accessToken));
    String userName = CryptoUtils.decryptName(jwtTokenProvider.getUserName(accessToken));

    actionLogService.logDownloadAction(userId, userName, menuName, "D", reason, httpRequest);

    return ApiResponse.success("로그가 기록되었습니다.");
  }

  /** 권한 레벨 확인 GET /api/admin/level */
  @GetMapping("/level")
  public ApiResponse<AdminLevelResponse> getUserLevel(
      @RequestHeader("Authorization") String token) {

    String userSeq = jwtTokenProvider.getUserId(extractToken(token));
    String userId = userIdResolver.resolveUserId(userSeq);
    Integer level = adminService.getUserLevel(userSeq);
    String levelName = adminService.getUserLevelName(level);

    AdminLevelResponse response =
        AdminLevelResponse.builder()
            .userId(userId)
            .userLevel(level)
            .userLevelName(levelName)
            .isAdmin(level >= 90)
            .isSuperAdmin(level >= 99)
            .build();

    return ApiResponse.success(response);
  }

  /**
   * 권한별 선택 가능 사용자 아이디 목록 조회 GET /api/admin/selectable-user-ids
   *
   * <p>- 최고관리자 (레벨 90, 99): 전체 사용자 아이디 목록 - 운영관리자 (레벨 50, 60): 본인 + customer_company 테이블의 관리 계정 아이디
   * - 기업 (레벨 10): 본인 아이디만
   */
  @GetMapping("/selectable-user-ids")
  public ApiResponse<List<String>> getSelectableUserIds(
      @RequestHeader("Authorization") String token) {

    String accessToken = extractToken(token);
    String userSeq = jwtTokenProvider.getUserId(accessToken); // JWT에서 seq 추출

    // seq로 userId 조회 필요
    String userId = adminService.getUserIdBySeq(userSeq);
    Integer userLevel = adminService.getUserLevel(userSeq);

    List<String> userIds = adminService.getSelectableUserIds(userId, userLevel);

    return ApiResponse.success(userIds);
  }

  // ==================== Private Methods ====================

  /** Authorization 헤더에서 Bearer 토큰 추출 */
  private String extractToken(String authHeader) {
    return authHeader.replace(BEARER_PREFIX, "");
  }

}
