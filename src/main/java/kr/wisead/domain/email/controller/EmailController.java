package kr.wisead.domain.email.controller;

import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.email.dto.EmailRequest;
import kr.wisead.domain.email.service.EmailAuthService;
import kr.wisead.domain.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 이메일 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;
    private final EmailAuthService emailAuthService;

    /**
     * 인증 코드 발송
     * POST /api/email/verification?email=test@example.com
     */
    @PostMapping("/verification")
    public ApiResponse<Void> sendVerificationCode(@RequestParam String email) {
        emailAuthService.sendVerificationCode(email);
        return ApiResponse.success(null, "인증 코드가 발송되었습니다.");
    }

    /**
     * 인증 코드 검증
     * POST /api/email/verification/verify?email=test@example.com&code=ABC12345
     */
    @PostMapping("/verification/verify")
    public ApiResponse<Boolean> verifyCode(
            @RequestParam String email,
            @RequestParam String code) {
        boolean verified = emailAuthService.verifyCode(email, code);
        return ApiResponse.success(verified, "이메일 인증이 완료되었습니다.");
    }

    /**
     * 인증 상태 조회
     * GET /api/email/verification/status?email=test@example.com
     */
    @GetMapping("/verification/status")
    public ApiResponse<EmailAuthService.VerificationStatus> getVerificationStatus(
            @RequestParam String email) {
        EmailAuthService.VerificationStatus status = emailAuthService.getVerificationStatus(email);
        return ApiResponse.success(status);
    }

    /**
     * 인증 코드 재발송
     * POST /api/email/verification/resend?email=test@example.com
     */
    @PostMapping("/verification/resend")
    public ApiResponse<Void> resendVerificationCode(@RequestParam String email) {
        emailAuthService.resendVerificationCode(email);
        return ApiResponse.success(null, "인증 코드가 재발송되었습니다.");
    }

    /**
     * 일반 이메일 발송 (관리자용)
     * POST /api/email/send
     */
    @PostMapping("/send")
    public ApiResponse<Void> sendEmail(@RequestBody EmailRequest request) {
        emailService.sendEmail(request);
        return ApiResponse.success();
    }
}
