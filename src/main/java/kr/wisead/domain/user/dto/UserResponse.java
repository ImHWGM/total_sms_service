package kr.wisead.domain.user.dto;

import kr.wisead.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 회원 정보 응답 DTO
 */
@Getter
@Builder
public class UserResponse {

    private Long seq;
    private String userId;
    private String corpName;
    private String corpAddr;
    private String bizNum;
    private String bizTel;
    private String person;
    private String phone;
    private String email;
    private Integer userLevel;
    private String status;
    private String useYn;
    private LocalDateTime lastLogin;
    private LocalDateTime regDate;
    private String callback;

    /**
     * Entity -> DTO 변환
     */
    public static UserResponse from(User user) {
        return UserResponse.builder()
                .seq(user.getSeq())
                .userId(user.getUserId())
                .corpName(user.getCorpName())
                .corpAddr(user.getCorpAddr())
                .bizNum(user.getBizNum())
                .bizTel(user.getBizTel())
                .person(user.getPerson())
                .phone(user.getPhone())
                .email(user.getEmail())
                .userLevel(user.getUserLevel())
                .status(user.getStatus())
                .useYn(user.getUseYn())
                .lastLogin(user.getLastLogin())
                .regDate(user.getRegDate())
                .callback(user.getCallback())
                .build();
    }
}
