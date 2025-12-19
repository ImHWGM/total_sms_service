package kr.wisead.domain.event.entity;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 행사별 액션 유형 Entity (EVENT_ACTION_TYPE)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EventActionType {

    private Long seq;                       // 액션 유형 시퀀스
    private Integer eventSeq;               // 이벤트 시퀀스 (SURVEY_MASTER)
    private String actionCode;              // 액션 코드 (CHECK_IN, PRIZE, GIFT, MEAL 등)
    private String actionName;              // 액션 이름 (입장, 경품 수령 등)
    private String requireAdminAuth;        // 관리자 인증 필요 여부 (Y/N)
    private String allowMultiple;           // 중복 허용 여부 (Y/N)
    private Integer sortOrder;              // 정렬 순서
    private String useYn;                   // 사용 여부
    private LocalDateTime regDate;          // 등록일

    /**
     * 기본 액션 유형 생성 (입장)
     */
    public static EventActionType createCheckIn(Integer eventSeq) {
        return EventActionType.builder()
                .eventSeq(eventSeq)
                .actionCode("CHECK_IN")
                .actionName("입장")
                .requireAdminAuth("N")
                .allowMultiple("N")
                .sortOrder(1)
                .useYn("Y")
                .build();
    }

    /**
     * 관리자 인증 필요 여부 확인
     */
    public boolean isAdminAuthRequired() {
        return "Y".equals(this.requireAdminAuth);
    }

    /**
     * 중복 허용 여부 확인
     */
    public boolean isMultipleAllowed() {
        return "Y".equals(this.allowMultiple);
    }

    /**
     * 사용 여부 확인
     */
    public boolean isActive() {
        return "Y".equals(this.useYn);
    }
}
