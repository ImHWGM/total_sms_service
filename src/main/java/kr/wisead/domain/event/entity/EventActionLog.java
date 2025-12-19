package kr.wisead.domain.event.entity;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 행사 액션 로그 Entity (EVENT_ACTION_LOG)
 * 입장, 경품수령, 기념품수령 등 모든 액션 기록
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EventActionLog {

    private Long seq;                       // 로그 시퀀스
    private Long participantSeq;            // 참가자 시퀀스 (EVENT_PARTICIPANT)
    private Long actionTypeSeq;             // 액션 유형 시퀀스 (EVENT_ACTION_TYPE)
    private LocalDateTime actionTime;       // 액션 시간
    private String deviceInfo;              // 체크인 기기 정보
    private String confirmedBy;             // 관리자 인증 시 관리자 ID
    private String memo;                    // 메모

    // 조회용 필드 (JOIN)
    private String actionCode;              // 액션 코드
    private String actionName;              // 액션 이름
    private String userName;                // 참가자 이름
    private String department;              // 소속/부서
    private String position;                // 직책

    /**
     * 액션 로그 생성 (관리자 인증 불필요)
     */
    public static EventActionLog create(Long participantSeq, Long actionTypeSeq,
                                         String deviceInfo, String memo) {
        return EventActionLog.builder()
                .participantSeq(participantSeq)
                .actionTypeSeq(actionTypeSeq)
                .actionTime(LocalDateTime.now())
                .deviceInfo(deviceInfo)
                .memo(memo)
                .build();
    }

    /**
     * 액션 로그 생성 (관리자 인증 필요)
     */
    public static EventActionLog createWithAdminAuth(Long participantSeq, Long actionTypeSeq,
                                                      String deviceInfo, String confirmedBy, String memo) {
        return EventActionLog.builder()
                .participantSeq(participantSeq)
                .actionTypeSeq(actionTypeSeq)
                .actionTime(LocalDateTime.now())
                .deviceInfo(deviceInfo)
                .confirmedBy(confirmedBy)
                .memo(memo)
                .build();
    }
}
