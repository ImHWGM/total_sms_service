package kr.wisead.domain.user.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import kr.wisead.domain.audit.service.AuditEventService;
import kr.wisead.domain.email.service.EmailService;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 휴면/탈퇴 배치 처리 서비스 (PR3).
 *
 * <ul>
 *   <li>notifyPendingDormant(): 5개월 미접속 활성 사용자에게 휴면 예정 안내 이메일 발송 (idempotent).
 *   <li>transitionToDormant(): 6개월 미접속 활성 사용자를 휴면으로 전환.
 *   <li>transitionToWithdrawn(): 휴면 6개월 경과 사용자를 탈퇴로 전환 + 개인정보 익명화 (PIPA Art.21).
 * </ul>
 *
 * <p>AC25: dryRun=true(기본값) 시 DB 변경 없이 INFO 로그만 출력.
 *
 * <p>AC37: 모든 SQL은 UTC_TIMESTAMP() 사용 (DB session tz 비의존 — UserMapper.xml 참고).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DormancyBatchService {

  private final UserMapper userMapper;
  private final EmailService emailService;
  private final AuditEventService auditEventService;

  /** dryRun=true 기본값 — 첫 배포 안전장치 (AC25). */
  @Value("${dormancy.batch.dryRun:true}")
  private boolean dryRun;

  /** 일일 처리 상한 (Scenario 2: 메일 폭주 방지). */
  @Value("${dormancy.batch.dailyLimit:1000}")
  private int dailyLimit;

  /** 복관 URL (이메일 본문에 삽입). */
  @Value("${dormancy.batch.recoverUrl:https://wisead.kr/dormant-recovery}")
  private String recoverUrl;

  // ==================== 공개 배치 메서드 ====================

  /**
   * 휴면 사전 알림 발송.
   *
   * <p>predicate: LAST_LOGIN &lt; UTC_TIMESTAMP() - 5개월 AND LIFECYCLE_STATUS='ACTIVE' AND
   * DORMANT_NOTIFIED_AT IS NULL — idempotent (AC34 계열).
   *
   * @return 처리(알림 발송 또는 dryRun 로그) 건수
   */
  @Transactional
  public int notifyPendingDormant() {
    List<User> candidates = userMapper.selectDormancyNotifyCandidates(dailyLimit);
    log.info("[휴면 배치] 사전 알림 대상: {}건 (dryRun={})", candidates.size(), dryRun);

    int processed = 0;
    for (User user : candidates) {
      if (dryRun) {
        log.info("[DRY-RUN] 휴면 예정 알림 대상: seq={}, userId={}", user.getSeq(), user.getUserId());
      } else {
        sendDormantWarningEmail(user);
        userMapper.markDormantNotified(user.getSeq());
        log.info("[휴면 배치] 알림 발송 완료: seq={}, userId={}", user.getSeq(), user.getUserId());
      }
      processed++;
    }

    log.info("[휴면 배치] 사전 알림 처리 완료: {}건", processed);
    return processed;
  }

  /**
   * 휴면 전환 처리.
   *
   * <p>predicate: LAST_LOGIN &lt; UTC_TIMESTAMP() - 6개월 AND LIFECYCLE_STATUS='ACTIVE'.
   *
   * @return 처리(전환 또는 dryRun 로그) 건수
   */
  @Transactional
  public int transitionToDormant() {
    List<User> candidates = userMapper.selectDormancyTransitionCandidates(dailyLimit);
    log.info("[휴면 배치] 휴면 전환 대상: {}건 (dryRun={})", candidates.size(), dryRun);

    int processed = 0;
    for (User user : candidates) {
      if (dryRun) {
        log.info("[DRY-RUN] 휴면 전환 대상: seq={}, userId={}", user.getSeq(), user.getUserId());
      } else {
        userMapper.transitionToDormant(user.getSeq());
        auditEventService.recordDormant(user);
        log.info("[휴면 배치] 휴면 전환 완료: seq={}, userId={}", user.getSeq(), user.getUserId());
      }
      processed++;
    }

    log.info("[휴면 배치] 휴면 전환 처리 완료: {}건", processed);
    return processed;
  }

  /**
   * 배치 탈퇴 전환 + 익명화 처리.
   *
   * <p>predicate: DORMANT_AT &lt; UTC_TIMESTAMP() - 6개월 AND LIFECYCLE_STATUS='DORMANT'. AC34:
   * isAnonymized() 체크로 중복 익명화 방지.
   *
   * @return 처리(전환+익명화 또는 dryRun 로그) 건수
   */
  @Transactional
  public int transitionToWithdrawn() {
    List<User> candidates = userMapper.selectWithdrawalCandidates(dailyLimit);
    log.info("[휴면 배치] 탈퇴 전환 대상: {}건 (dryRun={})", candidates.size(), dryRun);

    int processed = 0;
    for (User user : candidates) {
      if (dryRun) {
        log.info("[DRY-RUN] 탈퇴 전환 대상: seq={}, userId={}", user.getSeq(), user.getUserId());
      } else {
        // AC34: 이미 익명화된 사용자는 DB 업데이트 스킵 (ANONYMIZED_AT is non-null idempotent guard)
        if (user.isAnonymized()) {
          log.warn("[휴면 배치] 이미 익명화된 사용자 — 스킵: seq={}", user.getSeq());
        } else {
          userMapper.transitionToWithdrawnAndAnonymize(user.getSeq());
        }
        auditEventService.recordWithdrawnBatch(user);
        log.info("[휴면 배치] 탈퇴 전환 완료: seq={}, userId={}", user.getSeq(), user.getUserId());
      }
      processed++;
    }

    log.info("[휴면 배치] 탈퇴 전환 처리 완료: {}건", processed);
    return processed;
  }

  // ==================== Private Helpers ====================

  private void sendDormantWarningEmail(User user) {
    String email = user.getEmail();
    if (email == null || email.isBlank()) {
      log.warn("[휴면 배치] 이메일 없음 — 알림 스킵: seq={}", user.getSeq());
      return;
    }

    String userName = Objects.requireNonNullElse(user.getPerson(), user.getUserId());
    String lastLoginDate =
        user.getLastLogin() != null ? user.getLastLogin().toLocalDate().toString() : "알 수 없음";
    // 알림 발송 시점 + 30일 = 휴면 예정일 (5개월 경과 시 발송 → 6개월까지 약 30일 남음)
    String dormantDate = LocalDate.now().plusDays(30).toString();

    try {
      emailService.sendDormantWarningEmail(email, userName, lastLoginDate, dormantDate, recoverUrl);
    } catch (Exception e) {
      log.warn("[휴면 배치] 알림 이메일 발송 실패 — 스킵하고 계속: seq={}, 사유={}", user.getSeq(), e.getMessage());
    }
  }
}
