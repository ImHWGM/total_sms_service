package kr.wisead.domain.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 이메일 인증 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailVerificationRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;

    private String code;  // 인증 코드 검증 시 사용
}
