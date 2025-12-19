package kr.wisead.domain.event.dto;

import lombok.*;

import java.util.List;

/**
 * 행사 통계 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventStatisticsResponse {

    private Integer eventSeq;
    private String eventName;

    // 전체 참가자 통계
    private ParticipantSummary participantSummary;

    // 액션별 통계
    private List<ActionStatistics> actionStatistics;

    // 명찰 출력 통계
    private NametagSummary nametagSummary;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipantSummary {
        private int totalCount;           // 전체 참가자 수
        private int checkedInCount;       // 체크인 완료 수
        private int notCheckedInCount;    // 미체크인 수
        private double checkedInRate;     // 체크인율 (%)

        // 참가자 유형별 통계
        private List<ParticipantTypeStat> byType;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipantTypeStat {
        private String participantType;
        private int count;
        private int checkedInCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActionStatistics {
        private Long actionTypeSeq;
        private String actionCode;
        private String actionName;
        private int totalCount;           // 전체 참가자 수
        private int completedCount;       // 완료 수
        private double completionRate;    // 완료율 (%)
        private String requireAdminAuth;  // 관리자 인증 필요 여부
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NametagSummary {
        private int totalCount;           // 전체 참가자 수
        private int printedCount;         // 명찰 출력 수
        private int notPrintedCount;      // 미출력 수
        private double printRate;         // 출력율 (%)
    }
}
