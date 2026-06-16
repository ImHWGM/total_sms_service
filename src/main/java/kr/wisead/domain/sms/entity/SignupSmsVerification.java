package kr.wisead.domain.sms.entity;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원가입 SMS 본인인증 상태 Entity. signup_sms_verification 테이블 매핑.
 *
 * <p>(purpose, phone) 복합 PK 로 한 번호의 한 용도당 1행을 유지한다. {@code verifiedAt} 이 null 이면 "코드 발송/검증 대기" 상태,
 * not-null 이면 "인증 완료(도장)" 상태이다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SignupSmsVerification {

  private String purpose; // OTP 용도 (예: SIGNUP)
  private String phone; // 정규화 휴대폰번호 (숫자만)
  private String code; // 6자리 인증코드 (검증 성공/만료 시 null)
  private int attempts; // 검증 시도 횟수
  private LocalDateTime createdAt; // 코드 발송 시각
  private LocalDateTime verifiedAt; // 인증 완료 시각 (null 이면 미인증)
}
