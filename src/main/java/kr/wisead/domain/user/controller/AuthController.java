package kr.wisead.domain.user.controller;

import jakarta.validation.Valid;
import java.util.Map;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.user.dto.LoginRequest;
import kr.wisead.domain.user.dto.LoginResponse;
import kr.wisead.domain.user.dto.SignUpRequest;
import kr.wisead.domain.user.dto.SwitchChannelRequest;
import kr.wisead.domain.user.dto.TokenRefreshRequest;
import kr.wisead.domain.user.service.AuthService;
import kr.wisead.domain.user.service.BusinessNoValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 인증 API 컨트롤러 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;
  private final BusinessNoValidationService businessNoValidationService;

  /**
   * 로그인.
   *
   * <p>plan v5 §4 Phase C: 응답의 accessToken 유무로 OTP 필요 여부를 구분한다. accessToken == null 이면 OTP 발송됨
   * (response.channel + maskedEmail/maskedPhone 확인).
   */
  @PostMapping("/login")
  public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    LoginResponse response = authService.login(request);
    if (response.getAccessToken() == null) {
      String channel = response.getChannel();
      String message = "SMS".equals(channel) ? "SMS 인증이 필요합니다." : "이메일 인증이 필요합니다.";
      return ApiResponse.success(response, message);
    }
    return ApiResponse.success(response, "로그인 성공");
  }

  /** 로그인 이메일 인증 코드 재발송 */
  @PostMapping("/resend-email-code")
  public ApiResponse<LoginResponse> resendEmailCode(@Valid @RequestBody LoginRequest request) {
    LoginResponse response = authService.resendLoginEmailCode(request);
    return ApiResponse.success(response, "인증 코드가 재발송되었습니다.");
  }

  /**
   * 로그인 OTP 채널 전환 (EMAIL ↔ SMS).
   *
   * <p>plan v5 §4 Phase D. 1차 인증(ID/PW) 통과 후 OTP 입력 직전 단계에서 호출. sessionKey 로 사용자 식별.
   */
  @PostMapping("/switch-channel")
  public ApiResponse<LoginResponse> switchChannel(@RequestBody SwitchChannelRequest request) {
    LoginResponse response =
        authService.switchChannel(request.getSessionKey(), request.getTargetChannel());
    String message =
        "SMS".equals(response.getChannel()) ? "SMS 인증 코드를 발송했습니다." : "이메일 인증 코드를 발송했습니다.";
    return ApiResponse.success(response, message);
  }

  /** 회원가입 */
  @PostMapping("/signup")
  public ApiResponse<Void> signUp(@Valid @RequestBody SignUpRequest request) {
    authService.signUp(request);
    return ApiResponse.success("회원가입이 완료되었습니다. 관리자 승인 후 로그인 가능합니다.");
  }

  /** 토큰 갱신 */
  @PostMapping("/refresh")
  public ApiResponse<LoginResponse> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
    LoginResponse response = authService.refreshToken(request.getRefreshToken());
    return ApiResponse.success(response, "토큰 갱신 성공");
  }

  /** 아이디 중복 확인 */
  @GetMapping("/check-userid")
  public ApiResponse<Map<String, Boolean>> checkUserId(@RequestParam String userId) {
    boolean isDuplicate = authService.checkUserIdDuplicate(userId);
    return ApiResponse.success(Map.of("duplicate", isDuplicate));
  }

  /** 이메일 중복 확인 */
  @GetMapping("/check-email")
  public ApiResponse<Map<String, Boolean>> checkEmail(@RequestParam String email) {
    boolean isDuplicate = authService.checkEmailDuplicate(email);
    return ApiResponse.success(Map.of("duplicate", isDuplicate));
  }

  /** 사업자등록번호 유효성 검증 POST /api/auth/validate-bizno */
  @PostMapping("/validate-bizno")
  public ApiResponse<Map<String, Object>> validateBizNo(@RequestBody Map<String, String> request) {
    String bizNum = request.get("bizNum");
    Map<String, Object> result = businessNoValidationService.validateBizNo(bizNum);
    return ApiResponse.success(result);
  }
}
