package kr.wisead.domain.survey.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/** 항목(보기) 생성/수정 요청 DTO */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemRequest {

  @NotBlank(message = "항목 내용은 필수입니다.")
  private String item; // 항목 내용

  private String itemValue; // 항목 값

  private String itemImg; // 항목 이미지 경로

  private Integer order; // 순서

  private Integer jumpQuestion; // 분기 문항 시퀀스

  private String otherYn; // 기타 항목 여부 (Y/N)

  private String otherPlaceholder; // 기타 항목 입력 안내 문구
}
