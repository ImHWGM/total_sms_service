package kr.wisead.domain.survey.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.survey.dto.SurveyUserRequest;
import kr.wisead.domain.survey.dto.SurveyUserResponse;
import kr.wisead.domain.survey.service.SurveyUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/** 설문 참여자 관리 Controller (관리자용) */
@Slf4j
@RestController
@RequestMapping("/api/survey/users")
@RequiredArgsConstructor
public class SurveyUserController {

  private final SurveyUserService surveyUserService;
  private final AdminService adminService;
  private final UserIdResolver userIdResolver;

  /**
   * 참여자 목록 조회 (검색 + 페이징) GET
   * /api/survey/users?eventType=P&searchType=winnerName&keyword=홍길동&startDate=2025-01-01&endDate=2025-01-31&status=COMPLETED&page=1&size=10
   *
   * @param eventSeq 이벤트 시퀀스
   * @param eventType P(개인정보취합), S(설문조사)
   * @param searchType 검색 조건 (아래 참조)
   * @param keyword 검색어
   * @param startDate 시작일 (yyyy-MM-dd)
   * @param endDate 종료일 (yyyy-MM-dd)
   * @param status 제출 상태 (COMPLETED: 제출완료, IN_PROGRESS: 미제출)
   * @param page 페이지 번호
   * @param size 페이지 크기
   *     <p>개인정보취합(P) searchType: - number: 번호(SEQ) - customerName: 고객사명 - eventName: 이벤트명 -
   *     winnerName: 당첨자명 (암호화) - phoneNumber: 전화번호 (암호화) - rrn: 주민등록번호 (암호화) - address: 주소 (암호화) -
   *     depositDate: 입금일자 - shipmentDate: 출고일자
   *     <p>설문조사(S) searchType: - number: 번호(SEQ) - customerName: 고객사명 - eventName: 이벤트명 -
   *     phoneNumber: 전화번호 (암호화) - userKey: 난수 - lastAccessDate: 최종접속일 - completionDate: 최종완료일
   */
  @GetMapping
  public ApiResponse<PageResponse<SurveyUserResponse>> searchUsers(
      @RequestParam(required = false) Integer eventSeq,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String searchType,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(defaultValue = "true") boolean masked,
      @RequestParam(required = false) String reason,
      @AuthenticationPrincipal UserDetails userDetails,
      HttpServletRequest httpRequest) {

    String rawUserId = userDetails != null ? userDetails.getUsername() : "ANONYMOUS";
    String userId = userIdResolver.resolveUserId(rawUserId);
    Integer userLevel = adminService.getUserLevel(rawUserId);
    List<String> queryUserIds = adminService.resolveQueryUserIds(userId, userLevel);

    log.info(
        "[발송조회 검색] userId={}, eventType={}, searchType={}, keyword={}, startDate={}, endDate={},"
            + " status={}, masked={}",
        userId,
        eventType,
        searchType,
        keyword,
        startDate,
        endDate,
        status,
        masked);

    PageResponse<SurveyUserResponse> response =
        surveyUserService.searchUsers(
            eventSeq,
            eventType,
            searchType,
            keyword,
            startDate,
            endDate,
            status,
            page,
            size,
            masked,
            reason,
            userId,
            userLevel,
            queryUserIds,
            httpRequest);
    return ApiResponse.success(response);
  }

  /** 설문 완료자 목록 조회 */
  @GetMapping("/completed")
  public ApiResponse<List<SurveyUserResponse>> getCompletedUsers(@RequestParam Integer eventSeq) {
    List<SurveyUserResponse> response = surveyUserService.getCompletedUsers(eventSeq);
    return ApiResponse.success(response);
  }

  /** 미참여/접속자 목록 조회 */
  @GetMapping("/absentees")
  public ApiResponse<List<SurveyUserResponse>> getAbsenteesAndLurkers(
      @RequestParam Integer eventSeq) {
    List<SurveyUserResponse> response = surveyUserService.getAbsenteesAndLurkers(eventSeq);
    return ApiResponse.success(response);
  }

  /** 참여자 상세 조회 (시퀀스) */
  @GetMapping("/{userSeq}")
  public ApiResponse<SurveyUserResponse> getUserBySeq(@PathVariable Integer userSeq) {
    SurveyUserResponse response = surveyUserService.getUserBySeq(userSeq);
    return ApiResponse.success(response);
  }

  /** 참여자 상세 조회 (사용자 키) */
  @GetMapping("/key/{userKey}")
  public ApiResponse<SurveyUserResponse> getUserByUserKey(@PathVariable String userKey) {
    SurveyUserResponse response = surveyUserService.getUserByUserKey(userKey);
    return ApiResponse.success(response);
  }

  /** 이벤트별 참여자 수 통계 */
  @GetMapping("/count")
  public ApiResponse<Map<String, Integer>> getParticipantCounts(@RequestParam Integer eventSeq) {
    Map<String, Integer> counts =
        Map.of(
            "total", surveyUserService.countByEventSeq(eventSeq),
            "completed", surveyUserService.countCompletedByEventSeq(eventSeq),
            "absentees", surveyUserService.countAbsenteesByEventSeq(eventSeq),
            "lurkers", surveyUserService.countLurkersByEventSeq(eventSeq));
    return ApiResponse.success(counts);
  }

  /** 참여자 등록 */
  @PostMapping
  public ApiResponse<SurveyUserResponse> createUser(
      @Valid @RequestBody SurveyUserRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    String regId = userDetails != null ? userDetails.getUsername() : "SYSTEM";
    SurveyUserResponse response = surveyUserService.createUser(request, regId);
    return ApiResponse.success(response);
  }

  /** 참여자 일괄 등록 */
  @PostMapping("/batch")
  public ApiResponse<Integer> createUsersBatch(
      @RequestParam Integer eventSeq,
      @Valid @RequestBody List<SurveyUserRequest> requests,
      @AuthenticationPrincipal UserDetails userDetails) {
    String regId = userDetails != null ? userDetails.getUsername() : "SYSTEM";
    int result = surveyUserService.createUsersBatch(eventSeq, requests, regId);
    return ApiResponse.success(result);
  }

  /** 참여자 정보 수정 */
  @PutMapping("/{userSeq}")
  public ApiResponse<SurveyUserResponse> updateUser(
      @PathVariable Integer userSeq,
      @Valid @RequestBody SurveyUserRequest request,
      @AuthenticationPrincipal UserDetails userDetails) {
    String uptId = userDetails != null ? userDetails.getUsername() : "SYSTEM";
    SurveyUserResponse response = surveyUserService.updateUser(userSeq, request, uptId);
    return ApiResponse.success(response);
  }

  /** 재발송 전화번호 수정 */
  @PatchMapping("/{userSeq}/resend-phone")
  public ApiResponse<Void> updateResendPhone(
      @PathVariable Integer userSeq,
      @RequestParam String phone,
      @AuthenticationPrincipal UserDetails userDetails) {
    String uptId = userDetails != null ? userDetails.getUsername() : "SYSTEM";
    surveyUserService.updateResendPhone(userSeq, phone, uptId);
    return ApiResponse.success(null);
  }

  /** 입금/출고 정보 수정 */
  @PatchMapping("/{userSeq}/payment-info")
  public ApiResponse<Void> updatePaymentInfo(
      @PathVariable Integer userSeq,
      @RequestParam(required = false) LocalDate depositDate,
      @RequestParam(required = false) LocalDate shipmentDate,
      @AuthenticationPrincipal UserDetails userDetails) {
    String uptId = userDetails != null ? userDetails.getUsername() : "SYSTEM";
    surveyUserService.updatePaymentInfo(userSeq, depositDate, shipmentDate, uptId);
    return ApiResponse.success(null);
  }

  /** 참여자 삭제 */
  @DeleteMapping("/{userSeq}")
  public ApiResponse<Void> deleteUser(
      @PathVariable Integer userSeq, @AuthenticationPrincipal UserDetails userDetails) {
    String uptId = userDetails != null ? userDetails.getUsername() : "SYSTEM";
    surveyUserService.deleteUser(userSeq, uptId);
    return ApiResponse.success(null);
  }

  /** 이벤트의 모든 참여자 삭제 */
  @DeleteMapping("/event/{eventSeq}")
  public ApiResponse<Void> deleteUsersByEventSeq(
      @PathVariable Integer eventSeq, @AuthenticationPrincipal UserDetails userDetails) {
    String uptId = userDetails != null ? userDetails.getUsername() : "SYSTEM";
    surveyUserService.deleteUsersByEventSeq(eventSeq, uptId);
    return ApiResponse.success(null);
  }

  /** 범용인증 상태 확인 */
  @GetMapping("/auth/general/status")
  public ApiResponse<Map<String, Object>> checkGeneralAuthStatus(
      @RequestParam String authCodeUrl, @RequestParam String generalAuthCode) {
    Map<String, Object> status =
        surveyUserService.checkGeneralAuthStatus(authCodeUrl, generalAuthCode);
    return ApiResponse.success(status);
  }

  /** 범용인증으로 사용자 조회 */
  @GetMapping("/auth/general")
  public ApiResponse<SurveyUserResponse> getUserByGeneralAuthCode(
      @RequestParam String authCodeUrl, @RequestParam String generalAuthCode) {
    SurveyUserResponse response =
        surveyUserService.getUserByGeneralAuthCode(authCodeUrl, generalAuthCode);
    return ApiResponse.success(response);
  }

  /** 전화번호 중복 확인 */
  @GetMapping("/check-phone")
  public ApiResponse<Boolean> existsByEventCodeAndPhone(
      @RequestParam String eventCode, @RequestParam String phone) {
    boolean exists = surveyUserService.existsByEventCodeAndPhone(eventCode, phone);
    return ApiResponse.success(exists);
  }

  /** 사용자 키 유효성 검증 */
  @GetMapping("/validate-key")
  public ApiResponse<Boolean> validateUserKey(
      @RequestParam Integer eventSeq, @RequestParam String userKey) {
    boolean valid = surveyUserService.validateUserKey(eventSeq, userKey);
    return ApiResponse.success(valid);
  }

  /** 설문 접속 시간 기록 */
  @PostMapping("/start-time")
  public ApiResponse<Void> recordStartTime(@RequestParam String userKey) {
    surveyUserService.recordStartTime(userKey);
    return ApiResponse.success(null);
  }

  /** 설문 인증 시간 기록 */
  @PostMapping("/auth-time")
  public ApiResponse<Void> recordAuthTime(@RequestParam String userKey) {
    surveyUserService.recordAuthTime(userKey);
    return ApiResponse.success(null);
  }
}
