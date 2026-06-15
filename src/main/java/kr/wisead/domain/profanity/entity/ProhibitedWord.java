package kr.wisead.domain.profanity.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 금칙어 마스터 Entity (PROHIBITED_WORD). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProhibitedWord {

  /** PK */
  private Long seq;

  /** 금칙어 */
  private String word;

  /** 분류 (성인/도박/금융 등) */
  private String category;

  /** 활성 여부 */
  private Boolean active;

  /** 등록 사유 */
  private String reason;

  /** 등록 일시 */
  private LocalDateTime createdAt;

  /** 수정 일시 */
  private LocalDateTime updatedAt;

  /**
   * 캐시 폴링용 버전 스탬프. ProhibitedWordService 의 CUD 마다 INCREMENT 된다. ProfanityFilterService 는 5초마다
   * MAX(VERSION) 을 폴링하여 캐시를 갱신한다.
   */
  private Long version;
}
