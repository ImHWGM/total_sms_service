package kr.wisead.domain.audit.service;

import kr.wisead.domain.audit.entity.AuditEvent;
import kr.wisead.domain.user.entity.LifecycleStatus;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.AuditEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 컴플라이언스 감사 이벤트 기록 서비스.
 *
 * <p>모든 메서드는 fire-and-forget 패턴. 기록 실패 시 경고 로그를 남기고 호출자에게 예외를 전파하지 않는다 (감사 실패가 비즈니스 흐름을 막지 않도록). 필수
 * 필드 누락 시에는 예외를 전파한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditEventService {

  /** 잠금 해제 유형 */
  public enum UnlockType {
    /** 잠금 기간 만료 후 자동 해제 */
    AUTO,
    /** 이메일 OTP 인증을 통한 즉시 해제 */
    OTP,
    /** 관리자 강제 해제 */
    ADMIN
  }

  private final AuditEventMapper auditEventMapper;

  /**
   * 신규 회원가입 감사 기록.
   *
   * @param user 가입 완료된 사용자
   * @param ip 요청 IP
   * @param userAgent 요청 User-Agent
   * @param signupMethod 가입 방법 (예: "WEB")
   */
  public void recordSignup(User user, String ip, String userAgent, String signupMethod) {
    try {
      AuditEvent event =
          AuditEvent.builder()
              .eventType("SIGNUP")
              .userId(user.getSeq())
              .actorId(user.getSeq())
              .actorType("USER")
              .ip(ip)
              .userAgent(userAgent)
              .newStatus(LifecycleStatus.PENDING_APPROVAL.name())
              .metadata(toJson("signup_method", signupMethod))
              .build();
      auditEventMapper.insert(event);
      log.info("[감사] SIGNUP: userSeq={}, ip={}", user.getSeq(), ip);
    } catch (Exception e) {
      log.warn("[감사] SIGNUP 기록 실패: userSeq={}, 사유={}", user.getSeq(), e.getMessage());
    }
  }

  /**
   * 회원 탈퇴 감사 기록.
   *
   * @param user 탈퇴 처리된 사용자
   * @param reason 탈퇴 사유
   * @param ip 요청 IP
   * @param userAgent 요청 User-Agent
   */
  public void recordWithdraw(User user, String reason, String ip, String userAgent) {
    try {
      AuditEvent event =
          AuditEvent.builder()
              .eventType("WITHDRAW")
              .userId(user.getSeq())
              .actorId(user.getSeq())
              .actorType("USER")
              .ip(ip)
              .userAgent(userAgent)
              .reason(reason)
              .prevStatus(LifecycleStatus.ACTIVE.name())
              .newStatus(LifecycleStatus.WITHDRAWN.name())
              .build();
      auditEventMapper.insert(event);
      log.info("[감사] WITHDRAW: userSeq={}, ip={}", user.getSeq(), ip);
    } catch (Exception e) {
      log.warn("[감사] WITHDRAW 기록 실패: userSeq={}, 사유={}", user.getSeq(), e.getMessage());
    }
  }

  /**
   * 계정 잠금 해제 감사 기록.
   *
   * @param user 잠금 해제된 사용자
   * @param type 해제 유형 (AUTO / OTP / ADMIN)
   * @param actorId 행위자 seq (AUTO/OTP 시 user.seq, ADMIN 시 admin.seq)
   */
  public void recordUnlock(User user, UnlockType type, Integer actorId) {
    try {
      String eventType =
          switch (type) {
            case AUTO -> "UNLOCK_AUTO";
            case OTP -> "UNLOCK_OTP";
            case ADMIN -> "UNLOCK_ADMIN";
          };
      String actorType = (type == UnlockType.ADMIN) ? "ADMIN" : "USER";

      AuditEvent event =
          AuditEvent.builder()
              .eventType(eventType)
              .userId(user.getSeq())
              .actorId(actorId)
              .actorType(actorType)
              .prevStatus("LOCKED")
              .newStatus(LifecycleStatus.ACTIVE.name())
              .build();
      auditEventMapper.insert(event);
      log.info("[감사] {}: userSeq={}, actorId={}", eventType, user.getSeq(), actorId);
    } catch (Exception e) {
      log.warn(
          "[감사] UNLOCK 기록 실패: userSeq={}, type={}, 사유={}", user.getSeq(), type, e.getMessage());
    }
  }

  /**
   * 휴면 전환 감사 기록 (배치 시스템 행위).
   *
   * @param user 휴면 전환된 사용자
   */
  public void recordDormant(User user) {
    try {
      AuditEvent event =
          AuditEvent.builder()
              .eventType("DORMANT")
              .userId(user.getSeq())
              .actorType("SYSTEM")
              .prevStatus(LifecycleStatus.ACTIVE.name())
              .newStatus(LifecycleStatus.DORMANT.name())
              .build();
      auditEventMapper.insert(event);
      log.info("[감사] DORMANT: userSeq={}", user.getSeq());
    } catch (Exception e) {
      log.warn("[감사] DORMANT 기록 실패: userSeq={}, 사유={}", user.getSeq(), e.getMessage());
    }
  }

  /**
   * 휴면 복구 감사 기록.
   *
   * @param user 복구된 사용자
   * @param ip 요청 IP
   * @param userAgent 요청 User-Agent
   */
  public void recordRecovery(User user, String ip, String userAgent) {
    try {
      AuditEvent event =
          AuditEvent.builder()
              .eventType("RECOVERY")
              .userId(user.getSeq())
              .actorId(user.getSeq())
              .actorType("USER")
              .ip(ip)
              .userAgent(userAgent)
              .prevStatus(LifecycleStatus.DORMANT.name())
              .newStatus(LifecycleStatus.ACTIVE.name())
              .build();
      auditEventMapper.insert(event);
      log.info("[감사] RECOVERY: userSeq={}, ip={}", user.getSeq(), ip);
    } catch (Exception e) {
      log.warn("[감사] RECOVERY 기록 실패: userSeq={}, 사유={}", user.getSeq(), e.getMessage());
    }
  }

  /**
   * 배치 탈퇴 전환 감사 기록 (휴면 6개월 후 시스템 자동 탈퇴).
   *
   * @param user 탈퇴 전환된 사용자
   */
  public void recordWithdrawnBatch(User user) {
    try {
      AuditEvent event =
          AuditEvent.builder()
              .eventType("WITHDRAWN_BATCH")
              .userId(user.getSeq())
              .actorType("SYSTEM")
              .prevStatus(LifecycleStatus.DORMANT.name())
              .newStatus(LifecycleStatus.WITHDRAWN.name())
              .build();
      auditEventMapper.insert(event);
      log.info("[감사] WITHDRAWN_BATCH: userSeq={}", user.getSeq());
    } catch (Exception e) {
      log.warn("[감사] WITHDRAWN_BATCH 기록 실패: userSeq={}, 사유={}", user.getSeq(), e.getMessage());
    }
  }

  /** 단일 키-값 쌍을 JSON 문자열로 변환 (외부 라이브러리 비의존). */
  private String toJson(String key, String value) {
    if (value == null) {
      return null;
    }
    return "{\"" + key + "\": \"" + value.replace("\"", "\\\"") + "\"}";
  }
}
