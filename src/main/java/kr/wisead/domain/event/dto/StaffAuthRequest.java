package kr.wisead.domain.event.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 스태프 인증코드 검증 요청 DTO */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StaffAuthRequest {

  @NotBlank(message = "인증코드를 입력해주세요.")
  private String authCode;
}
