package kr.wisead.domain.email.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 이메일 인증 서비스
 * - 인증 코드 발송 및 검증
 * - 인메모리 캐시 사용 (유효시간 5분)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAuthService {

    private final EmailService emailService;

    // 인증 코드 저장소 (이메일 -> 인증정보)
    private final Map<String, VerificationInfo> verificationStore = new ConcurrentHashMap<>();

    // 인증 코드 유효 시간 (5분)
    private static final int EXPIRATION_MINUTES = 5;

    // 재발송 제한 시간 (1분)
    private static final int RESEND_LIMIT_SECONDS = 60;

    // 최대 시도 횟수
    private static final int MAX_ATTEMPTS = 5;

    /**
     * 인증 코드 발송
     * @param email 수신자 이메일
     * @return 성공 여부
     */
    public boolean sendVerificationCode(String email) {
        // 이메일 형식 검증
        if (!isValidEmail(email)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 이메일 형식입니다.");
        }

        // 재발송 제한 확인
        VerificationInfo existingInfo = verificationStore.get(email);
        if (existingInfo != null && !existingInfo.canResend()) {
            long remainingSeconds = existingInfo.getRemainingResendSeconds();
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    String.format("재발송은 %d초 후에 가능합니다.", remainingSeconds));
        }

        // 인증 코드 생성
        String code = emailService.createVerificationCode();

        // 인증 정보 저장
        VerificationInfo info = new VerificationInfo(code, LocalDateTime.now());
        verificationStore.put(email.toLowerCase(), info);

        // 이메일 발송
        try {
            emailService.sendVerificationEmail(email, code);
            log.info("인증 코드 발송 완료: email={}", maskEmail(email));
            return true;
        } catch (Exception e) {
            log.error("인증 코드 발송 실패: email={}", maskEmail(email), e);
            verificationStore.remove(email.toLowerCase());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "인증 코드 발송에 실패했습니다.");
        }
    }

    /**
     * 인증 코드 검증
     * @param email 이메일
     * @param code 인증 코드
     * @return 검증 성공 여부
     */
    public boolean verifyCode(String email, String code) {
        String normalizedEmail = email.toLowerCase();
        VerificationInfo info = verificationStore.get(normalizedEmail);

        // 인증 정보 존재 여부 확인
        if (info == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드를 먼저 발송해주세요.");
        }

        // 만료 여부 확인
        if (info.isExpired()) {
            verificationStore.remove(normalizedEmail);
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 코드가 만료되었습니다. 다시 발송해주세요.");
        }

        // 시도 횟수 확인
        if (info.getAttempts() >= MAX_ATTEMPTS) {
            verificationStore.remove(normalizedEmail);
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "인증 시도 횟수를 초과했습니다. 다시 발송해주세요.");
        }

        // 코드 검증
        info.incrementAttempts();
        if (!info.getCode().equals(code)) {
            log.warn("인증 코드 불일치: email={}, attempts={}", maskEmail(email), info.getAttempts());
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    String.format("인증 코드가 일치하지 않습니다. (남은 시도: %d회)", MAX_ATTEMPTS - info.getAttempts()));
        }

        // 인증 성공 - 저장소에서 제거
        verificationStore.remove(normalizedEmail);
        log.info("이메일 인증 성공: email={}", maskEmail(email));
        return true;
    }

    /**
     * 인증 상태 확인
     * @param email 이메일
     * @return 인증 코드 발송 여부 및 남은 시간
     */
    public VerificationStatus getVerificationStatus(String email) {
        VerificationInfo info = verificationStore.get(email.toLowerCase());
        if (info == null) {
            return new VerificationStatus(false, 0, 0, 0);
        }

        long remainingSeconds = info.getRemainingSeconds();
        long remainingResendSeconds = info.getRemainingResendSeconds();
        int remainingAttempts = MAX_ATTEMPTS - info.getAttempts();

        return new VerificationStatus(true, remainingSeconds, remainingResendSeconds, remainingAttempts);
    }

    /**
     * 인증 코드 재발송 (기존 코드 무효화)
     * @param email 이메일
     * @return 성공 여부
     */
    public boolean resendVerificationCode(String email) {
        verificationStore.remove(email.toLowerCase());
        return sendVerificationCode(email);
    }

    /**
     * 만료된 인증 정보 정리 (5분마다 실행)
     */
    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredCodes() {
        int before = verificationStore.size();
        verificationStore.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int after = verificationStore.size();
        if (before != after) {
            log.debug("만료된 인증 코드 정리: {} -> {}", before, after);
        }
    }

    // ==================== Private Methods ====================

    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf("@");
        if (atIndex <= 3) {
            return email.charAt(0) + "***" + email.substring(atIndex);
        }
        return email.substring(0, 3) + "***" + email.substring(atIndex);
    }

    // ==================== Inner Classes ====================

    /**
     * 인증 정보
     */
    private static class VerificationInfo {
        private final String code;
        private final LocalDateTime createdAt;
        private int attempts;

        public VerificationInfo(String code, LocalDateTime createdAt) {
            this.code = code;
            this.createdAt = createdAt;
            this.attempts = 0;
        }

        public String getCode() {
            return code;
        }

        public int getAttempts() {
            return attempts;
        }

        public void incrementAttempts() {
            this.attempts++;
        }

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(createdAt.plusMinutes(EXPIRATION_MINUTES));
        }

        public boolean canResend() {
            return LocalDateTime.now().isAfter(createdAt.plusSeconds(RESEND_LIMIT_SECONDS));
        }

        public long getRemainingSeconds() {
            LocalDateTime expiresAt = createdAt.plusMinutes(EXPIRATION_MINUTES);
            return java.time.Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
        }

        public long getRemainingResendSeconds() {
            LocalDateTime canResendAt = createdAt.plusSeconds(RESEND_LIMIT_SECONDS);
            long remaining = java.time.Duration.between(LocalDateTime.now(), canResendAt).getSeconds();
            return Math.max(0, remaining);
        }
    }

    /**
     * 인증 상태 응답
     */
    public record VerificationStatus(
            boolean codeSent,
            long remainingSeconds,
            long remainingResendSeconds,
            int remainingAttempts
    ) {}
}
