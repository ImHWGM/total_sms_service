package kr.wisead.domain.user.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 아이디 찾기 응답 DTO
 */
@Getter
@Builder
public class FindIdResponse {

    /**
     * 결과 코드
     * 1: 성공 (이메일 인증코드 발송 또는 아이디 반환)
     * 2: 계정 정보 없음
     */
    private int resultCode;

    /**
     * 결과 메시지
     */
    private String message;

    /**
     * 마스킹된 이메일 (인증코드 발송용)
     * 예: te***@example.com
     */
    private String maskedEmail;

    /**
     * 마스킹된 아이디 (인증 완료 후 반환)
     * 예: tes*****
     */
    private String maskedUserId;

    /**
     * 1단계 성공 - 인증코드 발송
     */
    public static FindIdResponse requestSuccess(String maskedEmail) {
        return FindIdResponse.builder()
                .resultCode(1)
                .message("인증 코드가 발송되었습니다.")
                .maskedEmail(maskedEmail)
                .build();
    }

    /**
     * 2단계 성공 - 아이디 반환
     */
    public static FindIdResponse verifySuccess(String maskedUserId) {
        return FindIdResponse.builder()
                .resultCode(1)
                .message("아이디 조회가 완료되었습니다.")
                .maskedUserId(maskedUserId)
                .build();
    }

    /**
     * 실패 - 계정 정보 없음
     */
    public static FindIdResponse accountNotFound() {
        return FindIdResponse.builder()
                .resultCode(2)
                .message("입력하신 정보와 일치하는 계정이 없습니다.")
                .build();
    }

    /**
     * 실패 - 인증 실패
     */
    public static FindIdResponse verificationFailed() {
        return FindIdResponse.builder()
                .resultCode(3)
                .message("인증 코드가 일치하지 않습니다.")
                .build();
    }
}
