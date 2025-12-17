package kr.wisead.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 비밀번호 찾기 응답 DTO
 */
@Getter
@Builder
public class FindPasswordResponse {

    /**
     * 결과 코드
     * 1: 성공 (비밀번호 재설정 링크 발송)
     * 2: 계정 정보 없음
     * 3: 비밀번호 힌트 불일치
     */
    private int resultCode;

    /**
     * 결과 메시지
     */
    private String message;

    /**
     * 마스킹된 이메일 (성공 시 재설정 링크가 발송된 이메일)
     * 예: te***@example.com
     */
    private String maskedEmail;

    /**
     * 성공 - 비밀번호 재설정 링크 발송
     */
    public static FindPasswordResponse success(String maskedEmail) {
        return FindPasswordResponse.builder()
                .resultCode(1)
                .message("비밀번호 재설정 링크가 이메일로 발송되었습니다.")
                .maskedEmail(maskedEmail)
                .build();
    }

    /**
     * 실패 - 계정 정보 없음
     */
    public static FindPasswordResponse accountNotFound() {
        return FindPasswordResponse.builder()
                .resultCode(2)
                .message("입력하신 정보와 일치하는 계정이 없습니다.")
                .build();
    }

    /**
     * 실패 - 비밀번호 힌트 불일치
     */
    public static FindPasswordResponse hintMismatch() {
        return FindPasswordResponse.builder()
                .resultCode(3)
                .message("비밀번호 힌트가 일치하지 않습니다.")
                .build();
    }
}
