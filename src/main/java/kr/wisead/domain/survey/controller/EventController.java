package kr.wisead.domain.survey.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import kr.wisead.common.dto.DownloadVerifyRequest;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.service.DownloadVerifyService;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.admin.service.ActionLogService;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.survey.dto.*;
import kr.wisead.domain.survey.service.EventService;
import kr.wisead.security.jwt.CurrentUser;
import kr.wisead.security.jwt.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 이벤트/설문 관리 Controller (관리자용) */
@Slf4j
@RestController
@RequestMapping("/api/event")
@RequiredArgsConstructor
public class EventController {

  private final EventService eventService;
  private final AdminService adminService;
  private final UserIdResolver userIdResolver;
  private final ActionLogService actionLogService;
  private final DownloadVerifyService downloadVerifyService;

  /** 이벤트 목록 조회 */
  @GetMapping
  public ApiResponse<PageResponse<EventResponse>> getList(
      @CurrentUser JwtPrincipal user,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) List<String> eventTypes,
      @RequestParam(required = false) String surveyStatus,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String searchKeyword,
      @RequestParam(required = false) String sortField,
      @RequestParam(required = false, defaultValue = "desc") String sortOrder,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {

    String userId = user.userId();
    Integer userLevel = adminService.getUserLevel(userId);
    EventSearchRequest request =
        EventSearchRequest.builder()
            .regId(userId)
            .userLevel(userLevel)
            .queryUserIds(adminService.resolveQueryUserIds(userId, userLevel))
            .eventType(eventType)
            .eventTypes(eventTypes)
            .surveyStatus(surveyStatus)
            .startDate(startDate)
            .endDate(endDate)
            .searchKeyword(searchKeyword)
            .sortField(sortField)
            .sortOrder(sortOrder)
            .pageNum(page)
            .amount(size)
            .build();

    PageResponse<EventResponse> response = eventService.getEventList(request);
    return ApiResponse.success(response);
  }

  /** 이벤트 상세 조회 */
  @GetMapping("/{eventSeq}")
  public ApiResponse<EventResponse> getDetail(@PathVariable Integer eventSeq) {
    EventResponse response = eventService.getEventDetail(eventSeq);
    return ApiResponse.success(response);
  }

  /** 이벤트 생성 - JSON으로 이벤트 데이터 전송, 이미지는 /api/file/survey/* API로 별도 업로드 */
  @PostMapping
  public ApiResponse<EventResponse> create(
      @CurrentUser JwtPrincipal user, @RequestBody @Valid EventRequest request) {
    EventResponse response =
        eventService.createEvent(user.userId(), request, null, null, null, null);
    return ApiResponse.success(response);
  }

  /** 이벤트 수정 - JSON으로 이벤트 데이터 전송, 이미지는 /api/file/survey/* API로 별도 업로드 */
  @PutMapping("/{eventSeq}")
  public ApiResponse<EventResponse> update(
      @CurrentUser JwtPrincipal user,
      @PathVariable Integer eventSeq,
      @RequestBody @Valid EventRequest request) {
    EventResponse response =
        eventService.updateEvent(eventSeq, request, user.userId(), null, null, null, null);
    return ApiResponse.success(response);
  }

  /** 이벤트 상태 변경 */
  @PatchMapping("/{eventSeq}/status")
  public ApiResponse<Void> updateStatus(
      @CurrentUser JwtPrincipal user,
      @PathVariable Integer eventSeq,
      @RequestParam String status) {
    eventService.updateEventStatus(eventSeq, status, user.userId());
    return ApiResponse.success(null);
  }

  /** 현장등록 QR 코드 생성 */
  @PostMapping("/{eventSeq}/onsite-qrcode")
  public ApiResponse<Map<String, String>> generateOnsiteQrCode(
      @CurrentUser JwtPrincipal user, @PathVariable Integer eventSeq) {
    String qrCodeImgPath = eventService.generateOnsiteRegistrationQrCode(eventSeq, user.userId());
    return ApiResponse.success(Map.of("qrCodeImgPath", qrCodeImgPath), "QR 코드가 생성되었습니다.");
  }

  /** 행사 복사 */
  @PostMapping("/{eventSeq}/copy")
  public ApiResponse<EventResponse> copyEvent(
      @CurrentUser JwtPrincipal user, @PathVariable Integer eventSeq) {
    EventResponse response = eventService.copyEvent(eventSeq, user.userId());
    return ApiResponse.success(response, "행사가 복사되었습니다.");
  }

  /** 설문 통계 조회 */
  @GetMapping("/{eventSeq}/statistics")
  public ApiResponse<SurveyStatisticsResponse> getStatistics(@PathVariable Integer eventSeq) {
    SurveyStatisticsResponse response = eventService.getStatistics(eventSeq);
    return ApiResponse.success(response);
  }

  /** 이벤트명 검색 (자동완성) */
  @GetMapping("/search/names")
  public ApiResponse<List<String>> searchEventNames(
      @CurrentUser JwtPrincipal user, @RequestParam String keyword) {
    String userId = user.userId();
    Integer userLevel = adminService.getUserLevel(userId);
    EventSearchRequest request =
        EventSearchRequest.builder()
            .regId(userId)
            .userLevel(userLevel)
            .queryUserIds(adminService.resolveQueryUserIds(userId, userLevel))
            .searchKeyword(keyword)
            .build();
    List<String> names = eventService.searchEventNames(request);
    return ApiResponse.success(names);
  }

  /** 범용인증키 목록 조회 */
  @GetMapping("/{eventSeq}/auth-keys")
  public ApiResponse<PageResponse<Map<String, Object>>> getAuthKeyList(
      @PathVariable Integer eventSeq,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    PageResponse<Map<String, Object>> response = eventService.getAuthKeyList(eventSeq, page, size);
    return ApiResponse.success(response);
  }

  /** 범용인증키 추가 */
  @PostMapping("/{eventSeq}/auth-keys")
  public ApiResponse<Void> addAuthKey(
      @CurrentUser JwtPrincipal user,
      @PathVariable Integer eventSeq,
      @Valid @RequestBody AuthKeyRequest request) {
    eventService.addAuthKey(eventSeq, request.getAuthCode(), user.userId());
    return ApiResponse.success(null);
  }

  /** 범용인증키 삭제 */
  @DeleteMapping("/{eventSeq}/auth-keys/{userKey}")
  public ApiResponse<Void> deleteAuthKey(
      @CurrentUser JwtPrincipal user,
      @PathVariable Integer eventSeq,
      @PathVariable String userKey) {
    eventService.deleteAuthKey(eventSeq, userKey, user.userId());
    return ApiResponse.success(null);
  }

  /** 범용인증키 선택 삭제 (body: {userKeys: [...]}) */
  @DeleteMapping("/{eventSeq}/auth-keys")
  public ApiResponse<Map<String, Object>> deleteAuthKeys(
      @CurrentUser JwtPrincipal user,
      @PathVariable Integer eventSeq,
      @RequestBody Map<String, List<String>> request) {
    List<String> userKeys = request.get("userKeys");
    Map<String, Object> result = eventService.deleteAuthKeys(eventSeq, userKeys, user.userId());
    return ApiResponse.success(result);
  }

  /** 범용인증키 전체 삭제 */
  @DeleteMapping("/{eventSeq}/auth-keys/all")
  public ApiResponse<Map<String, Object>> deleteAllAuthKeys(
      @CurrentUser JwtPrincipal user, @PathVariable Integer eventSeq) {
    Map<String, Object> result = eventService.deleteAllAuthKeys(eventSeq, user.userId());
    return ApiResponse.success(result);
  }

  /** 범용인증키 설명문구 조회 */
  @GetMapping("/{eventSeq}/auth-key-desc")
  public ApiResponse<Map<String, String>> getAuthKeyDesc(@PathVariable Integer eventSeq) {
    String authKeyDesc = eventService.getAuthKeyDesc(eventSeq);
    return ApiResponse.success(Map.of("authKeyDesc", authKeyDesc != null ? authKeyDesc : ""));
  }

  /** 범용인증키 설명문구 수정 */
  @PutMapping("/{eventSeq}/auth-key-desc")
  public ApiResponse<Void> updateAuthKeyDesc(
      @CurrentUser JwtPrincipal user,
      @PathVariable Integer eventSeq,
      @RequestBody Map<String, String> request) {
    String authKeyDesc = request.get("authKeyDesc");
    eventService.updateAuthKeyDesc(eventSeq, authKeyDesc, user.userId());
    return ApiResponse.success(null);
  }

  /** 유저키 생성 */
  @PostMapping("/{eventSeq}/user-keys")
  public ApiResponse<Map<String, Object>> generateUserKeys(
      @CurrentUser JwtPrincipal user,
      @PathVariable Integer eventSeq,
      @RequestParam int count) {
    List<String> userKeys = eventService.generateUserKeys(eventSeq, count, user.userId());
    return ApiResponse.success(Map.of("count", userKeys.size(), "userKeys", userKeys));
  }

  /** 범용인증코드 엑셀 업로드 POST /api/event/auth-keys/excel */
  @PostMapping(value = "/auth-keys/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<Map<String, Object>> uploadAuthKeyExcel(
      @CurrentUser JwtPrincipal user, @RequestParam("file") MultipartFile file) {
    log.info("범용인증코드 엑셀 업로드 요청 - 파일명: {}, 사용자: {}", file.getOriginalFilename(), user.userId());
    Map<String, Object> result = eventService.uploadAuthKeyExcel(file, user.userId());
    return ApiResponse.success(result);
  }

  /** 이벤트 결과 엑셀 다운로드 POST /api/event/{eventSeq}/excel */
  @PostMapping("/{eventSeq}/excel")
  public ResponseEntity<byte[]> downloadEventResultExcel(
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable Integer eventSeq,
      @RequestBody @Valid DownloadVerifyRequest verifyRequest,
      HttpServletRequest httpRequest) {

    // 비밀번호 검증
    String userId = downloadVerifyService.verify(userDetails, verifyRequest.getPassword());
    String userName = userIdResolver.resolveUserName(userId);

    log.info("이벤트 결과 엑셀 다운로드 요청 - eventSeq: {}, 사용자: {}", eventSeq, userId);

    // 활동 로그 기록
    actionLogService.logDownloadAction(
        userId, userName, "이벤트 결과 엑셀다운로드", "R", verifyRequest.getReason(), httpRequest);

    byte[] content = eventService.generateEventResultExcel(eventSeq);

    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    String fileName = "이벤트_참여_관리_" + timestamp + ".xlsx";
    String encodedFileName =
        URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(content);
  }
}
