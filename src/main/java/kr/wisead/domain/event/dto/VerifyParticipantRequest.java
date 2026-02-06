package kr.wisead.domain.event.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** 참가자 인증 요청 DTO */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VerifyParticipantRequest {

  @NotBlank(message = "이름을 입력해주세요.")
  private String name;

  @NotBlank(message = "연락처를 입력해주세요.")
  private String phone;
}
