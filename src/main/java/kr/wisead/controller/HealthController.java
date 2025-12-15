package kr.wisead.controller;

import kr.wisead.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 헬스 체크 및 테스트용 컨트롤러
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class HealthController {

    /**
     * 서버 상태 확인 (인증 불필요)
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> healthCheck() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        data.put("timestamp", LocalDateTime.now());
        data.put("version", "1.0.0");
        return ApiResponse.success(data, "서버가 정상 작동 중입니다.");
    }

    /**
     * API 버전 정보
     */
    @GetMapping("/version")
    public ApiResponse<Map<String, String>> version() {
        Map<String, String> data = new HashMap<>();
        data.put("application", "WiseAd");
        data.put("version", "1.0.0");
        data.put("java", System.getProperty("java.version"));
        data.put("springBoot", "3.4.1");
        return ApiResponse.success(data);
    }
}
