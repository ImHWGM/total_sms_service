package kr.wisead.scheduler;

import kr.wisead.domain.user.service.DormancyBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 휴면/탈퇴 배치 스케줄러 (PR3).
 *
 * <p>매일 03:00 KST 실행. 실행 순서:
 *
 * <ol>
 *   <li>notifyPendingDormant() — 5개월 미접속 사용자 사전 알림 (idempotent)
 *   <li>transitionToDormant() — 6개월 미접속 사용자 휴면 전환
 *   <li>transitionToWithdrawn() — 휴면 6개월 경과 사용자 탈퇴 전환 + PIPA 익명화
 * </ol>
 *
 * <p>{@code dormancy.batch.dryRun=true}(기본값) 시 DB 변경 없이 INFO 로그만 출력된다 (첫 배포 안전장치).
 *
 * <p>{@link org.springframework.scheduling.annotation.EnableScheduling}은 {@code WiseAdApplication}에
 * 선언되어 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DormancyScheduler {

  private final DormancyBatchService dormancyBatchService;

  /** 매일 03:00 실행 — 휴면/탈퇴 배치 전체 파이프라인. */
  @Scheduled(cron = "0 0 3 * * ?")
  public void runDailyBatch() {
    log.info("[휴면 배치 스케줄러] 시작");

    try {
      int notified = dormancyBatchService.notifyPendingDormant();
      int dormant = dormancyBatchService.transitionToDormant();
      int withdrawn = dormancyBatchService.transitionToWithdrawn();

      log.info("[휴면 배치 스케줄러] 완료 — 알림={}건, 휴면전환={}건, 탈퇴전환={}건", notified, dormant, withdrawn);
    } catch (Exception e) {
      log.error("[휴면 배치 스케줄러] 예외 발생: {}", e.getMessage(), e);
    }
  }
}
