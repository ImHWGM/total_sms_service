package kr.wisead.domain.ars.controller;

import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.ars.dto.BlockedSenderResponse;
import kr.wisead.domain.ars.service.BlockedNumberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 차단 번호(수신거부) 관리 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/blocked-numbers")
@RequiredArgsConstructor
public class BlockedNumberController {

    private final BlockedNumberService blockedNumberService;

    /**
     * 수신거부 목록 조회 (페이징)
     * GET /api/blocked-numbers?storeCode=xxx&page=1&size=20
     */
    @GetMapping
    public ApiResponse<PageResponse<BlockedSenderResponse>> getBlockedNumbers(
            @RequestParam String storeCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<BlockedSenderResponse> response = blockedNumberService.getBlockedNumbers(storeCode, page, size);
        return ApiResponse.success(response);
    }

    /**
     * 수신거부 목록 조회 (전체)
     * GET /api/blocked-numbers/all?storeCode=xxx
     */
    @GetMapping("/all")
    public ApiResponse<List<BlockedSenderResponse>> getAllBlockedNumbers(
            @RequestParam String storeCode) {
        List<BlockedSenderResponse> response = blockedNumberService.getAllBlockedNumbers(storeCode);
        return ApiResponse.success(response);
    }

    /**
     * 수신거부 건수 조회
     * GET /api/blocked-numbers/count?storeCode=xxx
     */
    @GetMapping("/count")
    public ApiResponse<Integer> countBlockedNumbers(@RequestParam String storeCode) {
        int count = blockedNumberService.countBlockedNumbers(storeCode);
        return ApiResponse.success(count);
    }

    /**
     * 수신거부 등록 (단건)
     * POST /api/blocked-numbers
     */
    @PostMapping
    public ApiResponse<Boolean> registerBlockedNumber(@RequestBody BlockedNumberRequest request) {
        boolean registered = blockedNumberService.registerBlockedNumber(
                request.getPhoneNumber(),
                request.getStoreCode(),
                request.getMenuName()
        );
        String message = registered ? "수신거부가 등록되었습니다." : "이미 등록된 번호입니다.";
        return ApiResponse.success(registered, message);
    }

    /**
     * 수신거부 등록 (일괄)
     * POST /api/blocked-numbers/batch
     */
    @PostMapping("/batch")
    public ApiResponse<Integer> registerBlockedNumbers(@RequestBody BatchBlockedNumberRequest request) {
        int registeredCount = blockedNumberService.registerBlockedNumbers(
                request.getPhoneNumbers(),
                request.getStoreCode(),
                request.getMenuName()
        );
        return ApiResponse.success(registeredCount, registeredCount + "건이 등록되었습니다.");
    }

    /**
     * 수신거부 삭제 (단건)
     * DELETE /api/blocked-numbers?phoneNumber=xxx&storeCode=xxx
     */
    @DeleteMapping
    public ApiResponse<Boolean> deleteBlockedNumber(
            @RequestParam String phoneNumber,
            @RequestParam String storeCode) {
        boolean deleted = blockedNumberService.deleteBlockedNumber(phoneNumber, storeCode);
        String message = deleted ? "수신거부가 삭제되었습니다." : "삭제할 번호를 찾을 수 없습니다.";
        return ApiResponse.success(deleted, message);
    }

    /**
     * 수신거부 삭제 (일괄 - 암호화된 키 목록)
     * DELETE /api/blocked-numbers/batch
     */
    @DeleteMapping("/batch")
    public ApiResponse<Integer> deleteBlockedNumbers(@RequestBody List<Map<String, String>> keyList) {
        int deletedCount = blockedNumberService.deleteBlockedNumbers(keyList);
        return ApiResponse.success(deletedCount, deletedCount + "건이 삭제되었습니다.");
    }

    /**
     * 수신거부 삭제 (일괄 - 평문 번호)
     * DELETE /api/blocked-numbers/batch-plain
     */
    @DeleteMapping("/batch-plain")
    public ApiResponse<Integer> deleteBlockedNumbersByPhone(@RequestBody BatchDeleteRequest request) {
        int deletedCount = blockedNumberService.deleteBlockedNumbersByPhone(
                request.getPhoneNumbers(),
                request.getStoreCode()
        );
        return ApiResponse.success(deletedCount, deletedCount + "건이 삭제되었습니다.");
    }

    /**
     * 수신거부 여부 확인 (단건)
     * GET /api/blocked-numbers/check?phoneNumber=xxx&storeCode=xxx
     */
    @GetMapping("/check")
    public ApiResponse<Boolean> checkBlocked(
            @RequestParam String phoneNumber,
            @RequestParam String storeCode) {
        boolean blocked = blockedNumberService.isBlocked(phoneNumber, storeCode);
        return ApiResponse.success(blocked);
    }

    /**
     * 수신거부 번호 필터링 (차단된 번호 반환)
     * POST /api/blocked-numbers/filter
     */
    @PostMapping("/filter")
    public ApiResponse<List<String>> filterBlockedNumbers(@RequestBody FilterRequest request) {
        List<String> blockedNumbers = blockedNumberService.filterBlockedNumbers(
                request.getStoreCode(),
                request.getPhoneNumbers()
        );
        return ApiResponse.success(blockedNumbers);
    }

    /**
     * 발송 가능 번호 조회 (차단 번호 제외)
     * POST /api/blocked-numbers/available
     */
    @PostMapping("/available")
    public ApiResponse<List<String>> getAvailableNumbers(@RequestBody FilterRequest request) {
        List<String> availableNumbers = blockedNumberService.getAvailableNumbers(
                request.getStoreCode(),
                request.getPhoneNumbers()
        );
        return ApiResponse.success(availableNumbers);
    }

    /**
     * 상점코드 유효성 검사
     * GET /api/blocked-numbers/validate-store?storeCode=xxx
     */
    @GetMapping("/validate-store")
    public ApiResponse<StoreValidationResponse> validateStoreCode(@RequestParam String storeCode) {
        boolean valid = blockedNumberService.isValidStoreCode(storeCode);
        String corpName = valid ? blockedNumberService.getCorpNameByStoreCode(storeCode) : null;
        return ApiResponse.success(new StoreValidationResponse(valid, corpName));
    }

    // ==================== Request/Response DTOs ====================

    @lombok.Data
    public static class BlockedNumberRequest {
        private String phoneNumber;
        private String storeCode;
        private String menuName;
    }

    @lombok.Data
    public static class BatchBlockedNumberRequest {
        private List<String> phoneNumbers;
        private String storeCode;
        private String menuName;
    }

    @lombok.Data
    public static class BatchDeleteRequest {
        private List<String> phoneNumbers;
        private String storeCode;
    }

    @lombok.Data
    public static class FilterRequest {
        private String storeCode;
        private List<String> phoneNumbers;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class StoreValidationResponse {
        private boolean valid;
        private String corpName;
    }
}
