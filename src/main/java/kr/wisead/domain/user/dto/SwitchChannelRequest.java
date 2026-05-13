package kr.wisead.domain.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 로그인 OTP 채널 전환 요청 DTO (EMAIL ↔ SMS).
 *
 * <p>plan v5 §4 Phase D. sessionKey 기반 인증으로 마이그레이션 완료 (#v3-4).
 */
@Getter
@Setter
@NoArgsConstructor
public class SwitchChannelRequest {

  /** 1차 인증 통과 후 발급된 임시 세션 토큰 (HMAC-SHA256, 7분 만료). */
  private String sessionKey;

  /** 전환 대상 채널: "EMAIL" 또는 "SMS". */
  private String targetChannel;
}
