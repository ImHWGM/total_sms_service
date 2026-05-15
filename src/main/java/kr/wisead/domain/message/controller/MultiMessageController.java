package kr.wisead.domain.message.controller;

import java.util.ArrayList;
import java.util.List;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.file.service.FileStorageService;
import kr.wisead.domain.message.dto.MultiMessageRequest;
import kr.wisead.domain.message.dto.MultiMessageResponse;
import kr.wisead.domain.message.service.MultiMessageService;
import kr.wisead.security.jwt.CurrentUser;
import kr.wisead.security.jwt.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 일반 문자(Multi Message) 발송 Controller. SMS/LMS/MMS 각각 다른 단가 적용.
 *
 * <p>audit 표준은 user_id(alpha). {@code user.userId()} 를 regId 로 전달한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/multi")
@RequiredArgsConstructor
public class MultiMessageController {

  private final MultiMessageService multiMessageService;
  private final FileStorageService fileStorageService;

  /** 일반 문자 발송 (직접 입력 - JSON) POST /api/multi/send */
  @PostMapping("/send")
  public ApiResponse<MultiMessageResponse> sendMessages(
      @CurrentUser JwtPrincipal user, @RequestBody MultiMessageRequest request) {
    MultiMessageResponse response = multiMessageService.sendDirectMessage(request, user.userId());
    return ApiResponse.success(response);
  }

  /** 일반 문자 발송 (MMS 이미지 포함 - Multipart) POST /api/multi/send/mms */
  @PostMapping("/send/mms")
  public ApiResponse<MultiMessageResponse> sendMmsMessages(
      @CurrentUser JwtPrincipal user,
      @RequestParam("messageType") String messageType,
      @RequestParam("callback") String callback,
      @RequestParam(value = "subject", required = false) String subject,
      @RequestParam("text") String text,
      @RequestParam(value = "reqType", defaultValue = "direct") String reqType,
      @RequestParam(value = "reqDate", required = false) String reqDate,
      @RequestParam(value = "delDuplicateNum", defaultValue = "N") String delDuplicateNum,
      @RequestParam("receivers") String receiversJson,
      @RequestParam(value = "mmsFiles", required = false) List<MultipartFile> mmsFiles,
      @RequestParam(value = "forceValidation", defaultValue = "false") boolean forceValidation) {

    // MMS 파일 저장
    String fileLoc1 = null, fileLoc2 = null, fileLoc3 = null;
    int fileCnt = 0;

    if (mmsFiles != null && !mmsFiles.isEmpty()) {
      fileCnt = mmsFiles.size();
      if (fileCnt >= 1 && !mmsFiles.get(0).isEmpty()) {
        fileLoc1 = fileStorageService.storeMmsFileRelative(mmsFiles.get(0));
      }
      if (fileCnt >= 2 && !mmsFiles.get(1).isEmpty()) {
        fileLoc2 = fileStorageService.storeMmsFileRelative(mmsFiles.get(1));
      }
      if (fileCnt >= 3 && !mmsFiles.get(2).isEmpty()) {
        fileLoc3 = fileStorageService.storeMmsFileRelative(mmsFiles.get(2));
      }
    }

    // receivers JSON 파싱
    List<MultiMessageRequest.ReceiverInfo> receivers = parseReceivers(receiversJson);

    MultiMessageRequest request =
        MultiMessageRequest.builder()
            .messageType(messageType)
            .callback(callback)
            .subject(subject)
            .text(text)
            .reqType(reqType)
            .reqDate(reqDate)
            .delDuplicateNum(delDuplicateNum)
            .receivers(receivers)
            .fileLoc1(fileLoc1)
            .fileLoc2(fileLoc2)
            .fileLoc3(fileLoc3)
            .fileCnt(fileCnt)
            .forceValidation(forceValidation)
            .build();

    MultiMessageResponse response = multiMessageService.sendDirectMessage(request, user.userId());
    return ApiResponse.success(response);
  }

  /** receivers JSON 문자열 파싱 형식: [{"phone":"01012345678","repChar01":"홍길동"},...] */
  private List<MultiMessageRequest.ReceiverInfo> parseReceivers(String receiversJson) {
    List<MultiMessageRequest.ReceiverInfo> receivers = new ArrayList<>();

    if (receiversJson == null || receiversJson.isEmpty()) {
      return receivers;
    }

    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      List<java.util.Map<String, String>> list =
          mapper.readValue(
              receiversJson,
              new com.fasterxml.jackson.core.type.TypeReference<
                  List<java.util.Map<String, String>>>() {});

      for (java.util.Map<String, String> item : list) {
        receivers.add(
            MultiMessageRequest.ReceiverInfo.builder()
                .phone(item.get("phone"))
                .text(item.get("text"))
                .repChar01(item.get("repChar01"))
                .repChar02(item.get("repChar02"))
                .repChar03(item.get("repChar03"))
                .build());
      }
    } catch (Exception e) {
      log.error("receivers JSON 파싱 실패: {}", e.getMessage());
    }

    return receivers;
  }
}
