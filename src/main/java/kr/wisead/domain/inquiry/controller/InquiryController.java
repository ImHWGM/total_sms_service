package kr.wisead.domain.inquiry.controller;

import jakarta.validation.Valid;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.inquiry.dto.InquiryAnswerRequest;
import kr.wisead.domain.inquiry.dto.InquiryRequest;
import kr.wisead.domain.inquiry.dto.InquiryResponse;
import kr.wisead.domain.inquiry.service.InquiryService;
import kr.wisead.security.jwt.CurrentUser;
import kr.wisead.security.jwt.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 문의 Controller */
@Slf4j
@RestController
@RequestMapping("/api/inquiry")
@RequiredArgsConstructor
public class InquiryController {

  private final InquiryService inquiryService;

  /** 문의 등록 (비로그인 가능) POST /api/inquiry */
  @PostMapping
  public ApiResponse<InquiryResponse> submitInquiry(@Valid @RequestBody InquiryRequest request) {
    InquiryResponse response = inquiryService.submitInquiry(request);
    return ApiResponse.success(response);
  }

  /** 문의 목록 조회 (관리자용) GET /api/inquiry/list?status=PENDING&keyword=검색어&page=1&size=10 */
  @GetMapping("/list")
  public ApiResponse<PageResponse<InquiryResponse>> getInquiryList(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    PageResponse<InquiryResponse> response =
        inquiryService.getInquiryList(status, keyword, page, size);
    return ApiResponse.success(response);
  }

  /** 문의 상세 조회 GET /api/inquiry/{inquiryId} */
  @GetMapping("/{inquiryId}")
  public ApiResponse<InquiryResponse> getInquiry(@PathVariable Long inquiryId) {
    InquiryResponse response = inquiryService.getInquiry(inquiryId);
    return ApiResponse.success(response);
  }

  /** 답변 등록 (관리자용) POST /api/inquiry/{inquiryId}/answer */
  @PostMapping("/{inquiryId}/answer")
  public ApiResponse<InquiryResponse> answerInquiry(
      @CurrentUser JwtPrincipal user,
      @PathVariable Long inquiryId,
      @Valid @RequestBody InquiryAnswerRequest request) {
    InquiryResponse response = inquiryService.answerInquiry(inquiryId, request, user.userId());
    return ApiResponse.success(response);
  }

  /** 문의 상태 변경 (관리자용) PUT /api/inquiry/{inquiryId}/status */
  @PutMapping("/{inquiryId}/status")
  public ApiResponse<Void> updateStatus(@PathVariable Long inquiryId, @RequestParam String status) {
    inquiryService.updateStatus(inquiryId, status);
    return ApiResponse.success();
  }

  /** 문의 삭제 (관리자용) DELETE /api/inquiry/{inquiryId} */
  @DeleteMapping("/{inquiryId}")
  public ApiResponse<Void> deleteInquiry(@PathVariable Long inquiryId) {
    inquiryService.deleteInquiry(inquiryId);
    return ApiResponse.success();
  }

  /** 대기중 문의 개수 조회 (관리자 대시보드용) GET /api/inquiry/pending/count */
  @GetMapping("/pending/count")
  public ApiResponse<Integer> getPendingCount() {
    int count = inquiryService.getPendingCount();
    return ApiResponse.success(count);
  }
}
