package kr.wisead.domain.payment.controller;

import jakarta.validation.Valid;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.payment.dto.ChargeBonusEventRequest;
import kr.wisead.domain.payment.dto.ChargeBonusEventResponse;
import kr.wisead.domain.payment.service.ChargeBonusEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 충전 보너스 이벤트 관리 Controller (관리자용)
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/charge-bonus-event")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ChargeBonusEventController {

    private final ChargeBonusEventService chargeBonusEventService;

    /**
     * 이벤트 목록 조회
     * GET /api/admin/charge-bonus-event
     */
    @GetMapping
    public ApiResponse<List<ChargeBonusEventResponse>> getAllEvents() {
        List<ChargeBonusEventResponse> events = chargeBonusEventService.getAllEvents();
        return ApiResponse.success(events);
    }

    /**
     * 이벤트 상세 조회
     * GET /api/admin/charge-bonus-event/{eventSeq}
     */
    @GetMapping("/{eventSeq}")
    public ApiResponse<ChargeBonusEventResponse> getEvent(@PathVariable Long eventSeq) {
        ChargeBonusEventResponse event = chargeBonusEventService.getEvent(eventSeq);
        return ApiResponse.success(event);
    }

    /**
     * 이벤트 생성
     * POST /api/admin/charge-bonus-event
     */
    @PostMapping
    public ApiResponse<ChargeBonusEventResponse> createEvent(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChargeBonusEventRequest request) {
        String createdBy = userDetails.getUsername();
        ChargeBonusEventResponse event = chargeBonusEventService.createEvent(request, createdBy);
        return ApiResponse.success(event);
    }

    /**
     * 이벤트 수정
     * PUT /api/admin/charge-bonus-event/{eventSeq}
     */
    @PutMapping("/{eventSeq}")
    public ApiResponse<ChargeBonusEventResponse> updateEvent(
            @PathVariable Long eventSeq,
            @Valid @RequestBody ChargeBonusEventRequest request) {
        ChargeBonusEventResponse event = chargeBonusEventService.updateEvent(eventSeq, request);
        return ApiResponse.success(event);
    }

    /**
     * 이벤트 상태 변경 (활성화/비활성화)
     * PATCH /api/admin/charge-bonus-event/{eventSeq}/status
     */
    @PatchMapping("/{eventSeq}/status")
    public ApiResponse<Void> updateEventStatus(
            @PathVariable Long eventSeq,
            @RequestParam String status) {
        chargeBonusEventService.updateEventStatus(eventSeq, status);
        return ApiResponse.success(null);
    }

    /**
     * 이벤트 삭제
     * DELETE /api/admin/charge-bonus-event/{eventSeq}
     */
    @DeleteMapping("/{eventSeq}")
    public ApiResponse<Void> deleteEvent(@PathVariable Long eventSeq) {
        chargeBonusEventService.deleteEvent(eventSeq);
        return ApiResponse.success(null);
    }

    /**
     * 현재 활성 이벤트 목록 조회 (충전 금액 기준)
     * GET /api/admin/charge-bonus-event/active?chargeAmount=50000
     */
    @GetMapping("/active")
    public ApiResponse<List<ChargeBonusEventResponse>> getActiveEvents(
            @RequestParam(required = false, defaultValue = "0") BigDecimal chargeAmount) {
        List<ChargeBonusEventResponse> events = chargeBonusEventService.getActiveEvents(chargeAmount);
        return ApiResponse.success(events);
    }
}
