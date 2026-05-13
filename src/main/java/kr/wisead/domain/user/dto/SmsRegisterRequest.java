package kr.wisead.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 마이페이지 SMS 2FA 등록 — OTP 발송 요청 DTO.
 *
 * <p>plan v5 §4 Phase E-4.
 */
@Getter
@Setter
@NoArgsConstructor
public class SmsRegisterRequest {

  /** 등록 대상 휴대폰번호 (010-XXXX-XXXX 또는 01012345678). */
  @NotBlank(message = "휴대폰번호는 필수입니다.")
  @Pattern(regexp = "^010-?\\d{4}-?\\d{4}$", message = "올바른 휴대폰 번호 형식이 아닙니다.")
  private String phoneNumber;
}
