package kr.wisead.domain.survey.controller;

import jakarta.validation.Valid;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.survey.dto.*;
import kr.wisead.domain.survey.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 이벤트/설문 관리 Controller (관리자용)
 */
@Slf4j
@RestController
@RequestMapping("/api/event")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /**
     * 이벤트 목록 조회
     */
    @GetMapping
    public ApiResponse<PageResponse<EventResponse>> getList(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String surveyStatus,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String searchKeyword,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        EventSearchRequest request = EventSearchRequest.builder()
                .regId(userDetails.getUsername())
                .eventType(eventType)
                .surveyStatus(surveyStatus)
                .startDate(startDate)
                .endDate(endDate)
                .searchKeyword(searchKeyword)
                .sortField(sortField)
                .sortOrder(sortOrder)
                .pageNum(page)
                .amount(size)
                .build();

        PageResponse<EventResponse> response = eventService.getEventList(request);
        return ApiResponse.success(response);
    }

    /**
     * 이벤트 상세 조회
     */
    @GetMapping("/{eventSeq}")
    public ApiResponse<EventResponse> getDetail(@PathVariable Integer eventSeq) {
        EventResponse response = eventService.getEventDetail(eventSeq);
        return ApiResponse.success(response);
    }

    /**
     * 이벤트 생성
     */
    @PostMapping
    public ApiResponse<EventResponse> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody EventRequest request) {
        Integer userSeq = Integer.parseInt(userDetails.getUsername());
        String regId = userDetails.getUsername();
        EventResponse response = eventService.createEvent(userSeq, request, regId);
        return ApiResponse.success(response);
    }

    /**
     * 이벤트 수정
     */
    @PutMapping("/{eventSeq}")
    public ApiResponse<EventResponse> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer eventSeq,
            @Valid @RequestBody EventRequest request) {
        String uptId = userDetails.getUsername();
        EventResponse response = eventService.updateEvent(eventSeq, request, uptId);
        return ApiResponse.success(response);
    }

    /**
     * 이벤트 상태 변경
     */
    @PatchMapping("/{eventSeq}/status")
    public ApiResponse<Void> updateStatus(
            @PathVariable Integer eventSeq,
            @RequestParam String status) {
        eventService.updateEventStatus(eventSeq, status);
        return ApiResponse.success(null);
    }

    /**
     * 설문 통계 조회
     */
    @GetMapping("/{eventSeq}/statistics")
    public ApiResponse<SurveyStatisticsResponse> getStatistics(@PathVariable Integer eventSeq) {
        SurveyStatisticsResponse response = eventService.getStatistics(eventSeq);
        return ApiResponse.success(response);
    }

    /**
     * 이벤트명 검색 (자동완성)
     */
    @GetMapping("/search/names")
    public ApiResponse<List<String>> searchEventNames(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String keyword) {
        EventSearchRequest request = EventSearchRequest.builder()
                .regId(userDetails.getUsername())
                .searchKeyword(keyword)
                .build();
        List<String> names = eventService.searchEventNames(request);
        return ApiResponse.success(names);
    }

    /**
     * 범용인증키 목록 조회
     */
    @GetMapping("/{eventSeq}/auth-keys")
    public ApiResponse<PageResponse<Map<String, Object>>> getAuthKeyList(
            @PathVariable Integer eventSeq,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<Map<String, Object>> response = eventService.getAuthKeyList(eventSeq, page, size);
        return ApiResponse.success(response);
    }

    /**
     * 범용인증키 추가
     */
    @PostMapping("/{eventSeq}/auth-keys")
    public ApiResponse<Void> addAuthKey(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Integer eventSeq,
            @Valid @RequestBody AuthKeyRequest request) {
        String regId = userDetails.getUsername();
        eventService.addAuthKey(eventSeq, request.getAuthCode(), regId);
        return ApiResponse.success(null);
    }

    /**
     * 범용인증키 삭제
     */
    @DeleteMapping("/{eventSeq}/auth-keys/{userKey}")
    public ApiResponse<Void> deleteAuthKey(
            @PathVariable Integer eventSeq,
            @PathVariable String userKey) {
        eventService.deleteAuthKey(eventSeq, userKey);
        return ApiResponse.success(null);
    }
}
