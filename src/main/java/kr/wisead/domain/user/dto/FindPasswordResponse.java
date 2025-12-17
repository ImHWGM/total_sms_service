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
     * 1: 성공 (임시 비밀번호 발급)
     * 2: 계정 정보 없음
     * 3: 비밀번호 힌트 불일치
     */
    private int resultCode;

    /**
     * 결과 메시지
     */
    private String message;

    /**
     * 임시 비밀번호 (성공 시에만 반환)
     */
    private String tempPassword;

    public static FindPasswordResponse success(String tempPassword) {
        return FindPasswordResponse.builder()
                .resultCode(1)
                .message("임시 비밀번호가 발급되었습니다.")
                .tempPassword(tempPassword)
                .build();
    }

    public static FindPasswordResponse accountNotFound() {
        return FindPasswordResponse.builder()
                .resultCode(2)
                .message("계정 정보가 없습니다.")
                .build();
    }

    public static FindPasswordResponse hintMismatch() {
        return FindPasswordResponse.builder()
                .resultCode(3)
                .message("비밀번호 힌트를 확인해주세요.")
                .build();
    }
}
