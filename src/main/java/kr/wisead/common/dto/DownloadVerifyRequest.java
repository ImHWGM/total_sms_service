package kr.wisead.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 엑셀 다운로드 비밀번호 검증 공통 요청 DTO */
@Getter
@Setter
@NoArgsConstructor
public class DownloadVerifyRequest {

  @NotBlank(message = "비밀번호를 입력해주세요.")
  private String password;

  @NotBlank(message = "다운로드 사유를 입력해주세요.")
  private String reason;
}
