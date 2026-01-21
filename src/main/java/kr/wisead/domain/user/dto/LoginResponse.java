package kr.wisead.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 로그인 응답 DTO
 */
@Getter
@Builder
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private UserInfo user;

    // 이메일 인증 관련 필드
    private Boolean emailRequired;      // 이메일 인증 필요 여부
    private String maskedEmail;         // 마스킹된 이메일 (예: abc***@example.com)

    @Getter
    @Builder
    public static class UserInfo {
        private Integer seq;
        private String userId;
        private String corpName;
        private String person;
        private String email;
        private Integer userLevel;
        private String status;
    }
}
