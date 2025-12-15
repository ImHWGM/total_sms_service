package kr.wisead.domain.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 예약 메시지 검색 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledMessageSearchRequest {

    private String msgType;         // 메시지 타입 (SMS, LMS, MMS)
    private String searchText;      // 검색어
    private String userId;          // 조회 대상 사용자 ID

    // 페이징
    @Builder.Default
    private int page = 1;
    @Builder.Default
    private int size = 10;

    public int getOffset() {
        return (page - 1) * size;
    }

    /**
     * 메시지 타입을 DB 값으로 변환
     */
    public String getConvertedMsgType() {
        if (msgType == null) return null;

        return switch (msgType.toUpperCase()) {
            case "SMS" -> "S";
            case "LMS" -> "L";
            case "MMS" -> "M";
            default -> msgType;
        };
    }
}
