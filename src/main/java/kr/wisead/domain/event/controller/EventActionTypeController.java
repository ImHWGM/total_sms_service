package kr.wisead.domain.event.controller;

import jakarta.validation.Valid;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.event.dto.*;
import kr.wisead.domain.event.service.EventActionTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 행사 액션 유형 관리 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/events/{eventSeq}/action-types")
@RequiredArgsConstructor
public class EventActionTypeController {

    private final EventActionTypeService actionTypeService;

    /**
     * 액션 유형 목록 조회
     */
    @GetMapping
    public ApiResponse<List<EventActionTypeResponse>> getActionTypes(@PathVariable Integer eventSeq) {
        return ApiResponse.success(actionTypeService.getActionTypes(eventSeq));
    }

    /**
     * 액션 유형 등록
     */
    @PostMapping
    public ApiResponse<EventActionTypeResponse> createActionType(
            @PathVariable Integer eventSeq,
            @Valid @RequestBody EventActionTypeRequest request) {
        request.setEventSeq(eventSeq);
        return ApiResponse.success(actionTypeService.createActionType(request), "액션 유형이 등록되었습니다.");
    }

    /**
     * 액션 유형 수정
     */
    @PutMapping("/{seq}")
    public ApiResponse<EventActionTypeResponse> updateActionType(
            @PathVariable Integer eventSeq,
            @PathVariable Long seq,
            @Valid @RequestBody EventActionTypeRequest request) {
        return ApiResponse.success(actionTypeService.updateActionType(seq, request), "액션 유형이 수정되었습니다.");
    }

    /**
     * 액션 유형 삭제
     */
    @DeleteMapping("/{seq}")
    public ApiResponse<Void> deleteActionType(
            @PathVariable Integer eventSeq,
            @PathVariable Long seq) {
        actionTypeService.deleteActionType(seq);
        return ApiResponse.success("액션 유형이 삭제되었습니다.");
    }
}
