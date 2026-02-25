package kr.wisead.domain.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/** 행사 참가자 요청 DTO */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventParticipantRequest {

  private Integer eventSeq; // Path variable에서 설정됨

  @NotBlank(message = "참가자 이름은 필수입니다.")
  @Size(max = 45, message = "이름은 45자 이내로 입력해주세요.")
  private String userName;

  @NotBlank(message = "연락처는 필수입니다.")
  @Pattern(regexp = "^010-?[2-9]\\d{3}-?\\d{4}$", message = "연락처 형식이 올바르지 않습니다.")
  private String userPhone;

  @Size(max = 150, message = "이메일은 150자 이내로 입력해주세요.")
  private String userEmail;

  @Size(max = 100, message = "소속은 100자 이내로 입력해주세요.")
  private String department;

  @Size(max = 100, message = "직책은 100자 이내로 입력해주세요.")
  private String position;

  @Size(max = 20, message = "참가자 유형은 20자 이내로 입력해주세요.")
  private String participantType;

  @Size(max = 500, message = "메모는 500자 이내로 입력해주세요.")
  private String memo;

  @Size(max = 20, message = "등록구분은 20자 이내로 입력해주세요.")
  private String registType; // 등록구분 (사전등록/현장등록/불참석, null=미등록)
}
