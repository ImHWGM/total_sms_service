package kr.wisead.domain.event.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.event.dto.RsvpSubmitRequest;
import kr.wisead.domain.event.dto.RsvpSubmitResponse;
import kr.wisead.domain.event.dto.UnifiedLinkStateResponse;
import kr.wisead.domain.event.service.EventParticipantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 행사 통합 링크 공개 API. checkCode capability 모델. FrontEventController(eventCode + phone)와 분리. */
@Slf4j
@RestController
@RequestMapping("/api/front/event/check")
@RequiredArgsConstructor
public class FrontEventCheckController {

  private final EventParticipantService participantService;

  /** 통합 링크 페이지 상태 조회. */
  @GetMapping("/{eventSeq}/{checkCode}/state")
  public ApiResponse<UnifiedLinkStateResponse> getState(
      @PathVariable Integer eventSeq,
      @PathVariable String checkCode,
      HttpServletRequest httpRequest) {
    // server.forward-headers-strategy=framework 설정으로 Spring이 XFF를 안전하게 처리.
    // getRemoteAddr()가 Nginx 등 프록시 너머 실제 클라이언트 IP를 반환한다.
    String clientIp = httpRequest.getRemoteAddr();
    UnifiedLinkStateResponse response =
        participantService.getUnifiedLinkState(eventSeq, checkCode, clientIp);
    return ApiResponse.success(response);
  }

  /** 통합 링크 RSVP 제출. */
  @PostMapping(value = "/{eventSeq}/{checkCode}/rsvp", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<RsvpSubmitResponse> submitRsvp(
      @PathVariable Integer eventSeq,
      @PathVariable String checkCode,
      @Valid @RequestBody RsvpSubmitRequest request,
      HttpServletRequest httpRequest) {
    String clientIp = httpRequest.getRemoteAddr();
    RsvpSubmitResponse response =
        participantService.submitRsvpByCheckCode(
            eventSeq, checkCode, request.getResponse(), request.getNonce(), clientIp);
    return ApiResponse.success(response);
  }
}
