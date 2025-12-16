package kr.wisead.domain.survey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 휴대폰 번호 검증 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
public class PhoneValidationRequest {

    @NotBlank(message = "휴대폰 번호는 필수입니다.")
    @Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "올바른 휴대폰 번호 형식이 아닙니다.")
    private String phone;

    private String eventCode;       // 이벤트 코드로 검증 시
    private String authCodeUrl;     // QR 코드 URL로 검증 시
    private String userKey;         // 사용자 키 (재발송 검증 시)
}
