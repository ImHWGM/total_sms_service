package kr.wisead.domain.inquiry.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 문의 답변 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InquiryAnswerRequest {

    @NotBlank(message = "답변 내용은 필수입니다.")
    private String answer;

    private boolean sendEmail;      // 이메일 발송 여부
}
