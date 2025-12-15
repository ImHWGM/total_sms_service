package kr.wisead.domain.inquiry.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 문의 등록 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InquiryRequest {

    @NotBlank(message = "회사명은 필수입니다.")
    @Size(max = 128, message = "회사명은 128자 이내로 입력해주세요.")
    private String companyName;

    @Size(max = 64, message = "신청자명은 64자 이내로 입력해주세요.")
    private String applicantName;

    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 256, message = "이메일은 256자 이내로 입력해주세요.")
    private String email;

    @Size(max = 128, message = "연락처는 128자 이내로 입력해주세요.")
    private String contact;

    private Integer inquiryType;        // 문의 유형

    @Size(max = 4096, message = "문의 내용은 4096자 이내로 입력해주세요.")
    private String content;
}
