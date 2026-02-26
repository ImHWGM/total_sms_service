package kr.wisead.domain.survey.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

/**
 * 설문 제출 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveySubmitRequest {

    @NotBlank(message = "사용자 키는 필수입니다.")
    private String userKey;                 // 사용자 키

    // 개인정보 (선택)
    private String userName;                // 이름
    private String juminNum;                // 주민번호
    private String userPhone;               // 휴대전화
    private String userEmail;               // 이메일
    private String address;                 // 기본주소
    private String address2;                // 상세주소

    // 설문 응답 목록
    private List<AnswerRequest> answers;

    /**
     * 답변 요청 DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AnswerRequest {
        private Integer questionSeq;        // 문항 시퀀스
        private String questionType;        // 문항 종류
        private String questionTypeDetail;  // 문항 종류 상세
        private Integer itemSeq;            // 선택한 항목 시퀀스 (객관식)
        private String answer;              // 답변 내용 (주관식/객관식 값)
        private String keypadId;            // SO 답변 RSA 키패드 세션 ID
        private String filePath;            // 파일 경로 (파일 업로드)
    }
}
