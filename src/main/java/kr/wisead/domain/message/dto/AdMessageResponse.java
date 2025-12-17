package kr.wisead.domain.message.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 광고 문자 발송 응답 DTO
 */
@Getter
@Builder
public class AdMessageResponse {

    /**
     * 결과 코드
     * 1: 성공
     * -1: 직접 등록 실패
     * -2: 시스템 오류
     * -3: 잔액 부족
     * -99: 수신거부로 전체 제외
     */
    private int resultCode;

    /**
     * 결과 메시지
     */
    private String message;

    /**
     * 발송 성공 건수
     */
    private int successCount;

    /**
     * 발송 실패 건수
     */
    private int failCount;

    /**
     * 중복 제외 건수
     */
    private int duplicateCount;

    /**
     * 수신거부 제외 건수
     */
    private int blockedCount;

    /**
     * 수신거부된 번호 목록 (마스킹)
     */
    private List<String> blockedNumbers;

    /**
     * 모든 번호가 차단된 경우 플래그
     */
    private boolean allBlocked;

    /**
     * 배치 ID (userKey)
     */
    private String batchId;

    /**
     * 발송 등록 시간
     */
    private LocalDateTime registeredAt;

    /**
     * 성공 응답 생성
     */
    public static AdMessageResponse success(int successCount, int duplicateCount, int blockedCount,
                                            List<String> blockedNumbers, String batchId) {
        String message = String.format("광고문자 발송이 완료되었습니다.\r\n등록 성공: %d건", successCount);
        if (duplicateCount > 0) {
            message += String.format("\r\n중복 제외: %d건", duplicateCount);
        }
        if (blockedCount > 0) {
            message += String.format("\r\n수신거부 제외: %d건", blockedCount);
        }

        return AdMessageResponse.builder()
                .resultCode(1)
                .message(message)
                .successCount(successCount)
                .duplicateCount(duplicateCount)
                .blockedCount(blockedCount)
                .blockedNumbers(blockedNumbers)
                .allBlocked(false)
                .batchId(batchId)
                .registeredAt(LocalDateTime.now())
                .build();
    }

    /**
     * 모든 번호가 수신거부로 차단된 경우
     */
    public static AdMessageResponse allBlocked(int blockedCount, List<String> blockedNumbers) {
        return AdMessageResponse.builder()
                .resultCode(-99)
                .message("광고문자 발송이 완료되었습니다.\r\n수신거부 등록으로 인해 모든 번호가 제외되었습니다.\r\n수신거부 제외: " + blockedCount + "건")
                .successCount(0)
                .blockedCount(blockedCount)
                .blockedNumbers(blockedNumbers)
                .allBlocked(true)
                .registeredAt(LocalDateTime.now())
                .build();
    }

    /**
     * 실패 응답 생성
     */
    public static AdMessageResponse fail(int resultCode, String message) {
        return AdMessageResponse.builder()
                .resultCode(resultCode)
                .message(message)
                .successCount(0)
                .registeredAt(LocalDateTime.now())
                .build();
    }

    /**
     * 잔액 부족 응답
     */
    public static AdMessageResponse insufficientBalance(String message) {
        return AdMessageResponse.builder()
                .resultCode(-3)
                .message(message)
                .successCount(0)
                .registeredAt(LocalDateTime.now())
                .build();
    }

    /**
     * 야간 전송 제한 응답
     */
    public static AdMessageResponse nightTimeRestricted() {
        return AdMessageResponse.builder()
                .resultCode(-4)
                .message("현재 야간 전송제한 시간입니다.\r\n금일 20:00 ~ 익일 09:00까지는 광고문자 전송이 제한됩니다.")
                .successCount(0)
                .registeredAt(LocalDateTime.now())
                .build();
    }
}
