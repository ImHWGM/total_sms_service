package kr.wisead.domain.email.controller;

import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.email.service.EmailUnsubscribeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 이메일 수신거부 Controller
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class UnsubscribeController {

    private final EmailUnsubscribeService emailUnsubscribeService;

    /**
     * 이메일 수신거부 (레거시 호환 - GET)
     * GET /unsubscribe?email=xxx@xxx.com
     */
    @GetMapping("/unsubscribe")
    public Map<String, String> unsubscribeLegacy(@RequestParam String email) {
        log.info("이메일 수신거부 요청 (레거시): {}", email);
        return emailUnsubscribeService.unsubscribe(email);
    }

    /**
     * 이메일 수신거부 (API)
     * POST /api/unsubscribe
     */
    @PostMapping("/api/unsubscribe")
    public ApiResponse<Map<String, String>> unsubscribe(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        log.info("이메일 수신거부 요청 (API): {}", email);
        Map<String, String> result = emailUnsubscribeService.unsubscribe(email);
        return ApiResponse.success(result);
    }

    /**
     * 수신거부 여부 확인
     * GET /api/unsubscribe/check?email=xxx@xxx.com
     */
    @GetMapping("/api/unsubscribe/check")
    public ApiResponse<Map<String, Boolean>> checkUnsubscribed(@RequestParam String email) {
        boolean isUnsubscribed = emailUnsubscribeService.isUnsubscribed(email);
        return ApiResponse.success(Map.of("unsubscribed", isUnsubscribed));
    }
}
