package kr.wisead.domain.event.entity;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 명찰 출력 이력 Entity (EVENT_NAMETAG_LOG)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EventNametagLog {

    private Long seq;                       // 로그 시퀀스
    private Long participantSeq;            // 참가자 시퀀스 (EVENT_PARTICIPANT)
    private LocalDateTime printTime;        // 출력 시간
    private String templateType;            // 명찰 템플릿 유형
    private String printBy;                 // 출력자 ID

    // 조회용 필드 (JOIN)
    private String userName;                // 참가자 이름
    private String department;              // 소속/부서
    private String position;                // 직책
    private String participantType;         // 참가자 유형

    /**
     * 명찰 출력 로그 생성
     */
    public static EventNametagLog create(Long participantSeq, String templateType, String printBy) {
        return EventNametagLog.builder()
                .participantSeq(participantSeq)
                .printTime(LocalDateTime.now())
                .templateType(templateType)
                .printBy(printBy)
                .build();
    }
}
