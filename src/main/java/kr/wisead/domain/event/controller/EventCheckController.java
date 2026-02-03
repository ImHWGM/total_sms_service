package kr.wisead.domain.event.controller;

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

  /** QR 스캔으로 체크인 (참가자용) URL: /api/events/check/{checkCode} */
  @PostMapping("/check/{checkCode}")
  public ApiResponse<EventCheckResponse> checkIn(
      @PathVariable String checkCode,
      @RequestHeader(value = "X-Device-Info", required = false) String deviceInfo) {
    EventCheckResponse response = checkService.checkIn(checkCode, deviceInfo);
    return ApiResponse.success(response, response.getMessage());
  }

  /** QR 스캔으로 참가자 정보 조회 (체크인 전 확인용) */
  @GetMapping("/check/{checkCode}")
  public ApiResponse<ParticipantStatusResponse> getParticipantByCheckCode(
      @PathVariable String checkCode) {
    return ApiResponse.success(participantService.getParticipantStatusByCheckCode(checkCode));
  }

  /** QR 스캔으로 명찰 데이터 조회 (checkCode 기반) */
  @GetMapping("/check/{checkCode}/nametag")
  public ApiResponse<NametagResponse> getNametagByCheckCode(@PathVariable String checkCode) {
    return ApiResponse.success(nametagService.getNametagDataByCheckCode(checkCode));
  }

  /** QR 스캔으로 명찰 출력 로그 기록 (checkCode 기반) */
  @PostMapping("/check/{checkCode}/nametag/print")
  public ApiResponse<Void> recordNametagPrintByCheckCode(
      @PathVariable String checkCode,
      @RequestBody NametagPrintRequest request,
      @RequestHeader(value = "X-Device-Info", required = false) String deviceInfo) {
    nametagService.recordPrintByCheckCode(checkCode, request, deviceInfo);
    return ApiResponse.success("명찰 출력이 기록되었습니다.");
  }
}
