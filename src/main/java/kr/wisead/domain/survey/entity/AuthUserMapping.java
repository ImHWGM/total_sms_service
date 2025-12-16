package kr.wisead.domain.survey.entity;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 범용인증 사용자 매핑 Entity (AUTH_USER_MAPPING)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AuthUserMapping {

    private Integer eventSeq;               // 이벤트 시퀀스
    private Integer userSeq;                // 사용자 시퀀스
    private String userKey;                 // 사용자 키
    private Integer authKey;                // 인증키
    private String authCode;                // 인증코드
    private String generalAuthCode;         // 범용인증코드
    private String regId;                   // 등록 ID
    private LocalDateTime regDate;          // 등록일

    /**
     * 범용인증 매핑 생성
     */
    public static AuthUserMapping create(Integer eventSeq, Integer userSeq,
                                          String userKey, String generalAuthCode, String regId) {
        return AuthUserMapping.builder()
                .eventSeq(eventSeq)
                .userSeq(userSeq)
                .userKey(userKey)
                .generalAuthCode(generalAuthCode)
                .regId(regId)
                .build();
    }
}
