package kr.wisead.domain.message.controller;

import jakarta.validation.Valid;
import java.util.List;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.file.service.FileStorageService;
import kr.wisead.domain.message.dto.MessageTemplateRequest;
import kr.wisead.domain.message.dto.MessageTemplateResponse;
import kr.wisead.domain.message.service.MessageTemplateService;
import kr.wisead.security.jwt.CurrentUser;
import kr.wisead.security.jwt.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  /** 템플릿 생성 */
  @PostMapping
  public ApiResponse<MessageTemplateResponse> create(
      @CurrentUser JwtPrincipal user, @Valid @RequestBody MessageTemplateRequest request) {
    MessageTemplateResponse response = messageTemplateService.create(user.seq(), request);
    return ApiResponse.success(response);
  }

  /** MMS 템플릿 생성 (이미지 포함) */
  @PostMapping("/with-image")
  public ApiResponse<MessageTemplateResponse> createWithImage(
      @CurrentUser JwtPrincipal user,
      @RequestParam("msgType") String msgType,
      @RequestParam(value = "subject", required = false) String subject,
      @RequestParam("text") String text,
      @RequestParam(value = "sendingForm", required = false, defaultValue = "d") String sendingForm,
      @RequestParam(value = "image", required = false) MultipartFile image) {

    String imagePath = null;

    // MMS이고 이미지가 있을 때만 처리
    if ("MMS".equals(msgType) && image != null && !image.isEmpty()) {
      imagePath = fileStorageService.storeTemplateImage(image, user.seq());
    }

    MessageTemplateRequest request =
        MessageTemplateRequest.builder()
            .msgType(msgType)
            .subject(subject)
            .text(text)
            .sendingForm(sendingForm)
            .imagePath(imagePath)
            .build();

    MessageTemplateResponse response = messageTemplateService.create(user.seq(), request);
    return ApiResponse.success(response);
  }

  /**
   * 템플릿 목록 조회
   *
   * @param type 발송 형태 (s: 설문용, d: 직접발송용) - 없으면 전체 조회
   */
  @GetMapping
  public ApiResponse<List<MessageTemplateResponse>> getList(
      @CurrentUser JwtPrincipal user, @RequestParam(required = false) String type) {
    List<MessageTemplateResponse> list;
    if (type != null && !type.isEmpty()) {
      list = messageTemplateService.getListBySendingForm(user.seq(), type);
    } else {
      list = messageTemplateService.getList(user.seq());
    }
    return ApiResponse.success(list);
  }

  /** 설문용 템플릿 목록 조회 */
  @GetMapping("/survey")
  public ApiResponse<List<MessageTemplateResponse>> getSurveyList(@CurrentUser JwtPrincipal user) {
    List<MessageTemplateResponse> list =
        messageTemplateService.getListBySendingForm(user.seq(), "s");
    return ApiResponse.success(list);
  }

  /** 직접발송용 템플릿 목록 조회 */
  @GetMapping("/direct")
  public ApiResponse<List<MessageTemplateResponse>> getDirectList(@CurrentUser JwtPrincipal user) {
    List<MessageTemplateResponse> list =
        messageTemplateService.getListBySendingForm(user.seq(), "d");
    return ApiResponse.success(list);
  }

  /** 템플릿 상세 조회 (소유자 검증 포함) */
  @GetMapping("/{templateSeq}")
  public ApiResponse<MessageTemplateResponse> getOne(
      @CurrentUser JwtPrincipal user, @PathVariable Long templateSeq) {
    MessageTemplateResponse response = messageTemplateService.getOne(templateSeq, user.seq());
    return ApiResponse.success(response);
  }

  /** 템플릿 수정 */
  @PutMapping("/{templateSeq}")
  public ApiResponse<MessageTemplateResponse> update(
      @CurrentUser JwtPrincipal user,
      @PathVariable Long templateSeq,
      @Valid @RequestBody MessageTemplateRequest request) {
    MessageTemplateResponse response =
        messageTemplateService.update(templateSeq, user.seq(), request);
    return ApiResponse.success(response);
  }

  /** 템플릿 삭제 */
  @DeleteMapping("/{templateSeq}")
  public ApiResponse<Void> delete(
      @CurrentUser JwtPrincipal user, @PathVariable Long templateSeq) {
    messageTemplateService.delete(templateSeq, user.seq());
    return ApiResponse.success(null);
  }

  /** 템플릿 순서 변경 */
  @PutMapping("/reorder")
  public ApiResponse<Void> reorder(
      @CurrentUser JwtPrincipal user, @RequestBody List<Long> templateSeqList) {
    messageTemplateService.reorder(user.seq(), templateSeqList);
    return ApiResponse.success(null);
  }

  /** 설문용 템플릿 순서 변경 */
  @PutMapping("/survey/reorder")
  public ApiResponse<Void> reorderSurvey(
      @CurrentUser JwtPrincipal user, @RequestBody List<Long> templateSeqList) {
    messageTemplateService.reorderBySendingForm(user.seq(), templateSeqList, "s");
    return ApiResponse.success(null);
  }

  /** 직접발송용 템플릿 순서 변경 */
  @PutMapping("/direct/reorder")
  public ApiResponse<Void> reorderDirect(
      @CurrentUser JwtPrincipal user, @RequestBody List<Long> templateSeqList) {
    messageTemplateService.reorderBySendingForm(user.seq(), templateSeqList, "d");
    return ApiResponse.success(null);
  }
}
