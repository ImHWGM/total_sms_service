package kr.wisead.domain.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** RSVP 제출 요청 DTO */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RsvpRequest {

  @NotBlank(message = "전화번호는 필수입니다.")
  private String phone;

  @NotBlank(message = "응답은 필수입니다.")
  @Pattern(
      regexp = "^(사전등록|불참석|attend|preregister|absent)$",
      message = "응답은 '사전등록(attend)' 또는 '불참석(absent)'만 가능합니다.")
  private String response;
}
