package kr.wisead.domain.message.controller;

import jakarta.validation.Valid;
import java.util.List;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.file.service.FileStorageService;
import kr.wisead.domain.message.dto.MessageTemplateRequest;
import kr.wisead.domain.message.dto.MessageTemplateResponse;
import kr.wisead.domain.message.service.MessageTemplateService;
import kr.wisead.mapper.primary.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 메시지 템플릿 Controller */
@Slf4j
@RestController
@RequestMapping("/api/message/template")
@RequiredArgsConstructor
public class MessageTemplateController {

  private final MessageTemplateService messageTemplateService;
  private final FileStorageService fileStorageService;
  private final UserMapper userMapper;

  /** 템플릿 생성 */
  @PostMapping
  public ApiResponse<MessageTemplateResponse> create(
      @AuthenticationPrincipal UserDetails userDetails,
      @Valid @RequestBody MessageTemplateRequest request) {
    Integer userSeq = getUserSeq(userDetails);
    MessageTemplateResponse response = messageTemplateService.create(userSeq, request);
    return ApiResponse.success(response);
  }

  /** MMS 템플릿 생성 (이미지 포함) */
  @PostMapping("/with-image")
  public ApiResponse<MessageTemplateResponse> createWithImage(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam("msgType") String msgType,
      @RequestParam(value = "subject", required = false) String subject,
      @RequestParam("text") String text,
      @RequestParam(value = "sendingForm", required = false, defaultValue = "d") String sendingForm,
      @RequestParam(value = "image", required = false) MultipartFile image) {

    Integer userSeq = getUserSeq(userDetails);
    String imagePath = null;

    // MMS이고 이미지가 있을 때만 처리
    if ("MMS".equals(msgType) && image != null && !image.isEmpty()) {
      imagePath = fileStorageService.storeTemplateImage(image, userSeq);
    }

    MessageTemplateRequest request =
        MessageTemplateRequest.builder()
            .msgType(msgType)
            .subject(subject)
            .text(text)
            .sendingForm(sendingForm)
            .imagePath(imagePath)
            .build();

    MessageTemplateResponse response = messageTemplateService.create(userSeq, request);
    return ApiResponse.success(response);
  }

  /**
   * 템플릿 목록 조회
   *
   * @param type 발송 형태 (s: 설문용, d: 직접발송용) - 없으면 전체 조회
   */
  @GetMapping
  public ApiResponse<List<MessageTemplateResponse>> getList(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(required = false) String type) {
    Integer userSeq = getUserSeq(userDetails);
    List<MessageTemplateResponse> list;
    if (type != null && !type.isEmpty()) {
      list = messageTemplateService.getListBySendingForm(userSeq, type);
    } else {
      list = messageTemplateService.getList(userSeq);
    }
    return ApiResponse.success(list);
  }

  /** 설문용 템플릿 목록 조회 */
  @GetMapping("/survey")
  public ApiResponse<List<MessageTemplateResponse>> getSurveyList(
      @AuthenticationPrincipal UserDetails userDetails) {
    Integer userSeq = getUserSeq(userDetails);
    List<MessageTemplateResponse> list = messageTemplateService.getListBySendingForm(userSeq, "s");
    return ApiResponse.success(list);
  }

  /** 직접발송용 템플릿 목록 조회 */
  @GetMapping("/direct")
  public ApiResponse<List<MessageTemplateResponse>> getDirectList(
      @AuthenticationPrincipal UserDetails userDetails) {
    Integer userSeq = getUserSeq(userDetails);
    List<MessageTemplateResponse> list = messageTemplateService.getListBySendingForm(userSeq, "d");
    return ApiResponse.success(list);
  }

  /** 템플릿 상세 조회 (소유자 검증 포함) */
  @GetMapping("/{templateSeq}")
  public ApiResponse<MessageTemplateResponse> getOne(
      @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long templateSeq) {
    Integer userSeq = getUserSeq(userDetails);
    MessageTemplateResponse response = messageTemplateService.getOne(templateSeq, userSeq);
    return ApiResponse.success(response);
  }

  /** 템플릿 수정 */
  @PutMapping("/{templateSeq}")
  public ApiResponse<MessageTemplateResponse> update(
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable Long templateSeq,
      @Valid @RequestBody MessageTemplateRequest request) {
    Integer userSeq = getUserSeq(userDetails);
    MessageTemplateResponse response = messageTemplateService.update(templateSeq, userSeq, request);
    return ApiResponse.success(response);
  }

  /** 템플릿 삭제 */
  @DeleteMapping("/{templateSeq}")
  public ApiResponse<Void> delete(
      @AuthenticationPrincipal UserDetails userDetails, @PathVariable Long templateSeq) {
    Integer userSeq = getUserSeq(userDetails);
    messageTemplateService.delete(templateSeq, userSeq);
    return ApiResponse.success(null);
  }

  /** 템플릿 순서 변경 */
  @PutMapping("/reorder")
  public ApiResponse<Void> reorder(
      @AuthenticationPrincipal UserDetails userDetails, @RequestBody List<Long> templateSeqList) {
    Integer userSeq = getUserSeq(userDetails);
    messageTemplateService.reorder(userSeq, templateSeqList);
    return ApiResponse.success(null);
  }

  /** 설문용 템플릿 순서 변경 */
  @PutMapping("/survey/reorder")
  public ApiResponse<Void> reorderSurvey(
      @AuthenticationPrincipal UserDetails userDetails, @RequestBody List<Long> templateSeqList) {
    Integer userSeq = getUserSeq(userDetails);
    messageTemplateService.reorderBySendingForm(userSeq, templateSeqList, "s");
    return ApiResponse.success(null);
  }

  /** 직접발송용 템플릿 순서 변경 */
  @PutMapping("/direct/reorder")
  public ApiResponse<Void> reorderDirect(
      @AuthenticationPrincipal UserDetails userDetails, @RequestBody List<Long> templateSeqList) {
    Integer userSeq = getUserSeq(userDetails);
    messageTemplateService.reorderBySendingForm(userSeq, templateSeqList, "d");
    return ApiResponse.success(null);
  }

  /** UserDetails에서 userSeq 추출 */
  private Integer getUserSeq(UserDetails userDetails) {
    String userId = userDetails.getUsername();
    return userMapper
        .findByUserId(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자를 찾을 수 없습니다."))
        .getSeq();
  }
}
