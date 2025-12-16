package kr.wisead.domain.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 사용자별 QR 통계 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserQrStatsResponse {

    /**
     * 사용자 ID
     */
    private String userId;

    /**
     * 사용자명
     */
    private String userName;

    /**
     * 이벤트명
     */
    private String eventName;

    /**
     * 이벤트 수
     */
    private int eventCount;

    /**
     * QR 방문 수
     */
    private int visitCount;
}
