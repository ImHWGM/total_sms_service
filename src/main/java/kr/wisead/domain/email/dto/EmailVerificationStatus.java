package kr.wisead.domain.email.dto;

/**
 * 이메일 인증 상태 응답 (공통 DTO).
 *
 * <p>회원가입 도메인({@code PreSignupEmailAuthService}, key=email)과 로그인 도메인({@code EmailAuthService},
 * key=userId)이 공유한다. 응답 스키마 회귀를 0으로 유지하기 위해 기존 {@code EmailAuthService.VerificationStatus} 내부
 * record의 필드/타입을 그대로 옮긴 것이다.
 *
 * <p>plan §4 Phase B-0-1.
 */
public record EmailVerificationStatus(
    boolean codeSent, long remainingSeconds, long remainingResendSeconds, int remainingAttempts) {}
