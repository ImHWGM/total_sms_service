package kr.wisead.domain.user.controller;

import jakarta.validation.Valid;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.user.dto.DefaultChannelRequest;
import kr.wisead.domain.user.dto.SmsRegisterRequest;
import kr.wisead.domain.user.dto.SmsVerifyRequest;
import kr.wisead.domain.user.dto.TwoFactorSettingsResponse;
import kr.wisead.domain.user.service.TwoFactorService;
import kr.wisead.security.jwt.CurrentUser;
import kr.wisead.security.jwt.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 2FA 채널 설정 컨트롤러 — SMS 활성화/비활성화/기본 채널 변경/조회.
 *
 * <p>모든 엔드포인트는 JWT 인증 필요 (SecurityConfig 의 PUBLIC_ENDPOINTS 외 — 기본 authenticated).
 *
 * <p>plan v5 §4 Phase E-1-a.
 */
@RestController
@RequestMapping("/api/users/me/two-factor")
@RequiredArgsConstructor
public class TwoFactorController {

  private final TwoFactorService twoFactorService;

  /** SMS 등록을 위한 OTP 발송 (C2). */
  @PostMapping("/sms/send-code")
  public ApiResponse<Void> sendSmsRegisterCode(
      @CurrentUser JwtPrincipal user, @Valid @RequestBody SmsRegisterRequest request) {
    twoFactorService.sendSmsRegisterCode(user.userId(), request.getPhoneNumber());
    return ApiResponse.success("SMS 인증 코드를 발송했습니다.");
  }

  /** SMS 등록 OTP 검증 + login_phone 저장 + default_two_factor_method=SMS (C3/C4). */
  @PostMapping("/sms/verify")
  public ApiResponse<Void> verifySmsRegisterCode(
      @CurrentUser JwtPrincipal user, @Valid @RequestBody SmsVerifyRequest request) {
    twoFactorService.verifySmsRegisterCode(user.userId(), request.getCode());
    return ApiResponse.success("SMS 인증 채널이 활성화되었습니다.");
  }

  /** SMS 채널 비활성화 (login_phone 삭제 + default_two_factor_method=EMAIL). */
  @DeleteMapping("/sms")
  public ApiResponse<Void> deactivateSms(@CurrentUser JwtPrincipal user) {
    twoFactorService.deactivateSms(user.userId());
    return ApiResponse.success("SMS 인증 채널이 비활성화되었습니다.");
  }

  /** 기본 채널 변경 (EMAIL ↔ SMS — C10). */
  @PutMapping("/default")
  public ApiResponse<Void> setDefaultChannel(
      @CurrentUser JwtPrincipal user, @Valid @RequestBody DefaultChannelRequest request) {
    twoFactorService.setDefaultChannel(user.userId(), request.getChannel());
    return ApiResponse.success("기본 인증 채널이 변경되었습니다.");
  }

  /** 현재 2FA 설정 조회 (FE UI 노출용 — C10). */
  @GetMapping
  public ApiResponse<TwoFactorSettingsResponse> getSettings(@CurrentUser JwtPrincipal user) {
    TwoFactorSettingsResponse settings = twoFactorService.getSettings(user.userId());
    return ApiResponse.success(settings);
  }
}
