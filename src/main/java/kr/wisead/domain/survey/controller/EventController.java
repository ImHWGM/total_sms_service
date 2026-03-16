package kr.wisead.domain.survey.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.admin.service.ActionLogService;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.survey.dto.*;
import kr.wisead.domain.survey.service.EventService;
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

  /** 이벤트 목록 조회 */
  @GetMapping
  public ApiResponse<PageResponse<EventResponse>> getList(
      @AuthenticationPrincipal UserDetails userDetails,
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

    String userId = userDetails.getUsername();
    Integer userLevel = adminService.getUserLevel(userId);
    String actualRegId = userIdResolver.resolveUserId(userId);

    EventSearchRequest request =
        EventSearchRequest.builder()
            .regId(actualRegId)
            .userLevel(userLevel)
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
      @AuthenticationPrincipal UserDetails userDetails, @RequestBody @Valid EventRequest request) {
    String userId = userDetails.getUsername();
    EventResponse response = eventService.createEvent(userId, request, null, null, null, null);
    return ApiResponse.success(response);
  }

  /** 이벤트 수정 - JSON으로 이벤트 데이터 전송, 이미지는 /api/file/survey/* API로 별도 업로드 */
  @PutMapping("/{eventSeq}")
  public ApiResponse<EventResponse> update(
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable Integer eventSeq,
      @RequestBody @Valid EventRequest request) {
    String uptId = userDetails.getUsername();
    EventResponse response =
        eventService.updateEvent(eventSeq, request, uptId, null, null, null, null);
    return ApiResponse.success(response);
  }

  /** 이벤트 상태 변경 */
  @PatchMapping("/{eventSeq}/status")
  public ApiResponse<Void> updateStatus(
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable Integer eventSeq,
      @RequestParam String status) {
    String userId = userDetails.getUsername();
    eventService.updateEventStatus(eventSeq, status, userId);
    return ApiResponse.success(null);
  }

  /** 현장등록 QR 코드 생성 */
  @PostMapping("/{eventSeq}/onsite-qrcode")
  public ApiResponse<Map<String, String>> generateOnsiteQrCode(
      @AuthenticationPrincipal UserDetails userDetails, @PathVariable Integer eventSeq) {
    String userId = userDetails.getUsername();
    String qrCodeImgPath = eventService.generateOnsiteRegistrationQrCode(eventSeq, userId);
    return ApiResponse.success(Map.of("qrCodeImgPath", qrCodeImgPath), "QR 코드가 생성되었습니다.");
  }

  /** 행사 복사 */
  @PostMapping("/{eventSeq}/copy")
  public ApiResponse<EventResponse> copyEvent(
      @AuthenticationPrincipal UserDetails userDetails, @PathVariable Integer eventSeq) {
    String userId = userDetails.getUsername();
    EventResponse response = eventService.copyEvent(eventSeq, userId);
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
      @AuthenticationPrincipal UserDetails userDetails, @RequestParam String keyword) {
    String userId = userDetails.getUsername();
    Integer userLevel = adminService.getUserLevel(userId);
    String actualRegId = userIdResolver.resolveUserId(userId);

    EventSearchRequest request =
        EventSearchRequest.builder()
            .regId(actualRegId)
            .userLevel(userLevel)
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
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable Integer eventSeq,
      @Valid @RequestBody AuthKeyRequest request) {
    String regId = userDetails.getUsername();
    eventService.addAuthKey(eventSeq, request.getAuthCode(), regId);
    return ApiResponse.success(null);
  }

  /** 범용인증키 삭제 */
  @DeleteMapping("/{eventSeq}/auth-keys/{userKey}")
  public ApiResponse<Void> deleteAuthKey(
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable Integer eventSeq,
      @PathVariable String userKey) {
    String userId = userDetails.getUsername();
    eventService.deleteAuthKey(eventSeq, userKey, userId);
    return ApiResponse.success(null);
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
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable Integer eventSeq,
      @RequestBody Map<String, String> request) {
    String userId = userDetails.getUsername();
    String authKeyDesc = request.get("authKeyDesc");
    eventService.updateAuthKeyDesc(eventSeq, authKeyDesc, userId);
    return ApiResponse.success(null);
  }

  /** 유저키 생성 */
  @PostMapping("/{eventSeq}/user-keys")
  public ApiResponse<Map<String, Object>> generateUserKeys(
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable Integer eventSeq,
      @RequestParam int count) {
    String regId = userDetails.getUsername();
    List<String> userKeys = eventService.generateUserKeys(eventSeq, count, regId);
    return ApiResponse.success(Map.of("count", userKeys.size(), "userKeys", userKeys));
  }

  /** 범용인증코드 엑셀 업로드 POST /api/event/auth-keys/excel */
  @PostMapping(value = "/auth-keys/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<Map<String, Object>> uploadAuthKeyExcel(
      @AuthenticationPrincipal UserDetails userDetails, @RequestParam("file") MultipartFile file) {
    String regId = userDetails.getUsername();
    log.info("범용인증코드 엑셀 업로드 요청 - 파일명: {}, 사용자: {}", file.getOriginalFilename(), regId);
    Map<String, Object> result = eventService.uploadAuthKeyExcel(file, regId);
    return ApiResponse.success(result);
  }

  /** 이벤트 결과 엑셀 다운로드 POST /api/event/{eventSeq}/excel */
  @PostMapping("/{eventSeq}/excel")
  public ResponseEntity<byte[]> downloadEventResultExcel(
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable Integer eventSeq,
      @RequestBody(required = false) Map<String, String> body,
      HttpServletRequest httpRequest) {

    String userId = userIdResolver.resolveUserId(userDetails.getUsername());
    String userName = userIdResolver.resolveUserName(userId);

    log.info("이벤트 결과 엑셀 다운로드 요청 - eventSeq: {}, 사용자: {}", eventSeq, userId);

    // 활동 로그 기록
    String reason = body != null ? body.get("reason") : null;
    actionLogService.logDownloadAction(
        userId, userName, "이벤트 결과 엑셀다운로드", "R", reason, httpRequest);

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
