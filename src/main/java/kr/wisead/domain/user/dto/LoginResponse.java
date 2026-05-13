package kr.wisead.domain.user.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** 로그인 응답 DTO */
@Getter
@Builder
public class LoginResponse {

  private String accessToken;
  private String refreshToken;
  private Long expiresIn;
  private UserInfo user;

  // 2FA 채널 관련 필드 (plan v5 §4 Phase C)
  private String channel; // "EMAIL" or "SMS" (OTP 발송 채널)
  private String maskedEmail; // 마스킹된 이메일 (예: abc***@example.com) - EMAIL 채널
  private String maskedPhone; // 마스킹된 휴대폰 (예: 010-****-1234) - SMS 채널
  private List<String> availableChannels; // 사용자가 선택 가능한 채널 목록 (마이페이지/스위치 UI용)

  // sessionKey (plan v5 §4 Phase D) — 1차 인증 통과 후 OTP 검증 직전 단계의 임시 토큰. 7분 만료.
  private String sessionKey;

  @Getter
  @Builder
  public static class UserInfo {
    private Integer seq;
    private String userId;
    private String corpName;
    private String person;
    private String email;
    private Integer userLevel;
    private String status;
  }
}
