package kr.wisead.domain.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * 행사 체크인/액션 처리 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventCheckRequest {

    private Long participantSeq;

    @NotBlank(message = "액션 코드는 필수입니다.")
    private String actionCode;

    @Size(max = 100, message = "기기 정보는 100자 이내로 입력해주세요.")
    private String deviceInfo;

    // 관리자 인증이 필요한 경우
    private String adminPassword;

    @Size(max = 200, message = "메모는 200자 이내로 입력해주세요.")
    private String memo;
}
