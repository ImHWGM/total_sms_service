package kr.wisead.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 요청 DTO
 */
@Getter
@NoArgsConstructor
public class LoginRequest {

    @NotBlank(message = "아이디를 입력해주세요.")
    @Size(max = 20, message = "아이디는 20자 이하로 입력해주세요.")
    private String userId;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    private String userPass;

    // 이메일 인증 코드 (2단계 인증 시 사용)
    private String emailCode;

    @Builder
    public LoginRequest(String userId, String userPass, String emailCode) {
        this.userId = userId;
        this.userPass = userPass;
        this.emailCode = emailCode;
    }
}
