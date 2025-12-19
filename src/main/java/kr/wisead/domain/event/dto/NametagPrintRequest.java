package kr.wisead.domain.event.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 명찰 출력 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NametagPrintRequest {

    @NotNull(message = "참가자 시퀀스는 필수입니다.")
    private Long participantSeq;

    @Builder.Default
    @Size(max = 50, message = "템플릿 유형은 50자 이내로 입력해주세요.")
    private String templateType = "DEFAULT";
}
