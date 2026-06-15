package kr.wisead.domain.profanity.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 금칙어 변경 이력 Entity (PROHIBITED_WORD_HISTORY). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProhibitedWordHistory {

  /** PK */
  private Long seq;

  /** 대상 금칙어 ID (prohibited_word.SEQ) */
  private Long wordId;

  /**
   * 변경 유형.
   *
   * <ul>
   *   <li>ADD – 신규 등록
   *   <li>MODIFY – 수정
   *   <li>DELETE – 삭제
   * </ul>
   */
  private String action;

  /** 변경 전 금칙어 상태 (JSON 직렬화 문자열) */
  private String prevValue;

  /** 변경 후 금칙어 상태 (JSON 직렬화 문자열) */
  private String newValue;

  /** 처리 관리자 user.SEQ */
  private Integer actorId;

  /** 변경 사유 (필수) */
  private String reason;

  /** 기록 일시 */
  private LocalDateTime createdAt;
}
