package kr.wisead.domain.message.controller;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.message.dto.*;
import kr.wisead.domain.message.service.MessageSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/** 메시지 발송 Controller */
@Slf4j
@RestController
@RequestMapping("/api/message/send")
@RequiredArgsConstructor
public class MessageSendController {

  private final MessageSendService messageSendService;

  /** 문자 발송 (SMS/LMS/MMS) */
  @PostMapping
  public ApiResponse<SmsSendResponse> send(
      @AuthenticationPrincipal UserDetails userDetails,
      @Valid @RequestBody SmsSendRequest request) {
    String regId = userDetails.getUsername();
    SmsSendResponse response = messageSendService.sendMessage(request, regId);
    return ApiResponse.success(response);
  }

  /** 발송 이력 조회 */
  @GetMapping("/history")
  public ApiResponse<PageResponse<MsgResultResponse>> getHistory(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime endDate,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false, defaultValue = "0") String sendFailure,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {

    // 기본값 설정 (최근 30일)
    if (startDate == null) {
      startDate = LocalDateTime.now().minusDays(30);
    }
    if (endDate == null) {
      endDate = LocalDateTime.now();
    }

    SendHistorySearchRequest searchRequest =
        SendHistorySearchRequest.builder()
            .regId(userDetails.getUsername())
            .srhDateStart(startDate)
            .srhDateEnd(endDate)
            .type(type)
            .keyword(keyword)
            .sendFailure(sendFailure)
            .pageNum(page)
            .amount(size)
            .build();

    PageResponse<MsgResultResponse> response = messageSendService.getSendHistory(searchRequest);
    return ApiResponse.success(response);
  }

  /** 예약 발송 대기 목록 조회 */
  @GetMapping("/pending")
  public ApiResponse<PageResponse<MsgQueueResponse>> getPendingMessages(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    String regId = userDetails.getUsername();
    PageResponse<MsgQueueResponse> response =
        messageSendService.getPendingMessages(regId, page, size);
    return ApiResponse.success(response);
  }

  /** 예약 발송 취소 (단건) */
  @DeleteMapping("/cancel/{mseq}")
  public ApiResponse<Integer> cancelMessage(
      @AuthenticationPrincipal UserDetails userDetails, @PathVariable Integer mseq) {
    String regId = userDetails.getUsername();
    int deleted = messageSendService.cancelScheduledMessage(mseq, regId);
    return ApiResponse.success(deleted);
  }

  /** 예약 발송 취소 (배치 전체) */
  @DeleteMapping("/cancel/batch/{userKey}")
  public ApiResponse<Integer> cancelBatch(
      @AuthenticationPrincipal UserDetails userDetails, @PathVariable String userKey) {
    String regId = userDetails.getUsername();
    int deleted = messageSendService.cancelScheduledBatch(userKey, regId);
    return ApiResponse.success(deleted);
  }

  /** 설문 문자 재발송 (단건) POST /api/message/send/resend */
  @PostMapping("/resend")
  public ApiResponse<Integer> resendSurveyMessage(
      @AuthenticationPrincipal UserDetails userDetails, @RequestBody ResendRequest request) {
    String regId = userDetails.getUsername();
    int mseq =
        messageSendService.resendSurveyMessage(
            request.getUserSeq(),
            request.getSubject(),
            request.getText(),
            request.getCallback(),
            request.isUseOriginalContent(),
            request.getReqType(),
            request.getReqDate(),
            request.getUseUrlYn(),
            regId);
    return ApiResponse.success(mseq);
  }

  /** 설문 문자 재발송 (다건) POST /api/message/send/resend/batch */
  @PostMapping("/resend/batch")
  public ApiResponse<ResendResponse> resendSurveyMessageBatch(
      @AuthenticationPrincipal UserDetails userDetails, @RequestBody ResendRequest request) {
    String regId = userDetails.getUsername();

    // duplicateReceivers가 있으면 대치문자 지원되는 resendToDuplicates 경로 사용
    if (request.getDuplicateReceivers() != null && !request.getDuplicateReceivers().isEmpty()) {
      ResendResponse response = messageSendService.resendToDuplicates(request, regId);
      return ApiResponse.success(response);
    }

    ResendResponse response =
        messageSendService.resendSurveyMessageBatch(
            request.getUserSeqList(),
            request.getSubject(),
            request.getText(),
            request.getCallback(),
            request.isUseOriginalContent(),
            request.getReqType(),
            request.getReqDate(),
            request.getUseUrlYn(),
            regId);
    return ApiResponse.success(response);
  }

  /**
   * 중복 번호 재발송 (이전 내용 재발송) POST /api/message/send/resend/duplicate
   *
   * <p>설문 발송 시 중복으로 실패한 번호들에게 이전 발송 내용을 그대로 재발송
   */
  @PostMapping("/resend/duplicate")
  public ApiResponse<ResendResponse> resendToDuplicates(
      @AuthenticationPrincipal UserDetails userDetails, @RequestBody ResendRequest request) {
    String regId = userDetails.getUsername();
    ResendResponse response = messageSendService.resendToDuplicates(request, regId);
    return ApiResponse.success(response);
  }

  /**
   * 중복 번호에 새 내용 발송 POST /api/message/send/duplicate/new
   *
   * <p>설문 발송 시 중복으로 실패한 번호들에게 새로운 내용으로 발송
   */
  @PostMapping("/duplicate/new")
  public ApiResponse<ResendResponse> sendNewToDuplicates(
      @AuthenticationPrincipal UserDetails userDetails, @RequestBody ResendRequest request) {
    String regId = userDetails.getUsername();
    ResendResponse response = messageSendService.sendNewToDuplicates(request, regId);
    return ApiResponse.success(response);
  }

  /**
   * 설문 문자 발송 POST /api/message/send/survey
   *
   * <p>설문 문자 발송 (단축 URL 자동 적용) - #유저키#, #userKey# 치환 - #대치문자1#, #대치문자2#, #대치문자3# 치환 - /auth/ 패턴 URL
   * 자동 단축
   */
  @PostMapping("/survey")
  public ApiResponse<SurveyMessageResponse> sendSurveyMessage(
      @AuthenticationPrincipal UserDetails userDetails,
      @Valid @RequestBody SurveyMessageRequest request) {
    String regId = userDetails.getUsername();
    SurveyMessageResponse response = messageSendService.sendSurveyMessages(request, regId);
    return ApiResponse.success(response);
  }

  /**
   * 행사참여자 문자 발송 POST /api/message/send/event
   *
   * <p>행사참여자 문자 발송 (LMS 전용, 단축 URL 자동 적용) - #이벤트명#, #이벤트기간#, #이벤트장소# 치환 - #이름#, #QR링크#, #접속링크# 치환 -
   * #대치문자1#, #대치문자2#, #대치문자3# 치환 - QR/접속 링크 자동 단축
   */
  @PostMapping("/event")
  public ApiResponse<EventMessageResponse> sendEventMessage(
      @AuthenticationPrincipal UserDetails userDetails,
      @Valid @RequestBody EventMessageRequest request) {
    String regId = userDetails.getUsername();
    EventMessageResponse response = messageSendService.sendEventMessages(request, regId);
    return ApiResponse.success(response);
  }
}
