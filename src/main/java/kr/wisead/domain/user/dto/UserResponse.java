package kr.wisead.domain.user.dto;

import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * 회원 정보 응답 DTO
 */
@Slf4j
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
    private String userLevelName;
    private String status;
    private String useYn;
    private LocalDateTime lastLogin;
    private LocalDateTime regDate;
    private String callback;

    /**
     * Entity -> DTO 변환 (개인정보 복호화 포함)
     */
    public static UserResponse from(User user) {
        return UserResponse.builder()
                .seq(user.getSeq())
                .userId(user.getUserId())
                .corpName(user.getCorpName())
                .corpAddr(user.getCorpAddr())
                .bizNum(user.getBizNum())
                .bizTel(user.getBizTel())
                .person(decryptField(user.getPerson()))
                .phone(decryptField(user.getPhone()))
                .email(decryptField(user.getEmail()))
                .userLevel(user.getUserLevel())
                .status(user.getStatus())
                .useYn(user.getUseYn())
                .lastLogin(user.getLastLogin())
                .regDate(user.getRegDate())
                .callback(user.getCallback())
                .build();
    }

    /**
     * 암호화된 필드 복호화 (AES256 + Base64)
     * 복호화 실패 시 원본 값 반환
     */
    private static String decryptField(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            return encryptedValue;
        }
        try {
            return CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(encryptedValue));
        } catch (Exception e) {
            log.debug("필드 복호화 실패, 원본 반환: {}", e.getMessage());
            return encryptedValue;
        }
    }
}
