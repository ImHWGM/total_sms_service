package kr.wisead.domain.message.controller;

import jakarta.validation.Valid;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.message.dto.*;
import kr.wisead.domain.message.entity.MsgQueue;
import kr.wisead.domain.message.service.MessageSendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 메시지 발송 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/message/send")
@RequiredArgsConstructor
public class MessageSendController {

    private final MessageSendService messageSendService;

    /**
     * 문자 발송 (SMS/LMS/MMS)
     */
    @PostMapping
    public ApiResponse<SmsSendResponse> send(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SmsSendRequest request) {
        String regId = userDetails.getUsername();
        SmsSendResponse response = messageSendService.sendMessage(request, regId);
        return ApiResponse.success(response);
    }

    /**
     * 발송 이력 조회
     */
    @GetMapping("/history")
    public ApiResponse<PageResponse<MsgResultResponse>> getHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
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

        SendHistorySearchRequest searchRequest = SendHistorySearchRequest.builder()
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

    /**
     * 예약 발송 대기 목록 조회
     */
    @GetMapping("/pending")
    public ApiResponse<List<MsgQueue>> getPendingMessages(
            @AuthenticationPrincipal UserDetails userDetails) {
        String regId = userDetails.getUsername();
        List<MsgQueue> list = messageSendService.getPendingMessages(regId);
        return ApiResponse.success(list);
    }

    /**
     * 예약 발송 취소 (단건)
     */
    @DeleteMapping("/cancel/{mseq}")
    public ApiResponse<Integer> cancelMessage(@PathVariable Integer mseq) {
        int deleted = messageSendService.cancelScheduledMessage(mseq);
        return ApiResponse.success(deleted);
    }

    /**
     * 예약 발송 취소 (배치 전체)
     */
    @DeleteMapping("/cancel/batch/{userKey}")
    public ApiResponse<Integer> cancelBatch(@PathVariable String userKey) {
        int deleted = messageSendService.cancelScheduledBatch(userKey);
        return ApiResponse.success(deleted);
    }
}
