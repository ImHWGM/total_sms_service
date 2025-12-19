package kr.wisead.domain.event.dto;

import kr.wisead.domain.event.entity.EventActionType;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 행사 액션 유형 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventActionTypeResponse {

    private Long seq;
    private Integer eventSeq;
    private String actionCode;
    private String actionName;
    private String requireAdminAuth;
    private String allowMultiple;
    private Integer sortOrder;
    private String useYn;
    private LocalDateTime regDate;

    /**
     * Entity -> Response 변환
     */
    public static EventActionTypeResponse from(EventActionType entity) {
        return EventActionTypeResponse.builder()
                .seq(entity.getSeq())
                .eventSeq(entity.getEventSeq())
                .actionCode(entity.getActionCode())
                .actionName(entity.getActionName())
                .requireAdminAuth(entity.getRequireAdminAuth())
                .allowMultiple(entity.getAllowMultiple())
                .sortOrder(entity.getSortOrder())
                .useYn(entity.getUseYn())
                .regDate(entity.getRegDate())
                .build();
    }
}
