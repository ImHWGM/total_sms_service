package kr.wisead.domain.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 행사 통합 링크 페이지 상태 응답. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedLinkStateResponse {

  /** "RSVP" | "RSVP_ANSWERED_ATTEND" | "RSVP_ANSWERED_ABSENT" | "QR_ONLY" */
  private String mode;

  private ParticipantSummary participant;

  /** mode가 QR_ONLY 또는 RSVP_ANSWERED_ATTEND일 때만 채워짐 */
  private String qrCodeUrl;

  /** 한국어 registType (nullable) */
  private String registType;

  private Boolean preSurveyActive;

  /** RSVP/RSVP_ANSWERED_* 모드에서 발급되는 5분 TTL nonce */
  private String nonce;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ParticipantSummary {
    private String name;
    private String checkCode;
  }
}
