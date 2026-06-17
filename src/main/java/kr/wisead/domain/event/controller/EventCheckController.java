package kr.wisead.domain.event.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.event.dto.*;
import kr.wisead.domain.event.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 행사 체크인 Controller (참가자용 - 인증 불필요) */
@Slf4j
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventCheckController {

  private final EventCheckService checkService;
  private final EventParticipantService participantService;
  private final NametagService nametagService;

  /** QR 스캔으로 체크인 (참가자용) URL: /api/events/{eventSeq}/check/{checkCode} */
  @PostMapping("/{eventSeq}/check/{checkCode}")
  public ApiResponse<EventCheckResponse> checkIn(
      @PathVariable Integer eventSeq,
      @PathVariable String checkCode,
      @RequestHeader(value = "X-Device-Info", required = false) String deviceInfo) {
    EventCheckResponse response = checkService.checkIn(eventSeq, checkCode, deviceInfo);
    return ApiResponse.success(response, response.getMessage());
  }

  /** 전화번호로 체크인 (키오스크용) URL: /api/events/{eventSeq}/check-phone */
  @PostMapping("/{eventSeq}/check-phone")
  public ApiResponse<EventCheckResponse> checkInByPhone(
      @PathVariable Integer eventSeq,
      @Valid @RequestBody PhoneCheckRequest request,
      @RequestHeader(value = "X-Device-Info", required = false) String deviceInfo) {
    EventCheckResponse response =
        checkService.checkInByPhone(eventSeq, request.getPhone(), deviceInfo);
    return ApiResponse.success(response, response.getMessage());
  }

  /** QR 스캔으로 참가자 정보 조회 (체크인 전 확인용) */
  @GetMapping("/{eventSeq}/check/{checkCode}")
  public ApiResponse<ParticipantStatusResponse> getParticipantByCheckCode(
      @PathVariable Integer eventSeq,
      @PathVariable String checkCode,
      HttpServletRequest request) {
    return ApiResponse.success(
        participantService.getParticipantStatusByCheckCode(
            eventSeq, checkCode, false, request.getRemoteAddr()));
  }

  /** QR 스캔으로 명찰 데이터 조회 (checkCode 기반) */
  @GetMapping("/{eventSeq}/check/{checkCode}/nametag")
  public ApiResponse<NametagResponse> getNametagByCheckCode(
      @PathVariable Integer eventSeq,
      @PathVariable String checkCode,
      HttpServletRequest request) {
    return ApiResponse.success(
        nametagService.getNametagDataByCheckCode(eventSeq, checkCode, request.getRemoteAddr()));
  }

  /** QR 스캔으로 명찰 출력 로그 기록 (checkCode 기반) */
  @PostMapping("/{eventSeq}/check/{checkCode}/nametag/print")
  public ApiResponse<Void> recordNametagPrintByCheckCode(
      @PathVariable Integer eventSeq,
      @PathVariable String checkCode,
      @RequestBody NametagPrintRequest request,
      @RequestHeader(value = "X-Device-Info", required = false) String deviceInfo,
      HttpServletRequest httpRequest) {
    nametagService.recordPrintByCheckCode(
        eventSeq, checkCode, request, deviceInfo, httpRequest.getRemoteAddr());
    return ApiResponse.success("명찰 출력이 기록되었습니다.");
  }

  // ==================== 스태프 인증 + 체크인 (쿠키 기반) ====================

  /** 스태프 인증코드 검증 + 쿠키 발급 */
  @PostMapping("/{eventSeq}/staff-auth")
  public ApiResponse<Void> staffAuth(
      @PathVariable Integer eventSeq,
      @Valid @RequestBody StaffAuthRequest request,
      HttpServletResponse response) {
    String cookieValue = checkService.verifyAndGenerateCookie(eventSeq, request.getAuthCode());
    String cookiePath = "/api/events/" + eventSeq + "/";
    response.setHeader(
        "Set-Cookie",
        "staff_auth="
            + cookieValue
            + "; Max-Age=86400; HttpOnly; Secure; Path="
            + cookiePath
            + "; SameSite=None");
    return ApiResponse.success("인증 성공");
  }

  /** 스태프 QR 스캔으로 체크인 (쿠키 인증) */
  @PostMapping("/{eventSeq}/staff-checkin/{checkCode}")
  public ApiResponse<EventCheckResponse> staffCheckIn(
      @PathVariable Integer eventSeq,
      @PathVariable String checkCode,
      @CookieValue(name = "staff_auth", required = false) String staffAuth,
      @RequestHeader(value = "X-Device-Info", required = false) String deviceInfo) {
    checkService.validateStaffCookie(eventSeq, staffAuth);
    EventCheckResponse response = checkService.staffCheckIn(eventSeq, checkCode, deviceInfo);
    return ApiResponse.success(response, response.getMessage());
  }

  /** 스태프 QR 스캔으로 참가자 정보 조회 (쿠키 인증) */
  @GetMapping("/{eventSeq}/staff-checkin/{checkCode}")
  public ApiResponse<ParticipantStatusResponse> staffGetParticipant(
      @PathVariable Integer eventSeq,
      @PathVariable String checkCode,
      @CookieValue(name = "staff_auth", required = false) String staffAuth) {
    checkService.validateStaffCookie(eventSeq, staffAuth);
    return ApiResponse.success(
        participantService.getParticipantStatusByCheckCode(eventSeq, checkCode, true, null));
  }
}
