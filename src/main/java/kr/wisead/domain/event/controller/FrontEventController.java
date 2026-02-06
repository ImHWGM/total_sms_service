package kr.wisead.domain.event.controller;

import jakarta.validation.Valid;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.event.dto.OnsiteRegistrationRequest;
import kr.wisead.domain.event.dto.OnsiteRegistrationResponse;
import kr.wisead.domain.event.dto.VerifyParticipantRequest;
import kr.wisead.domain.event.dto.VerifyParticipantResponse;
import kr.wisead.domain.event.service.EventParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 행사 공개 API Controller (인증 불필요 - /api/front/**) */
@Slf4j
@RestController
@RequestMapping("/api/front/event")
@RequiredArgsConstructor
public class FrontEventController {

  private final EventParticipantService participantService;

  /** 현장 참가자 등록 */
  @PostMapping("/{eventCode}/register-participant")
  public ApiResponse<OnsiteRegistrationResponse> registerOnsiteParticipant(
      @PathVariable String eventCode, @Valid @RequestBody OnsiteRegistrationRequest request) {
    OnsiteRegistrationResponse response =
        participantService.registerOnsiteParticipant(eventCode, request);
    return ApiResponse.success(response, "참가자 등록이 완료되었습니다.");
  }

  /** 참가자 인증 (이름 + 연락처로 QR코드 조회) */
  @PostMapping("/{eventCode}/verify-participant")
  public ApiResponse<VerifyParticipantResponse> verifyParticipant(
      @PathVariable String eventCode, @Valid @RequestBody VerifyParticipantRequest request) {
    VerifyParticipantResponse response = participantService.verifyParticipant(eventCode, request);
    return ApiResponse.success(response);
  }
}
