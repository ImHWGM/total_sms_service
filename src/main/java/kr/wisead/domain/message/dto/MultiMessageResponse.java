package kr.wisead.domain.message.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 일반 문자 발송 응답 DTO (Multi Message)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultiMessageResponse {

    private int resultCode;         // 결과 코드 (1: 성공, -1: 실패, -2: 특수문자 확인 필요, -3: 잔액 부족)
    private List<String> errorMsg;  // 에러/결과 메시지 목록
    private int count;              // 발송 성공 건수
    private int successCount;       // 발송 성공 건수
    private int duplicate;          // 중복 제거된 건수
    private int blockedCount;       // 수신거부 제외 건수
    private List<String> duplicateList; // 중복 번호 목록
    private String unsupportedChars;    // 지원하지 않는 문자
    private String batchId;         // 배치 ID (userKey)
    private BigDecimal chargedAmount;   // 차감된 금액

    public static MultiMessageResponse success(int count, int duplicate, int blockedCount, String batchId, BigDecimal chargedAmount) {
        StringBuilder msg = new StringBuilder();
        msg.append("발송 등록에 성공하였습니다.\r\n");
        msg.append("등록 성공: ").append(count).append(" 건\r\n");
        if (duplicate > 0) {
            msg.append("중복 연락처: ").append(duplicate).append(" 건\r\n");
        }
        if (blockedCount > 0) {
            msg.append("수신거부: ").append(blockedCount).append(" 건");
        }

        return MultiMessageResponse.builder()
                .resultCode(1)
                .errorMsg(List.of(msg.toString().trim()))
                .count(count)
                .successCount(count)
                .duplicate(duplicate)
                .blockedCount(blockedCount)
                .batchId(batchId)
                .chargedAmount(chargedAmount)
                .build();
    }

    // 기존 호환성을 위한 오버로드
    public static MultiMessageResponse success(int count, int duplicate, String batchId, BigDecimal chargedAmount) {
        return success(count, duplicate, 0, batchId, chargedAmount);
    }

    public static MultiMessageResponse insufficientBalance() {
        return MultiMessageResponse.builder()
                .resultCode(-3)
                .errorMsg(List.of("충전 금액이 문자를 발송하기에 모자랍니다."))
                .count(0)
                .successCount(0)
                .build();
    }

    public static MultiMessageResponse unsupportedChars(String chars) {
        return MultiMessageResponse.builder()
                .resultCode(-2)
                .errorMsg(List.of("지원하지 않는 문자가 포함되어 있습니다: [" + chars + "]"))
                .unsupportedChars(chars)
                .count(0)
                .successCount(0)
                .build();
    }

    public static MultiMessageResponse error(String message) {
        return MultiMessageResponse.builder()
                .resultCode(-1)
                .errorMsg(List.of(message))
                .count(0)
                .successCount(0)
                .build();
    }
}
