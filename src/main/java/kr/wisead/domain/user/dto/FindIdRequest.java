package kr.wisead.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 아이디 찾기 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FindIdRequest {

    /**
     * 기업명 (CORP_NAME)
     */
    @NotBlank(message = "기업명을 입력해주세요.")
    private String corpName;

    /**
     * 담당자명 (PERSON) - 암호화되어 전송됨
     */
    @NotBlank(message = "담당자명을 입력해주세요.")
    private String person;

    /**
     * 담당자 연락처 (PHONE) - 암호화되어 전송됨
     */
    @NotBlank(message = "연락처를 입력해주세요.")
    private String phone;
}
