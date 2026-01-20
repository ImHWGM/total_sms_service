package kr.wisead.domain.message.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 행사참여자 문자 발송 응답 DTO
 */
@Getter
@Builder
public class EventMessageResponse {

    private boolean success;
    private String message;
    private int successCount;
    private int failCount;
    private int duplicateCount;
    private List<String> failedPhones;
    private List<Integer> mseqList;
    private String txGroupId;
    private LocalDateTime sendTime;

    /**
     * 성공 응답
     */
    public static EventMessageResponse success(int successCount, List<Integer> mseqList,
                                                String txGroupId, LocalDateTime sendTime) {
        return EventMessageResponse.builder()
                .success(true)
                .message("행사참여자 문자 발송이 등록되었습니다.")
                .successCount(successCount)
                .failCount(0)
                .duplicateCount(0)
                .mseqList(mseqList)
                .txGroupId(txGroupId)
                .sendTime(sendTime)
                .build();
    }

    /**
     * 부분 성공 응답
     */
    public static EventMessageResponse partial(int successCount, int failCount,
                                                int duplicateCount, List<String> failedPhones,
                                                List<Integer> mseqList, String txGroupId) {
        return EventMessageResponse.builder()
                .success(true)
                .message(String.format("행사참여자 문자 발송 완료 (성공: %d건, 실패: %d건, 중복: %d건)",
                        successCount, failCount, duplicateCount))
                .successCount(successCount)
                .failCount(failCount)
                .duplicateCount(duplicateCount)
                .failedPhones(failedPhones)
                .mseqList(mseqList)
                .txGroupId(txGroupId)
                .sendTime(LocalDateTime.now())
                .build();
    }

    /**
     * 실패 응답
     */
    public static EventMessageResponse fail(String message) {
        return EventMessageResponse.builder()
                .success(false)
                .message(message)
                .successCount(0)
                .failCount(0)
                .duplicateCount(0)
                .sendTime(LocalDateTime.now())
                .build();
    }

    /**
     * 잔액 부족 응답
     */
    public static EventMessageResponse insufficientBalance(String message) {
        return EventMessageResponse.builder()
                .success(false)
                .message(message != null ? message : "잔액이 부족합니다.")
                .successCount(0)
                .failCount(0)
                .duplicateCount(0)
                .sendTime(LocalDateTime.now())
                .build();
    }
}
