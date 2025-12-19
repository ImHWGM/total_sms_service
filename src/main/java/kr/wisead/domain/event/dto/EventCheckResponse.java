package kr.wisead.domain.event.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 행사 체크인/액션 처리 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventCheckResponse {

    private String action;              // CHECK_IN, PRIZE, GIFT 등
    private String actionName;          // 액션 이름 (입장, 경품 수령 등)
    private ParticipantInfo participant;
    private String nametagUrl;          // 명찰 URL (체크인 시)
    private String message;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipantInfo {
        private Long seq;
        private String name;
        private String department;
        private String position;
        private String participantType;
        private String phone;
        private String email;
    }

    /**
     * 체크인 응답 생성
     */
    public static EventCheckResponse checkIn(EventParticipantResponse participant, String nametagUrl) {
        return EventCheckResponse.builder()
                .action("CHECK_IN")
                .actionName("입장")
                .participant(ParticipantInfo.builder()
                        .seq(participant.getSeq())
                        .name(participant.getUserName())
                        .department(participant.getDepartment())
                        .position(participant.getPosition())
                        .participantType(participant.getParticipantType())
                        .phone(participant.getUserPhone())
                        .email(participant.getUserEmail())
                        .build())
                .nametagUrl(nametagUrl)
                .message("입장 처리되었습니다. 명찰을 출력해주세요.")
                .build();
    }

    /**
     * 이미 체크인한 경우 응답
     */
    public static EventCheckResponse alreadyCheckedIn(EventParticipantResponse participant) {
        return EventCheckResponse.builder()
                .action("ALREADY_CHECKED_IN")
                .actionName("입장 완료")
                .participant(ParticipantInfo.builder()
                        .seq(participant.getSeq())
                        .name(participant.getUserName())
                        .department(participant.getDepartment())
                        .position(participant.getPosition())
                        .participantType(participant.getParticipantType())
                        .build())
                .message("이미 입장 처리된 참가자입니다.")
                .build();
    }

    /**
     * 액션 처리 응답 생성
     */
    public static EventCheckResponse action(String actionCode, String actionName,
                                             EventParticipantResponse participant) {
        return EventCheckResponse.builder()
                .action(actionCode)
                .actionName(actionName)
                .participant(ParticipantInfo.builder()
                        .seq(participant.getSeq())
                        .name(participant.getUserName())
                        .department(participant.getDepartment())
                        .position(participant.getPosition())
                        .participantType(participant.getParticipantType())
                        .build())
                .message(actionName + " 처리가 완료되었습니다.")
                .build();
    }
}
