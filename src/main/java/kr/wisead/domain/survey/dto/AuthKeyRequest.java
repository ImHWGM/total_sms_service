package kr.wisead.domain.survey.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * 범용인증키 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthKeyRequest {

    private Integer eventSeq;               // 이벤트 시퀀스

    @NotBlank(message = "인증코드는 필수입니다.")
    private String authCode;                // 인증코드

    private String authKeyDesc;             // 인증키 설명 문구
}
