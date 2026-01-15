package kr.wisead.domain.message.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SMS 발송 결과 응답 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SmsSendResponse {

    private int totalCount;           // 총 발송 요청 건수
    private int successCount;         // 성공 건수
    private int failCount;            // 실패 건수
    private List<Integer> mseqList;   // 발송 큐 시퀀스 목록
    private LocalDateTime requestTime; // 발송 요청 시간
    private String sendType;          // 발송 타입 (즉시/예약)
    private String txGroupId;         // 결제 거래 그룹 ID (환불용)

    @Builder
    public SmsSendResponse(int totalCount, int successCount, int failCount,
                           List<Integer> mseqList, LocalDateTime requestTime, String sendType,
                           String txGroupId) {
        this.totalCount = totalCount;
        this.successCount = successCount;
        this.failCount = failCount;
        this.mseqList = mseqList;
        this.requestTime = requestTime;
        this.sendType = sendType;
        this.txGroupId = txGroupId;
    }

    /**
     * 성공 응답 생성
     */
    public static SmsSendResponse success(List<Integer> mseqList, LocalDateTime requestTime, boolean immediate) {
        return success(mseqList, requestTime, immediate, null);
    }

    /**
     * 성공 응답 생성 (txGroupId 포함)
     */
    public static SmsSendResponse success(List<Integer> mseqList, LocalDateTime requestTime, boolean immediate, String txGroupId) {
        return SmsSendResponse.builder()
                .totalCount(mseqList.size())
                .successCount(mseqList.size())
                .failCount(0)
                .mseqList(mseqList)
                .requestTime(requestTime)
                .sendType(immediate ? "즉시발송" : "예약발송")
                .txGroupId(txGroupId)
                .build();
    }
}
