package kr.wisead.domain.sms.controller;

import jakarta.validation.Valid;
import kr.wisead.common.dto.VerificationStatus;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.sms.dto.SmsVerificationRequest;
import kr.wisead.domain.sms.service.PreSignupSmsAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * SMS 인증 Controller.
 *
 * <p>회원가입(사전 인증) 흐름 전용. 4개 인증 메서드 모두 {@link PreSignupSmsAuthService}(key=phone)에 라우팅된다. 로그인/2FA
 * 흐름은 {@code AuthService}/{@code SmsAuthService}(key=userId)를 통해 동작한다.
 *
 * <p>이메일판 {@code EmailController}(/api/email) 와 평행 구조.
 */
@Slf4j
@RestController
@RequestMapping("/api/sms")
@RequiredArgsConstructor
public class SmsVerificationController {

  private final PreSignupSmsAuthService smsAuthService;

  /** 인증 코드 발송 POST /api/sms/verification Body: { "phoneNumber": "010-1234-5678" } */
  @PostMapping("/verification")
  public ApiResponse<Void> sendVerificationCode(@Valid @RequestBody SmsVerificationRequest request) {
    smsAuthService.sendVerificationCode(
        request.getPhoneNumber(), PreSignupSmsAuthService.PURPOSE_SIGNUP);
    return ApiResponse.success(null, "인증 코드가 발송되었습니다.");
  }

  /**
   * 인증 코드 검증 POST /api/sms/verification/verify Body: { "phoneNumber": "010-1234-5678", "code":
   * "123456" }
   */
  @PostMapping("/verification/verify")
  public ApiResponse<Boolean> verifyCode(@Valid @RequestBody SmsVerificationRequest request) {
    boolean verified =
        smsAuthService.verifyCode(
            request.getPhoneNumber(), request.getCode(), PreSignupSmsAuthService.PURPOSE_SIGNUP);
    return ApiResponse.success(verified, "휴대폰 인증이 완료되었습니다.");
  }

  /** 인증 상태 조회 GET /api/sms/verification/status?phoneNumber=010-1234-5678 */
  @GetMapping("/verification/status")
  public ApiResponse<VerificationStatus> getVerificationStatus(
      @RequestParam String phoneNumber) {
    VerificationStatus status =
        smsAuthService.getVerificationStatus(phoneNumber, PreSignupSmsAuthService.PURPOSE_SIGNUP);
    return ApiResponse.success(status);
  }

  /** 인증 코드 재발송 POST /api/sms/verification/resend Body: { "phoneNumber": "010-1234-5678" } */
  @PostMapping("/verification/resend")
  public ApiResponse<Void> resendVerificationCode(
      @Valid @RequestBody SmsVerificationRequest request) {
    smsAuthService.resendVerificationCode(
        request.getPhoneNumber(), PreSignupSmsAuthService.PURPOSE_SIGNUP);
    return ApiResponse.success(null, "인증 코드가 재발송되었습니다.");
  }
}
