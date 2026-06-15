package kr.wisead.domain.profanity.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 금칙어 차단 로그 Entity (PROFANITY_BLOCK_LOG).
 *
 * <p>12개월 보존 대상. 월별 archive 배치에서 profanity_block_log_archive_YYYYMM 으로 이관 후 원본 삭제.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfanityBlockLog {

  /** PK */
  private Long seq;

  /** 발송 요청 사용자 user.SEQ */
  private Integer userId;

  /** 매칭된 금칙어 */
  private String matchedWord;

  /** 메시지 내용 앞 500자 */
  private String contentSnippet;

  /** 원본 전체 내용 (archive 후 삭제) */
  private String fullContent;

  /**
   * 발생 경로.
   *
   * <ul>
   *   <li>DRAFT – 임시저장
   *   <li>SEND – 즉시/예약 발송
   *   <li>SCHEDULE – 예약 dispatch
   * </ul>
   */
  private String source;

  /** 관련 메시지 ID */
  private Long messageId;

  /** 발송 대상 수 */
  private Integer recipientCount;

  /** 요청 IP (IPv4/IPv6 최대 45자) */
  private String ip;

  /** 요청 User-Agent */
  private String userAgent;

  /** 차단 일시 */
  private LocalDateTime createdAt;
}
