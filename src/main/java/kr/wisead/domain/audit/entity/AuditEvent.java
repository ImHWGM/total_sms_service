package kr.wisead.domain.audit.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 계정 생명주기/잠금/휴면 등 컴플라이언스 감사 이벤트 Entity (AUDIT_EVENT).
 *
 * <p>12개월 이상 보존 대상. action_log 와 별도 테이블로 분리 (action_log.ACTION_TYPE 은 char(1) 의미 잠금).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

  /** PK */
  private Long seq;

  /**
   * 이벤트 유형. SIGNUP / WITHDRAW / UNLOCK_AUTO / UNLOCK_OTP / UNLOCK_ADMIN / DORMANT / RECOVERY /
   * WITHDRAWN_BATCH
   */
  private String eventType;

  /** 대상 사용자 seq (user.SEQ) */
  private Integer userId;

  /** 행위자 seq — 관리자 강제 해제 시 admin.seq, SYSTEM 행위 시 null */
  private Integer actorId;

  /** 행위자 유형: USER / ADMIN / SYSTEM */
  private String actorType;

  /** 요청 IP (IPv4/IPv6 최대 45자) */
  private String ip;

  /** 요청 User-Agent */
  private String userAgent;

  /** 사유 (탈퇴 사유, 잠금 해제 사유 등) */
  private String reason;

  /** 변경 전 상태 (LIFECYCLE_STATUS 값) */
  private String prevStatus;

  /** 변경 후 상태 (LIFECYCLE_STATUS 값) */
  private String newStatus;

  /** 자유 형식 메타데이터 JSON (signup_method, withdraw_method 등) */
  private String metadata;

  /** 생성 시각 */
  private LocalDateTime createdAt;
}
