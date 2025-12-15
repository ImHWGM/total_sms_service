package kr.wisead.domain.message.controller;

import jakarta.validation.Valid;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.message.dto.MessageTemplateRequest;
import kr.wisead.domain.message.dto.MessageTemplateResponse;
import kr.wisead.domain.message.service.MessageTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 메시지 템플릿 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/message/template")
@RequiredArgsConstructor
public class MessageTemplateController {

    private final MessageTemplateService messageTemplateService;

    /**
     * 템플릿 생성
     */
    @PostMapping
    public ApiResponse<MessageTemplateResponse> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody MessageTemplateRequest request) {
        Long userSeq = getUserSeq(userDetails);
        MessageTemplateResponse response = messageTemplateService.create(userSeq, request);
        return ApiResponse.success(response);
    }

    /**
     * 템플릿 목록 조회
     */
    @GetMapping
    public ApiResponse<List<MessageTemplateResponse>> getList(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userSeq = getUserSeq(userDetails);
        List<MessageTemplateResponse> list = messageTemplateService.getList(userSeq);
        return ApiResponse.success(list);
    }

    /**
     * 템플릿 상세 조회
     */
    @GetMapping("/{templateSeq}")
    public ApiResponse<MessageTemplateResponse> getOne(
            @PathVariable Long templateSeq) {
        MessageTemplateResponse response = messageTemplateService.getOne(templateSeq);
        return ApiResponse.success(response);
    }

    /**
     * 템플릿 수정
     */
    @PutMapping("/{templateSeq}")
    public ApiResponse<MessageTemplateResponse> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long templateSeq,
            @Valid @RequestBody MessageTemplateRequest request) {
        Long userSeq = getUserSeq(userDetails);
        MessageTemplateResponse response = messageTemplateService.update(templateSeq, userSeq, request);
        return ApiResponse.success(response);
    }

    /**
     * 템플릿 삭제
     */
    @DeleteMapping("/{templateSeq}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long templateSeq) {
        Long userSeq = getUserSeq(userDetails);
        messageTemplateService.delete(templateSeq, userSeq);
        return ApiResponse.success(null);
    }

    /**
     * 템플릿 순서 변경
     */
    @PutMapping("/reorder")
    public ApiResponse<Void> reorder(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody List<Long> templateSeqList) {
        Long userSeq = getUserSeq(userDetails);
        messageTemplateService.reorder(userSeq, templateSeqList);
        return ApiResponse.success(null);
    }

    /**
     * UserDetails에서 userSeq 추출
     * TODO: 실제 구현 시 JWT 토큰에서 userSeq를 추출하도록 수정 필요
     */
    private Long getUserSeq(UserDetails userDetails) {
        // JWT 토큰의 username이 userSeq인 경우
        return Long.parseLong(userDetails.getUsername());
    }
}
