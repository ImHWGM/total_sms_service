package kr.wisead.domain.verification.entity;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사전 인증(로그인 전) 인증 상태 Entity. verification 테이블 매핑.
 *
 * <p>채널 무관 공용 — SMS(휴대폰)·EMAIL(이메일) 사전 인증이 한 테이블을 공유한다. (purpose, channel, identifier)
 * 조합이 유일하다. {@code verifiedAt} 이 null 이면 "코드 발송/검증 대기", not-null 이면 "인증 완료(도장)" 상태.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Verification {

  private Integer seq; // PK (대리키, auto-increment)
  private String purpose; // 용도 (예: SIGNUP, SMS_2FA)
  private String channel; // 채널 (SMS | EMAIL)
  private String identifier; // 대상 식별자 (정규화 휴대폰번호, 이메일, 또는 userId)
  private String target; // SMS 발송 대상 (마이페이지 SMS 등록 시 전화번호; nullable)
  private String code; // 인증코드 (검증 성공/만료 시 null)
  private int attempts; // 검증 시도 횟수
  private LocalDateTime createdAt; // 코드 발송 시각
  private LocalDateTime verifiedAt; // 인증 완료 시각 (null 이면 미인증)
}
