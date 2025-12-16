package kr.wisead.domain.survey.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가상 키패드 응답 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeypadResponse {

    private String publicKey;       // RSA 공개키 (Base64)
    private String keypadId;        // 키패드 세션 ID
    private long expiresAt;         // 만료 시간 (timestamp)
}
