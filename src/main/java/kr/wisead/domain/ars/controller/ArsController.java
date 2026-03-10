package kr.wisead.domain.ars.controller;

import java.util.List;
import java.util.Map;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.util.CommonUtils;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.ars.dto.ArsResponse;
import kr.wisead.domain.ars.dto.BlockedSenderResponse;
import kr.wisead.domain.ars.service.ArsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ARS 수신거부 Controller */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ArsController {

  private final ArsService arsService;

  /** 발신번호 자동등록형 수신거부 ARS 시스템에서 호출 - 최근 발송 이력에서 상점코드 자동 조회 */
  @PostMapping(value = "/ars/auto-reject", produces = "text/html; charset=EUC-KR")
  public String autoReject(
      @RequestParam("T_ID") String tId,
      @RequestParam("T_TIME") String tTime,
      @RequestParam("MENU_NAME") String menuName,
      @RequestParam("ANI") String ani,
      @RequestParam("DTMF_CNT") String dtmfCnt) {
    log.info("ARS 자동등록형 수신거부 요청: ani={}, menuName={}", CommonUtils.maskingPhone(ani), menuName);

    String effectiveTime = tTime != null ? tTime : arsService.getNowString();

    // 휴대폰 번호 유효성 검사
    if (!arsService.isCellPhone(ani)) {
      log.warn("휴대폰 번호가 아님: {}", CommonUtils.maskingPhone(ani));
      return ArsResponse.failNotCellPhone(tId, effectiveTime, menuName).toHtml();
    }

    // 최근 발송 이력에서 상점코드 조회
    String storeCode = arsService.getStoreCodeByAni(ani, effectiveTime);
    if (storeCode == null) {
      log.warn("발송 이력 없음: {}", CommonUtils.maskingPhone(ani));
      return ArsResponse.failInvalidStoreCode(tId, effectiveTime, menuName).toHtml();
    }

    // 수신거부 등록
    arsService.registerBlockedSender(ani, storeCode, menuName, effectiveTime);

    // 회사명 조회
    String corpName = arsService.getCorpNameByStoreCode(storeCode);
    String dateStr = arsService.getTodayString();

    return ArsResponse.success(tId, effectiveTime, menuName, corpName, dateStr).toHtml();
  }

  /** 상점코드 입력형 수신거부 ARS 시스템에서 호출 - 고객이 DTMF로 상점코드 입력 */
  @PostMapping(value = "/ars/code-reject", produces = "text/html; charset=EUC-KR")
  public String codeReject(
      @RequestParam("T_ID") String tId,
      @RequestParam("T_TIME") String tTime,
      @RequestParam("MENU_NAME") String menuName,
      @RequestParam("ANI") String ani,
      @RequestParam("DTMF_CNT") String dtmfCnt,
      @RequestParam(value = "DTMF_1", required = false) String dtmf1) {
    log.info(
        "ARS 상점코드 입력형 수신거부 요청: ani={}, storeCode={}, menuName={}", CommonUtils.maskingPhone(ani), dtmf1, menuName);

    String effectiveTime = tTime != null ? tTime : arsService.getNowString();

    // 휴대폰 번호 유효성 검사
    if (!arsService.isCellPhone(ani)) {
      log.warn("휴대폰 번호가 아님: {}", CommonUtils.maskingPhone(ani));
      return ArsResponse.failNotCellPhone(tId, effectiveTime, menuName).toHtml();
    }

    // 상점코드 유효성 검사
    if (!arsService.isValidStoreCode(dtmf1)) {
      log.warn("잘못된 상점코드: {}", dtmf1);
      return ArsResponse.failInvalidStoreCode(tId, effectiveTime, menuName).toHtml();
    }

    // 수신거부 등록
    arsService.registerBlockedSender(ani, dtmf1, menuName, effectiveTime);

    // 회사명 조회
    String corpName = arsService.getCorpNameByStoreCode(dtmf1);
    String dateStr = arsService.getTodayString();

    return ArsResponse.success(tId, effectiveTime, menuName, corpName, dateStr).toHtml();
  }

  // ==================== Web API (관리자용) ====================

  /** 수신거부 목록 조회 */
  @GetMapping("/api/blocked-senders")
  public ResponseEntity<ApiResponse<PageResponse<BlockedSenderResponse>>> getBlockedSenders(
      @RequestParam String storeCode,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {

    PageResponse<BlockedSenderResponse> response =
        arsService.getBlockedSenders(storeCode, page, size);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  /** 수신거부 삭제 */
  @DeleteMapping("/api/blocked-senders")
  public ResponseEntity<ApiResponse<Integer>> deleteBlockedSenders(
      @RequestBody List<Map<String, String>> keyList) {

    int deletedCount = arsService.deleteBlockedSenders(keyList);
    return ResponseEntity.ok(ApiResponse.success(deletedCount));
  }

  /** 수신거부 여부 확인 (메시지 발송 시 체크용) */
  @GetMapping("/api/blocked-senders/check")
  public ResponseEntity<ApiResponse<Boolean>> checkBlocked(
      @RequestParam String ani, @RequestParam String storeCode) {

    boolean blocked = arsService.isBlocked(ani, storeCode);
    return ResponseEntity.ok(ApiResponse.success(blocked));
  }

  /** 수신거부 번호 필터링 (일괄 발송 시) */
  @PostMapping("/api/blocked-senders/filter")
  public ResponseEntity<ApiResponse<List<String>>> filterBlockedNumbers(
      @RequestParam String storeCode, @RequestBody List<String> phoneNumbers) {

    List<String> blockedNumbers = arsService.filterBlockedNumbers(storeCode, phoneNumbers);
    return ResponseEntity.ok(ApiResponse.success(blockedNumbers));
  }

}
