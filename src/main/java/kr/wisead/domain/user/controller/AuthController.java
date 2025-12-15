package kr.wisead.domain.user.controller;

import jakarta.validation.Valid;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.user.dto.LoginRequest;
import kr.wisead.domain.user.dto.LoginResponse;
import kr.wisead.domain.user.dto.SignUpRequest;
import kr.wisead.domain.user.dto.TokenRefreshRequest;
import kr.wisead.domain.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 인증 API 컨트롤러
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 로그인
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ApiResponse.success(response, "로그인 성공");
    }

    /**
     * 회원가입
     */
    @PostMapping("/signup")
    public ApiResponse<Void> signUp(@Valid @RequestBody SignUpRequest request) {
        authService.signUp(request);
        return ApiResponse.success("회원가입이 완료되었습니다. 관리자 승인 후 로그인 가능합니다.");
    }

    /**
     * 토큰 갱신
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        LoginResponse response = authService.refreshToken(request.getRefreshToken());
        return ApiResponse.success(response, "토큰 갱신 성공");
    }

    /**
     * 아이디 중복 확인
     */
    @GetMapping("/check-userid")
    public ApiResponse<Map<String, Boolean>> checkUserId(@RequestParam String userId) {
        boolean isDuplicate = authService.checkUserIdDuplicate(userId);
        return ApiResponse.success(Map.of("duplicate", isDuplicate));
    }

    /**
     * 이메일 중복 확인
     */
    @GetMapping("/check-email")
    public ApiResponse<Map<String, Boolean>> checkEmail(@RequestParam String email) {
        boolean isDuplicate = authService.checkEmailDuplicate(email);
        return ApiResponse.success(Map.of("duplicate", isDuplicate));
    }
}
