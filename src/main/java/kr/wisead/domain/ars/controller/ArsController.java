package kr.wisead.domain.ars.controller;

import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.ars.dto.ArsRequest;
import kr.wisead.domain.ars.dto.ArsResponse;
import kr.wisead.domain.ars.dto.BlockedSenderResponse;
import kr.wisead.domain.ars.service.ArsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ARS 수신거부 Controller
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ArsController {

    private final ArsService arsService;

    /**
     * 발신번호 자동등록형 수신거부
     * ARS 시스템에서 호출 - 최근 발송 이력에서 상점코드 자동 조회
     */
    @PostMapping("/ars/auto-reject")
    public ArsResponse autoReject(ArsRequest request) {
        log.info("ARS 자동등록형 수신거부 요청: ani={}, menuName={}",
                maskPhone(request.getAni()), request.getMenuName());

        String ani = request.getAni();
        String tTime = request.getTTime() != null ? request.getTTime() : arsService.getNowString();

        // 휴대폰 번호 유효성 검사
        if (!arsService.isCellPhone(ani)) {
            log.warn("휴대폰 번호가 아님: {}", maskPhone(ani));
            return ArsResponse.failNotCellPhone(request.getTId(), tTime, request.getMenuName());
        }

        // 최근 발송 이력에서 상점코드 조회
        String storeCode = arsService.getStoreCodeByAni(ani, tTime);
        if (storeCode == null) {
            log.warn("발송 이력 없음: {}", maskPhone(ani));
            return ArsResponse.failInvalidStoreCode(request.getTId(), tTime, request.getMenuName());
        }

        // 수신거부 등록
        boolean registered = arsService.registerBlockedSender(ani, storeCode, request.getMenuName(), tTime);

        // 회사명 조회
        String corpName = arsService.getCorpNameByStoreCode(storeCode);
        String dateStr = arsService.getTodayString();

        return ArsResponse.success(request.getTId(), tTime, request.getMenuName(), corpName, dateStr);
    }

    /**
     * 상점코드 입력형 수신거부
     * ARS 시스템에서 호출 - 고객이 DTMF로 상점코드 입력
     */
    @PostMapping("/ars/code-reject")
    public ArsResponse codeReject(ArsRequest request) {
        log.info("ARS 상점코드 입력형 수신거부 요청: ani={}, storeCode={}, menuName={}",
                maskPhone(request.getAni()), request.getDtmf1(), request.getMenuName());

        String ani = request.getAni();
        String storeCode = request.getDtmf1();
        String tTime = request.getTTime() != null ? request.getTTime() : arsService.getNowString();

        // 휴대폰 번호 유효성 검사
        if (!arsService.isCellPhone(ani)) {
            log.warn("휴대폰 번호가 아님: {}", maskPhone(ani));
            return ArsResponse.failNotCellPhone(request.getTId(), tTime, request.getMenuName());
        }

        // 상점코드 유효성 검사
        if (!arsService.isValidStoreCode(storeCode)) {
            log.warn("잘못된 상점코드: {}", storeCode);
            return ArsResponse.failInvalidStoreCode(request.getTId(), tTime, request.getMenuName());
        }

        // 수신거부 등록
        boolean registered = arsService.registerBlockedSender(ani, storeCode, request.getMenuName(), tTime);

        // 회사명 조회
        String corpName = arsService.getCorpNameByStoreCode(storeCode);
        String dateStr = arsService.getTodayString();

        return ArsResponse.success(request.getTId(), tTime, request.getMenuName(), corpName, dateStr);
    }

    // ==================== Web API (관리자용) ====================

    /**
     * 수신거부 목록 조회
     */
    @GetMapping("/api/blocked-senders")
    public ResponseEntity<ApiResponse<PageResponse<BlockedSenderResponse>>> getBlockedSenders(
            @RequestParam String storeCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<BlockedSenderResponse> response = arsService.getBlockedSenders(storeCode, page, size);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 수신거부 삭제
     */
    @DeleteMapping("/api/blocked-senders")
    public ResponseEntity<ApiResponse<Integer>> deleteBlockedSenders(
            @RequestBody List<Map<String, String>> keyList) {

        int deletedCount = arsService.deleteBlockedSenders(keyList);
        return ResponseEntity.ok(ApiResponse.success(deletedCount));
    }

    /**
     * 수신거부 여부 확인 (메시지 발송 시 체크용)
     */
    @GetMapping("/api/blocked-senders/check")
    public ResponseEntity<ApiResponse<Boolean>> checkBlocked(
            @RequestParam String ani,
            @RequestParam String storeCode) {

        boolean blocked = arsService.isBlocked(ani, storeCode);
        return ResponseEntity.ok(ApiResponse.success(blocked));
    }

    /**
     * 수신거부 번호 필터링 (일괄 발송 시)
     */
    @PostMapping("/api/blocked-senders/filter")
    public ResponseEntity<ApiResponse<List<String>>> filterBlockedNumbers(
            @RequestParam String storeCode,
            @RequestBody List<String> phoneNumbers) {

        List<String> blockedNumbers = arsService.filterBlockedNumbers(storeCode, phoneNumbers);
        return ResponseEntity.ok(ApiResponse.success(blockedNumbers));
    }

    // ==================== Private Methods ====================

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
