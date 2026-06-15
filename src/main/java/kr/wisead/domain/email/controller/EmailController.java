package kr.wisead.domain.email.controller;

import jakarta.validation.Valid;
import kr.wisead.common.dto.VerificationStatus;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.email.dto.EmailRequest;
import kr.wisead.domain.email.dto.EmailVerificationRequest;
import kr.wisead.domain.email.service.EmailService;
import kr.wisead.domain.email.service.PreSignupEmailAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 이메일 Controller.
 *
 * <p>회원가입(사전 인증) 흐름 전용. 4개 인증 메서드 모두 {@link PreSignupEmailAuthService}(key=email)에 라우팅된다. 로그인/2FA
 * 흐름은 {@code AuthService}/{@code EmailAuthService}(key=userId)를 통해 동작한다.
 *
 * <p>plan §4 Phase B-0-4.
 */
@Slf4j
@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailController {

  private final EmailService emailService;
  private final PreSignupEmailAuthService emailAuthService;

  /** 인증 코드 발송 POST /api/email/verification Body: { "email": "test@example.com" } */
  @PostMapping("/verification")
  public ApiResponse<Void> sendVerificationCode(
      @Valid @RequestBody EmailVerificationRequest request) {
    emailAuthService.sendVerificationCode(request.getEmail());
    return ApiResponse.success(null, "인증 코드가 발송되었습니다.");
  }

  /**
   * 인증 코드 검증 POST /api/email/verification/verify Body: { "email": "test@example.com", "code":
   * "ABC12345" }
   */
  @PostMapping("/verification/verify")
  public ApiResponse<Boolean> verifyCode(@Valid @RequestBody EmailVerificationRequest request) {
    boolean verified = emailAuthService.verifyCode(request.getEmail(), request.getCode());
    return ApiResponse.success(verified, "이메일 인증이 완료되었습니다.");
  }

  /** 인증 상태 조회 GET /api/email/verification/status?email=test@example.com */
  @GetMapping("/verification/status")
  public ApiResponse<VerificationStatus> getVerificationStatus(@RequestParam String email) {
    VerificationStatus status = emailAuthService.getVerificationStatus(email);
    return ApiResponse.success(status);
  }

  /** 인증 코드 재발송 POST /api/email/verification/resend Body: { "email": "test@example.com" } */
  @PostMapping("/verification/resend")
  public ApiResponse<Void> resendVerificationCode(
      @Valid @RequestBody EmailVerificationRequest request) {
    emailAuthService.resendVerificationCode(request.getEmail());
    return ApiResponse.success(null, "인증 코드가 재발송되었습니다.");
  }

  /** 일반 이메일 발송 (관리자용) POST /api/email/send */
  @PostMapping("/send")
  public ApiResponse<Void> sendEmail(@RequestBody EmailRequest request) {
    emailService.sendEmail(request);
    return ApiResponse.success();
  }
}
