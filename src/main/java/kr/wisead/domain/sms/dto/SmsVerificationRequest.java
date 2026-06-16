package kr.wisead.domain.sms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 회원가입(사전 인증) SMS 인증 요청 DTO.
 *
 * <p>{@code PreSignupSmsAuthService} 라우팅용. 이메일판 {@code EmailVerificationRequest} 와 평행 구조이며,
 * email 대신 phoneNumber 를 받는다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsVerificationRequest {

  /** 인증 대상 휴대폰번호 (010-XXXX-XXXX 또는 01012345678). */
  @NotBlank(message = "휴대폰번호는 필수입니다.")
  @Pattern(regexp = "^010-?\\d{4}-?\\d{4}$", message = "올바른 휴대폰 번호 형식이 아닙니다.")
  private String phoneNumber;

  /** 인증 코드 (검증 시에만 사용). */
  private String code;
}
