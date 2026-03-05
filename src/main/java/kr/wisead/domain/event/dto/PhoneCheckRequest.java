package kr.wisead.domain.event.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** 전화번호 기반 체크인 요청 DTO */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PhoneCheckRequest {

  @NotBlank(message = "전화번호는 필수입니다.")
  private String phone;
}
