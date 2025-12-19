package kr.wisead.domain.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.wisead.domain.event.entity.EventActionType;
import lombok.*;

/**
 * 행사 액션 유형 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventActionTypeRequest {

    private Integer eventSeq;  // Path variable에서 설정됨

    @NotBlank(message = "액션 코드는 필수입니다.")
    @Size(max = 30, message = "액션 코드는 30자 이내로 입력해주세요.")
    private String actionCode;

    @NotBlank(message = "액션 이름은 필수입니다.")
    @Size(max = 100, message = "액션 이름은 100자 이내로 입력해주세요.")
    private String actionName;

    @Builder.Default
    private String requireAdminAuth = "N";

    @Builder.Default
    private String allowMultiple = "N";

    @Builder.Default
    private Integer sortOrder = 0;

    @Builder.Default
    private String useYn = "Y";

    /**
     * Entity 변환
     */
    public EventActionType toEntity() {
        return EventActionType.builder()
                .eventSeq(eventSeq)
                .actionCode(actionCode)
                .actionName(actionName)
                .requireAdminAuth(requireAdminAuth)
                .allowMultiple(allowMultiple)
                .sortOrder(sortOrder)
                .useYn(useYn)
                .build();
    }
}
