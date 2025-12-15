package kr.wisead.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 전화번호 마스킹 해제 로그 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhoneMaskingLogRequest {

    @NotBlank(message = "액션은 필수입니다")
    private String action;           // UNMASK, UNMASK_FAIL

    @NotBlank(message = "사유는 필수입니다")
    private String reason;           // 마스킹 해제 사유

    @NotBlank(message = "페이지 번호는 필수입니다")
    private String pageNumber;       // 페이지 번호
}
