package kr.wisead.domain.survey.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.*;

/** 문항 생성/수정 요청 DTO */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionRequest {

  @NotBlank(message = "문항 종류는 필수입니다.")
  private String questionType; // 문항 종류 (MC:객관식, SA:주관식)

  private String questionTypeDetail; // 문항 종류 상세 (MCS:단일선택, MCM:복수선택, SA:단답, FE:파일)

  @NotBlank(message = "문항 내용은 필수입니다.")
  private String question; // 문항 내용

  private String questionImg; // 문항 이미지 경로

  private Integer order; // 순서

  private String foreignAllow; // 외국인 허용 여부 (Y/N)

  private String requiredYn; // 필수 응답 여부 (Y:필수, N:선택, 미전달 시 N)

  // 객관식 문항의 보기 목록
  private List<ItemRequest> items;
}
