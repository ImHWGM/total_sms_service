package kr.wisead.domain.survey.controller;

import jakarta.validation.Valid;
import java.util.List;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.survey.dto.*;
import kr.wisead.domain.survey.service.SurveyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 설문 참여 Controller (사용자용) */
@Slf4j
@RestController
@RequestMapping("/api/survey")
@RequiredArgsConstructor
public class SurveyController {

  private final SurveyService surveyService;

  /** 이벤트 코드로 설문 조회 */
  @GetMapping("/code/{eventCode}")
  public ApiResponse<EventResponse> getSurveyByEventCode(@PathVariable String eventCode) {
    EventResponse response = surveyService.getSurveyByEventCode(eventCode);
    return ApiResponse.success(response);
  }

  /** QR코드 URL로 설문 조회 */
  @GetMapping("/qr/{authCodeUrl}")
  public ApiResponse<EventResponse> getSurveyByAuthCodeUrl(@PathVariable String authCodeUrl) {
    EventResponse response = surveyService.getSurveyByAuthCodeUrl(authCodeUrl);
    return ApiResponse.success(response);
  }

  /** 사용자 키로 설문 조회 */
  @GetMapping("/key/{userKey}")
  public ApiResponse<EventResponse> getSurveyByUserKey(@PathVariable String userKey) {
    EventResponse response = surveyService.getSurveyByUserKey(userKey);
    return ApiResponse.success(response);
  }

  /** 범용인증 확인 */
  @PostMapping("/auth/general")
  public ApiResponse<SurveyUserResponse> checkGeneralAuth(
      @RequestParam String authCodeUrl, @RequestParam String authCode) {
    SurveyUserResponse response = surveyService.checkGeneralAuth(authCodeUrl, authCode);
    return ApiResponse.success(response);
  }

  /** 범용인증 확인 (eventCode 기반) */
  @PostMapping("/auth/general/code")
  public ApiResponse<SurveyUserResponse> checkGeneralAuthByEventCode(
      @RequestParam String eventCode, @RequestParam String authCode) {
    SurveyUserResponse response = surveyService.checkGeneralAuthByEventCode(eventCode, authCode);
    return ApiResponse.success(response);
  }

  /** 설문 제출 */
  @PostMapping("/{eventSeq}/submit")
  public ApiResponse<Void> submitSurvey(
      @PathVariable Integer eventSeq, @Valid @RequestBody SurveySubmitRequest request) {
    surveyService.submitSurvey(eventSeq, request);
    return ApiResponse.success(null);
  }

  /** 참여자 목록 조회 */
  @GetMapping("/{eventSeq}/participants")
  public ApiResponse<List<SurveyUserResponse>> getParticipants(@PathVariable Integer eventSeq) {
    List<SurveyUserResponse> response = surveyService.getParticipants(eventSeq);
    return ApiResponse.success(response);
  }

  /** 미참여/접속자 목록 조회 */
  @GetMapping("/{eventSeq}/absentees")
  public ApiResponse<List<SurveyUserResponse>> getAbsenteesAndLurkers(
      @PathVariable Integer eventSeq) {
    List<SurveyUserResponse> response = surveyService.getAbsenteesAndLurkers(eventSeq);
    return ApiResponse.success(response);
  }
}
