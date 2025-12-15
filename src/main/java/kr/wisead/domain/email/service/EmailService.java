package kr.wisead.domain.email.service;

import kr.wisead.domain.email.dto.EmailRequest;
import kr.wisead.domain.inquiry.entity.Inquiry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * 이메일 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final HiworksMailService hiworksMailService;

    @Value("${mail.notify.to:sales@enmad.com}")
    private String notifyEmail;

    /**
     * 인증 코드 생성 (8자리 영문+숫자)
     */
    public String createVerificationCode() {
        StringBuilder key = new StringBuilder();
        SecureRandom rnd = new SecureRandom();

        for (int i = 0; i < 8; i++) {
            int index = rnd.nextInt(3);
            switch (index) {
                case 0 -> key.append((char) (rnd.nextInt(26) + 97)); // a~z
                case 1 -> key.append((char) (rnd.nextInt(26) + 65)); // A~Z
                case 2 -> key.append(rnd.nextInt(10)); // 0~9
            }
        }

        return key.toString();
    }

    /**
     * 인증 코드 이메일 발송
     */
    public void sendVerificationEmail(String to, String code) {
        String subject = "[WiseAd] 이메일 인증코드";
        String content = buildVerificationEmailContent(code);

        EmailRequest request = EmailRequest.builder()
                .to(to)
                .subject(subject)
                .content(content)
                .saveSentMail(false)
                .build();

        hiworksMailService.send(request);
        log.info("인증 코드 이메일 발송 완료: to={}", to);
    }

    /**
     * 문의 접수 확인 이메일 발송 (문의자에게)
     */
    public void sendInquiryConfirmation(String to) {
        String subject = "[WiseAd] 문의가 접수되었습니다";
        String content = buildInquiryConfirmationContent();

        EmailRequest request = EmailRequest.builder()
                .to(to)
                .subject(subject)
                .content(content)
                .saveSentMail(false)
                .build();

        hiworksMailService.send(request);
        log.info("문의 접수 확인 이메일 발송 완료: to={}", to);
    }

    /**
     * 문의 접수 알림 이메일 발송 (관리자에게)
     */
    public void sendInquiryNotification(Inquiry inquiry) {
        String subject = "[WiseAd] 새로운 문의가 접수되었습니다";
        String content = buildInquiryNotificationContent(inquiry);

        EmailRequest request = EmailRequest.builder()
                .to(notifyEmail)
                .subject(subject)
                .content(content)
                .saveSentMail(false)
                .build();

        hiworksMailService.send(request);
        log.info("문의 알림 이메일 발송 완료: to={}", notifyEmail);
    }

    /**
     * 문의 답변 알림 이메일 발송 (문의자에게)
     */
    public void sendInquiryAnswerNotification(String to, String answer) {
        String subject = "[WiseAd] 문의에 대한 답변이 등록되었습니다";
        String content = buildInquiryAnswerContent(answer);

        EmailRequest request = EmailRequest.builder()
                .to(to)
                .subject(subject)
                .content(content)
                .saveSentMail(false)
                .build();

        hiworksMailService.send(request);
        log.info("문의 답변 알림 이메일 발송 완료: to={}", to);
    }

    /**
     * 일반 이메일 발송
     */
    public void sendEmail(EmailRequest request) {
        hiworksMailService.send(request);
        log.info("이메일 발송 완료: to={}", request.getTo());
    }

    // ==================== Private Methods ====================

    private String buildVerificationEmailContent(String code) {
        return """
            <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                <h1 style="font-size: 24px; color: #333; margin-bottom: 20px;">이메일 인증</h1>
                <p style="font-size: 16px; color: #666; margin-bottom: 30px;">
                    아래 인증 코드를 5분 이내에 입력해주세요.
                </p>
                <div style="background-color: #f4f4f4; padding: 20px; text-align: center; border-radius: 8px;">
                    <span style="font-size: 32px; font-weight: bold; color: #333; letter-spacing: 4px;">%s</span>
                </div>
                <p style="font-size: 14px; color: #999; margin-top: 30px;">
                    본 메일은 발신 전용입니다.
                </p>
            </div>
            """.formatted(code);
    }

    private String buildInquiryConfirmationContent() {
        return """
            <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                <h1 style="font-size: 24px; color: #333; margin-bottom: 20px;">문의가 접수되었습니다</h1>
                <p style="font-size: 16px; color: #666; margin-bottom: 20px;">
                    문의해 주셔서 감사합니다.
                </p>
                <p style="font-size: 16px; color: #666;">
                    빠른 시일 내에 담당자가 연락드리겠습니다.
                </p>
            </div>
            """;
    }

    private String buildInquiryNotificationContent(Inquiry inquiry) {
        String typeName = switch (inquiry.getInquiryType() != null ? inquiry.getInquiryType() : 4) {
            case 1 -> "서비스 문의";
            case 2 -> "기술 문의";
            case 3 -> "결제 문의";
            default -> "기타";
        };

        return """
            <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                <h1 style="font-size: 24px; color: #333; margin-bottom: 20px;">새로운 문의가 접수되었습니다</h1>
                <table style="width: 100%%; border-collapse: collapse;">
                    <tr>
                        <td style="padding: 10px; border-bottom: 1px solid #eee; font-weight: bold; width: 100px;">문의유형</td>
                        <td style="padding: 10px; border-bottom: 1px solid #eee;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 10px; border-bottom: 1px solid #eee; font-weight: bold;">회사명</td>
                        <td style="padding: 10px; border-bottom: 1px solid #eee;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 10px; border-bottom: 1px solid #eee; font-weight: bold;">신청자</td>
                        <td style="padding: 10px; border-bottom: 1px solid #eee;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 10px; border-bottom: 1px solid #eee; font-weight: bold;">이메일</td>
                        <td style="padding: 10px; border-bottom: 1px solid #eee;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 10px; border-bottom: 1px solid #eee; font-weight: bold;">연락처</td>
                        <td style="padding: 10px; border-bottom: 1px solid #eee;">%s</td>
                    </tr>
                    <tr>
                        <td style="padding: 10px; font-weight: bold; vertical-align: top;">내용</td>
                        <td style="padding: 10px;">%s</td>
                    </tr>
                </table>
            </div>
            """.formatted(
                typeName,
                inquiry.getCompanyName(),
                inquiry.getApplicantName(),
                inquiry.getEmail(),
                inquiry.getContact(),
                inquiry.getContent()
        );
    }

    private String buildInquiryAnswerContent(String answer) {
        return """
            <div style="max-width: 600px; margin: 0 auto; padding: 20px;">
                <h1 style="font-size: 24px; color: #333; margin-bottom: 20px;">문의에 대한 답변</h1>
                <p style="font-size: 16px; color: #666; margin-bottom: 20px;">
                    문의해 주셔서 감사합니다. 아래와 같이 답변드립니다.
                </p>
                <div style="background-color: #f9f9f9; padding: 20px; border-radius: 8px; border-left: 4px solid #007bff;">
                    <p style="font-size: 16px; color: #333; white-space: pre-wrap;">%s</p>
                </div>
                <p style="font-size: 14px; color: #999; margin-top: 30px;">
                    추가 문의사항이 있으시면 언제든지 연락주세요.
                </p>
            </div>
            """.formatted(answer);
    }
}
