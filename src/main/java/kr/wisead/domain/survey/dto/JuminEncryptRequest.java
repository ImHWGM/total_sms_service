package kr.wisead.domain.survey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

/**
 * 가상 키패드 입력 주민번호 즉시 변환 요청 DTO.
 *
 * <p>F+H 흐름: 사용자가 jumin 키패드 "확인" 시점에 호출 → backend가 RSA 복호화 → AES256+Base64 재암호화 → ciphertext 반환.
 * FE는 ciphertext만 보관 후 설문 제출 시 answer 필드에 첨부. keypad TTL은 "발급~확인" 구간만 책임.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JuminEncryptRequest {

  @NotBlank(message = "키패드 세션 ID는 필수입니다.")
  private String keypadId;

  @NotBlank(message = "주민번호 앞자리는 필수입니다.")
  @Pattern(regexp = "\\d{6}", message = "주민번호 앞자리는 6자리 숫자여야 합니다.")
  private String front;

  @NotBlank(message = "주민번호 뒷자리 암호문은 필수입니다.")
  private String backCipher;
}
