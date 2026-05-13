package kr.wisead.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 마이페이지 기본 2FA 채널 변경 요청 DTO (EMAIL ↔ SMS).
 *
 * <p>plan v5 §4 Phase E-4.
 */
@Getter
@Setter
@NoArgsConstructor
public class DefaultChannelRequest {

  /** 기본 채널: "EMAIL" 또는 "SMS". */
  @NotBlank(message = "채널은 필수입니다.")
  @Pattern(regexp = "^(EMAIL|SMS)$", message = "지원하지 않는 채널입니다.")
  private String channel;
}
