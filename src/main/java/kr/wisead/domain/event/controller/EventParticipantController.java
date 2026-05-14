package kr.wisead.domain.event.controller;

import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import kr.wisead.common.dto.DownloadVerifyRequest;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.service.DownloadVerifyService;
import kr.wisead.domain.event.dto.*;
import kr.wisead.domain.event.service.*;
import kr.wisead.security.jwt.CurrentUser;
import kr.wisead.security.jwt.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 행사 참가자 관리 Controller */
@Slf4j
@RestController
@RequestMapping("/api/events/{eventSeq}/participants")
@RequiredArgsConstructor
public class EventParticipantController {

  private final EventParticipantService participantService;
  private final EventCheckService checkService;
  private final NametagService nametagService;
  private final EventActionTypeService actionTypeService;
  private final EventExcelService excelService;
  private final DownloadVerifyService downloadVerifyService;

  /** 참가자 등록 */
  @PostMapping
  public ApiResponse<EventParticipantResponse> createParticipant(
      @PathVariable Integer eventSeq,
      @Valid @RequestBody EventParticipantRequest request,
      @CurrentUser JwtPrincipal user) {
    request.setEventSeq(eventSeq);
    EventParticipantResponse response =
        participantService.createParticipant(request, user.userId());
    return ApiResponse.success(response, "참가자가 등록되었습니다.");
  }

  /** 참가자 엑셀 일괄 등록 */
  @PostMapping("/batch")
  public ApiResponse<Map<String, Object>> uploadParticipantExcel(
      @PathVariable Integer eventSeq,
      @RequestParam("file") MultipartFile file,
      @CurrentUser JwtPrincipal user) {
    Map<String, Object> result =
        participantService.uploadParticipantExcel(eventSeq, file, user.userId());
    return ApiResponse.success(result, "일괄 등록 완료");
  }

  /** 문자 발송용 참가자 전체 목록 조회 (페이징 없음) */
  @GetMapping("/for-message")
  public ApiResponse<List<ParticipantForMessageResponse>> getParticipantsForMessage(
      @PathVariable Integer eventSeq) {
    return ApiResponse.success(participantService.getParticipantsForMessage(eventSeq));
  }

  /** 참가자 목록 조회 */
  @GetMapping
  public ApiResponse<PageResponse<EventParticipantResponse>> getParticipants(
      @PathVariable Integer eventSeq,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) List<String> participantTypes,
      @RequestParam(required = false) List<String> excludeParticipantTypes,
      @RequestParam(required = false) String registType,
      @RequestParam(required = false) String attendStatus,
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "20") Integer size) {
    ParticipantSearchRequest request =
        ParticipantSearchRequest.builder()
            .eventSeq(eventSeq)
            .keyword(keyword)
            .participantTypes(participantTypes)
            .excludeParticipantTypes(excludeParticipantTypes)
            .registType(registType)
            .attendStatus(attendStatus)
            .page(page)
            .size(size)
            .build();
    return ApiResponse.success(participantService.getParticipants(request));
  }

  /** 참가자 상세 조회 */
  @GetMapping("/{seq}")
  public ApiResponse<EventParticipantResponse> getParticipant(
      @PathVariable Integer eventSeq, @PathVariable Long seq) {
    return ApiResponse.success(participantService.getParticipant(seq));
  }

  /** 참가자 상태 조회 (액션 현황 포함) */
  @GetMapping("/{seq}/status")
  public ApiResponse<ParticipantStatusResponse> getParticipantStatus(
      @PathVariable Integer eventSeq, @PathVariable Long seq) {
    return ApiResponse.success(participantService.getParticipantStatus(seq));
  }

  /** 참가자 수정 */
  @PutMapping("/{seq}")
  public ApiResponse<EventParticipantResponse> updateParticipant(
      @PathVariable Integer eventSeq,
      @PathVariable Long seq,
      @Valid @RequestBody EventParticipantRequest request,
      @CurrentUser JwtPrincipal user) {
    return ApiResponse.success(
        participantService.updateParticipant(seq, request, user.userId()),
        "참가자 정보가 수정되었습니다.");
  }

  /** 참가자 삭제 */
  @DeleteMapping("/{seq}")
  public ApiResponse<Void> deleteParticipant(
      @PathVariable Integer eventSeq,
      @PathVariable Long seq,
      @CurrentUser JwtPrincipal user) {
    participantService.deleteParticipant(seq, user.userId());
    return ApiResponse.success("참가자가 삭제되었습니다.");
  }

  /** 액션 처리 (관리자용) */
  @PostMapping("/action")
  public ApiResponse<EventCheckResponse> processAction(
      @PathVariable Integer eventSeq,
      @Valid @RequestBody EventCheckRequest request,
      @CurrentUser JwtPrincipal user) {
    EventCheckResponse response = checkService.processAction(eventSeq, request, user.userId());
    return ApiResponse.success(response, response.getMessage());
  }

  /** 명찰 데이터 조회 (미리보기/출력용) */
  @GetMapping("/{seq}/nametag")
  public ApiResponse<Map<String, Object>> getNametagData(
      @PathVariable Integer eventSeq, @PathVariable Long seq) {
    return ApiResponse.success(nametagService.getNametagData(seq));
  }

  /** 명찰 출력 로그 기록 */
  @PostMapping("/{seq}/nametag/print")
  public ApiResponse<Void> recordNametagPrint(
      @PathVariable Integer eventSeq,
      @PathVariable Long seq,
      @RequestBody NametagPrintRequest request,
      @CurrentUser JwtPrincipal user) {
    request.setParticipantSeq(seq);
    nametagService.recordPrint(request, user.userId());
    return ApiResponse.success("명찰 출력이 기록되었습니다.");
  }

  // ==================== 통계 및 엑셀 다운로드 ====================

  /** 행사 통계 조회 */
  @GetMapping("/statistics")
  public ApiResponse<EventStatisticsResponse> getStatistics(@PathVariable Integer eventSeq) {
    return ApiResponse.success(participantService.getStatistics(eventSeq));
  }

  /** 참가자 목록 엑셀 다운로드 */
  @PostMapping("/excel")
  public ResponseEntity<byte[]> downloadParticipantExcel(
      @PathVariable Integer eventSeq,
      @RequestBody @Valid DownloadVerifyRequest verifyRequest,
      @CurrentUser JwtPrincipal user) {

    // 비밀번호 검증
    downloadVerifyService.verify(user, verifyRequest.getPassword());

    byte[] excelData = excelService.createParticipantExcel(eventSeq);
    String fileName =
        "참가자목록_"
            + eventSeq
            + "_"
            + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            + ".xlsx";
    String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(excelData);
  }

  /** 통계 엑셀 다운로드 */
  @PostMapping("/statistics/excel")
  public ResponseEntity<byte[]> downloadStatisticsExcel(
      @PathVariable Integer eventSeq,
      @RequestBody @Valid DownloadVerifyRequest verifyRequest,
      @CurrentUser JwtPrincipal user) {

    // 비밀번호 검증
    downloadVerifyService.verify(user, verifyRequest.getPassword());

    byte[] excelData = excelService.createStatisticsExcel(eventSeq);
    String fileName =
        "행사통계_"
            + eventSeq
            + "_"
            + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            + ".xlsx";
    String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(excelData);
  }
}
