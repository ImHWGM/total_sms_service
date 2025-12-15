package kr.wisead.domain.email.controller;

import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.email.dto.EmailRequest;
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

    /**
     * 인증 코드 발송
     * POST /api/email/verification?email=test@example.com
     */
    @PostMapping("/verification")
    public ApiResponse<String> sendVerificationCode(@RequestParam String email) {
        String code = emailService.createVerificationCode();
        emailService.sendVerificationEmail(email, code);
        return ApiResponse.<String>success(code, "인증 코드가 발송되었습니다.");
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
