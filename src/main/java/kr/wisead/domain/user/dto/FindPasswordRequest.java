package kr.wisead.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 비밀번호 찾기 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
public class FindPasswordRequest {

    /**
     * 사용자 아이디
     */
    @NotBlank(message = "아이디를 입력해주세요.")
    private String userId;

    /**
     * 기업명
     */
    @NotBlank(message = "기업명을 입력해주세요.")
    private String corpName;

    /**
     * 담당자명 (암호화되어 전송됨)
     */
    @NotBlank(message = "담당자명을 입력해주세요.")
    private String person;

    /**
     * 담당자 연락처 (암호화되어 전송됨)
     */
    @NotBlank(message = "연락처를 입력해주세요.")
    private String phone;

    /**
     * 비밀번호 힌트 질문
     */
    @NotBlank(message = "비밀번호 힌트 질문을 선택해주세요.")
    private String hintQuestion;

    /**
     * 비밀번호 힌트 답변
     */
    @NotBlank(message = "비밀번호 힌트 답변을 입력해주세요.")
    private String hintAnswer;
}
