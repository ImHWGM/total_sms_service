package kr.wisead.common.dto;

/**
 * 인증 코드 상태 응답 (공통 DTO).
 *
 * <p>이메일/SMS, 사전 인증/로그인(2FA) 도메인이 채널·식별자에 무관하게 공유한다. 사전 인증 이메일({@code
 * EmailVerificationService}, key=email), 로그인 이메일({@code EmailAuthService}, key=userId), 사전 인증
 * SMS({@code SmsVerificationService}, key=purpose:phone), 로그인 SMS({@code SmsAuthService}, key=userId)가
 * 동일 스키마로 응답하기 위해 사용한다.
 */
public record VerificationStatus(
    boolean codeSent, long remainingSeconds, long remainingResendSeconds, int remainingAttempts) {}
