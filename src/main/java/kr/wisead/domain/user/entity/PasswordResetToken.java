package kr.wisead.domain.user.entity;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 비밀번호 재설정 토큰 Entity
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    /**
     * 토큰 시퀀스
     */
    private Long seq;

    /**
     * 사용자 아이디
     */
    private String userId;

    /**
     * 재설정 토큰 (UUID)
     */
    private String token;

    /**
     * 만료일시
     */
    private LocalDateTime expireDate;

    /**
     * 사용여부 (Y/N)
     */
    private String usedYn;

    /**
     * 등록일
     */
    private LocalDateTime regDate;

    /**
     * 토큰 만료 여부 확인
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expireDate);
    }

    /**
     * 토큰 사용 여부 확인
     */
    public boolean isUsed() {
        return "Y".equals(usedYn);
    }

    /**
     * 토큰 유효성 확인 (만료되지 않았고, 사용되지 않음)
     */
    public boolean isValid() {
        return !isExpired() && !isUsed();
    }
}
