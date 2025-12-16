package kr.wisead.domain.message.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 재발송 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResendResponse {

    private int resultCode;                 // 결과 코드 (1: 성공, -1: 실패)
    private String message;                 // 결과 메시지
    private int successCount;               // 성공 건수
    private int failCount;                  // 실패 건수
    private List<String> failedNumbers;     // 실패한 번호 목록

    public static ResendResponse success(int successCount) {
        return ResendResponse.builder()
                .resultCode(1)
                .message("재발송 완료: " + successCount + "건")
                .successCount(successCount)
                .failCount(0)
                .build();
    }

    public static ResendResponse partial(int successCount, int failCount, List<String> failedNumbers) {
        return ResendResponse.builder()
                .resultCode(2)
                .message("재발송 부분 완료: 성공 " + successCount + "건, 실패 " + failCount + "건")
                .successCount(successCount)
                .failCount(failCount)
                .failedNumbers(failedNumbers)
                .build();
    }

    public static ResendResponse fail(String message) {
        return ResendResponse.builder()
                .resultCode(-1)
                .message(message)
                .successCount(0)
                .failCount(0)
                .build();
    }
}
