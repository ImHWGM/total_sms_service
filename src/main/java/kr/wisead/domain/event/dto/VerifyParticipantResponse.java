package kr.wisead.domain.event.dto;

import lombok.*;

/** 참가자 인증 응답 DTO */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyParticipantResponse {

  private boolean verified;
  private ParticipantInfo participant;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class ParticipantInfo {
    private Long seq;
    private String name;
    private String phone;
    private String department;
    private String position;
    private String participantType;
    private String checkCode;
    private String eventName;
    private String qrCodeUrl;
  }

  /** 인증 성공 응답 */
  public static VerifyParticipantResponse verified(ParticipantInfo participant) {
    return VerifyParticipantResponse.builder().verified(true).participant(participant).build();
  }

  /** 인증 실패 응답 */
  public static VerifyParticipantResponse notVerified() {
    return VerifyParticipantResponse.builder().verified(false).participant(null).build();
  }
}
