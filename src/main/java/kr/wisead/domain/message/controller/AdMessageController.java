package kr.wisead.domain.message.controller;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.file.service.FileStorageService;
import kr.wisead.domain.message.dto.AdMessageRequest;
import kr.wisead.domain.message.dto.AdMessageResponse;
import kr.wisead.domain.message.service.AdMessageService;
import kr.wisead.security.jwt.CurrentUser;
import kr.wisead.security.jwt.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 광고 문자 발송 Controller.
 *
 * <p>audit 표준은 user_id(alpha). {@code user.userId()} 를 AdMessageService 에 전달한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/ad/msg")
@RequiredArgsConstructor
public class AdMessageController {

  private final AdMessageService adMessageService;
  private final FileStorageService fileStorageService;

  /** 광고 문자 발송 (직접 등록) */
  @PostMapping("/send")
  public ApiResponse<AdMessageResponse> sendAdMessage(
      @CurrentUser JwtPrincipal user,
      @Valid @RequestPart("request") AdMessageRequest request,
      @RequestPart(value = "mmsFiles", required = false) List<MultipartFile> mmsFiles) {

    String userId = user.userId();
    log.info("광고문자 발송 요청 - userId: {}, type: {}", userId, request.getMessageTypeIs());

    // MMS 파일 업로드 처리
    AdMessageRequest processedRequest = processMmsFiles(request, mmsFiles);

    AdMessageResponse response = adMessageService.sendDirectMessage(processedRequest, userId);
    return ApiResponse.success(response);
  }

  /** 광고 문자 발송 (JSON Only - MMS 파일 없는 경우) */
  @PostMapping("/send/json")
  public ApiResponse<AdMessageResponse> sendAdMessageJson(
      @CurrentUser JwtPrincipal user, @Valid @RequestBody AdMessageRequest request) {

    String userId = user.userId();
    log.info("광고문자 발송 요청 (JSON) - userId: {}, type: {}", userId, request.getMessageTypeIs());

    AdMessageResponse response = adMessageService.sendDirectMessage(request, userId);
    return ApiResponse.success(response);
  }

  /** 야간 전송제한 시간 체크 */
  @GetMapping("/check-night-time")
  public ApiResponse<Map<String, Object>> checkNightTimeRestriction() {
    Map<String, Object> result = new HashMap<>();
    boolean isNightTime = adMessageService.isNightTimeRestriction();

    result.put("isNightTime", isNightTime);
    if (isNightTime) {
      result.put("message", "현재 야간 전송제한 시간으로 인해 금일 20:00 ~ 익일 09:00까지는 광고문자 전송이 제한됩니다.");
    } else {
      result.put("message", "광고문자 발송 가능 시간입니다.");
    }

    return ApiResponse.success(result);
  }

  /** MMS 파일 업로드 처리 */
  private AdMessageRequest processMmsFiles(AdMessageRequest request, List<MultipartFile> mmsFiles) {
    if (mmsFiles == null || mmsFiles.isEmpty()) {
      return request;
    }

    String fileloc1 = null;
    String fileloc2 = null;
    String fileloc3 = null;
    int fileCnt = 0;

    try {
      for (int i = 0; i < mmsFiles.size() && i < 3; i++) {
        MultipartFile file = mmsFiles.get(i);
        if (file != null && !file.isEmpty()) {
          String filePath = fileStorageService.storeMmsImage(file);
          fileCnt++;
          switch (i) {
            case 0 -> fileloc1 = filePath;
            case 1 -> fileloc2 = filePath;
            case 2 -> fileloc3 = filePath;
          }
        }
      }
    } catch (Exception e) {
      log.error("MMS 파일 업로드 실패: {}", e.getMessage());
    }

    // 새로운 Request 객체 생성 (불변성 유지)
    return AdMessageRequest.builder()
        .reqType(request.getReqType())
        .messageTypeIs(request.getMessageTypeIs())
        .reqNum(request.getReqNum())
        .sendTtl(request.getSendTtl())
        .sendTimeType(request.getSendTimeType())
        .reqDate(request.getReqDate())
        .delDuplicateNum(request.getDelDuplicateNum())
        .contTxt(request.getContTxt())
        .recipients(request.getRecipients())
        .fileCnt(fileCnt > 0 ? fileCnt : request.getFileCnt())
        .fileloc1(fileloc1 != null ? fileloc1 : request.getFileloc1())
        .fileloc2(fileloc2 != null ? fileloc2 : request.getFileloc2())
        .fileloc3(fileloc3 != null ? fileloc3 : request.getFileloc3())
        .build();
  }
}
