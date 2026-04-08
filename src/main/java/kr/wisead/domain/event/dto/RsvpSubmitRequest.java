package kr.wisead.domain.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 통합 링크 RSVP 제출 요청. */
@Getter
@Setter
@NoArgsConstructor
public class RsvpSubmitRequest {

  @NotBlank(message = "응답값은 필수입니다.")
  @Pattern(regexp = "^(attend|absent)$", message = "응답값은 attend 또는 absent여야 합니다.")
  private String response;

  @NotBlank(message = "nonce는 필수입니다.")
  private String nonce;
}
