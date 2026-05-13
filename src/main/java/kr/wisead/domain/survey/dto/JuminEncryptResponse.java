package kr.wisead.domain.survey.dto;

import lombok.*;

/**
 * 가상 키패드 입력 주민번호 즉시 변환 응답 DTO.
 *
 * <p>{@code ciphertext}는 {@code "ENC:" + AES256+Base64} 형식. FE는 이 값을 설문 답변 {@code answer} 필드에 그대로
 * 넣어 제출.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JuminEncryptResponse {

  private String ciphertext;
}
