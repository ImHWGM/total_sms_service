package kr.wisead.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 마이페이지 SMS 2FA 등록 — OTP 검증 요청 DTO.
 *
 * <p>plan v5 §4 Phase E-4.
 */
@Getter
@Setter
@NoArgsConstructor
public class SmsVerifyRequest {

  /** 6자리 숫자 OTP. */
  @NotBlank(message = "인증 코드는 필수입니다.")
  @Pattern(regexp = "^\\d{6}$", message = "인증 코드는 6자리 숫자여야 합니다.")
  private String code;
}
