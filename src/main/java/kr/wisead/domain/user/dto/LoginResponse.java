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

    @Getter
    @Builder
    public static class UserInfo {
        private Long seq;
        private String userId;
        private String corpName;
        private String person;
        private String email;
        private Integer userLevel;
        private String status;
    }
}
