package kr.wisead.domain.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
        // 무인증 공개 경로에서는 null로 마스킹 → 응답에서 완전히 제외 (다른 필드는 기존대로 null 렌더 유지)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String phone;
        @JsonInclude(JsonInclude.Include.NON_NULL)
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
