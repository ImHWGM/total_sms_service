package kr.wisead.domain.event.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 참가자 상태 조회 응답 DTO (액션 현황 포함)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipantStatusResponse {

    private ParticipantInfo participant;
    private List<ActionStatus> actions;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipantInfo {
        private Long seq;
        private String checkCode;
        private String name;
        private String department;
        private String position;
        private String participantType;
        private String phone;
        private String email;
        private String nametagPrinted;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActionStatus {
        private Long actionTypeSeq;
        private String actionCode;
        private String actionName;
        private boolean completed;
        private LocalDateTime completedAt;
        private String confirmedBy;
        private boolean requireAdminAuth;
        private boolean allowMultiple;
    }
}
