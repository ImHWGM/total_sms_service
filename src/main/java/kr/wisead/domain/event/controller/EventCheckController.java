package kr.wisead.domain.event.controller;

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
      @PathVariable Integer eventSeq, @PathVariable String checkCode) {
    return ApiResponse.success(
        participantService.getParticipantStatusByCheckCode(eventSeq, checkCode));
  }

  /** QR 스캔으로 명찰 데이터 조회 (checkCode 기반) */
  @GetMapping("/{eventSeq}/check/{checkCode}/nametag")
  public ApiResponse<NametagResponse> getNametagByCheckCode(
      @PathVariable Integer eventSeq, @PathVariable String checkCode) {
    return ApiResponse.success(nametagService.getNametagDataByCheckCode(eventSeq, checkCode));
  }

  /** QR 스캔으로 명찰 출력 로그 기록 (checkCode 기반) */
  @PostMapping("/{eventSeq}/check/{checkCode}/nametag/print")
  public ApiResponse<Void> recordNametagPrintByCheckCode(
      @PathVariable Integer eventSeq,
      @PathVariable String checkCode,
      @RequestBody NametagPrintRequest request,
      @RequestHeader(value = "X-Device-Info", required = false) String deviceInfo) {
    nametagService.recordPrintByCheckCode(eventSeq, checkCode, request, deviceInfo);
    return ApiResponse.success("명찰 출력이 기록되었습니다.");
  }
}
