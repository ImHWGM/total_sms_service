package kr.wisead.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 현재 2FA 설정 응답 DTO — 마이페이지 UI 노출용 (C10).
 *
 * <p>plan v5 §4 Phase E-4.
 */
@Getter
@Builder
public class TwoFactorSettingsResponse {

  /** 기본 인증 채널: "EMAIL" 또는 "SMS". */
  private final String defaultChannel;

  /** SMS 채널 등록 여부 (login_phone 존재 시 true). */
  private final boolean smsRegistered;

  /** 마스킹된 등록 휴대폰번호 (smsRegistered=false 시 null). */
  private final String maskedPhone;

  /** 마스킹된 이메일. */
  private final String maskedEmail;
}
