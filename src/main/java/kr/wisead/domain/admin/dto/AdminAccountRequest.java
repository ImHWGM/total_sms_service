package kr.wisead.domain.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자 계정 생성 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAccountRequest {

    @NotBlank(message = "사용자 ID는 필수입니다")
    private String userId;

    @NotBlank(message = "비밀번호는 필수입니다")
    private String userPass;

    @NotBlank(message = "비밀번호 확인은 필수입니다")
    private String userPassChk;

    @NotBlank(message = "업체명은 필수입니다")
    private String corpName;

    private String corpAddr;         // 업체 주소

    private String bizNum;           // 사업자 번호

    private String bizTel;           // 업체 연락처

    @NotBlank(message = "담당자명은 필수입니다")
    private String person;

    @NotBlank(message = "담당자 연락처는 필수입니다")
    private String phone;

    @Email(message = "올바른 이메일 형식이 아닙니다")
    private String email;

    @NotNull(message = "권한 레벨은 필수입니다")
    private Integer userLevel;       // 10: 기업관리자, 50: 운영관리자(B), 60: 운영관리자(A), 90: 최고관리자(B), 99: 최고관리자(A)

    // 비밀번호 힌트
    private String hintQuestion;
    private String hintAnswer;
}
