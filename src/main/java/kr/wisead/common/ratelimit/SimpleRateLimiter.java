package kr.wisead.common.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 단일 인스턴스 전제의 메모리 기반 rate limiter. 동일 키에 대해 1초당 1회만 허용. 멀티 인스턴스 환경 마이그레이션 시 Redis 기반으로 교체 필요.
 *
 * <p>메모리 보호: 키 개수가 {@link #MAX_ENTRIES}를 초과하면 신규 acquire를 거부하며, 주기적 cleanup으로 유휴 키를 제거한다.
 */
@Slf4j
@Component
public class SimpleRateLimiter {

  private static final long MIN_INTERVAL_MS = 1_000L;
  private static final long EVICT_AFTER_MS = 5 * 60 * 1_000L;
  private static final int MAX_ENTRIES = 100_000;

  private final ConcurrentHashMap<String, Long> lastCalls = new ConcurrentHashMap<>();

  /** 마지막 호출로부터 1초가 지났으면 true, 아니면 false. compute를 사용하여 rollback race condition 없이 원자적으로 처리한다. */
  public boolean tryAcquire(String key) {
    // 메모리 폭주 방어: 엔트리 수가 상한을 넘으면 모든 acquire 거부.
    if (lastCalls.size() >= MAX_ENTRIES && !lastCalls.containsKey(key)) {
      log.warn("SimpleRateLimiter at capacity: size={}, rejecting new key", lastCalls.size());
      return false;
    }
    long now = System.currentTimeMillis();
    Long resolved =
        lastCalls.compute(
            key,
            (k, v) -> {
              if (v != null && now - v < MIN_INTERVAL_MS) {
                return v; // 기존 값 유지 → 거부
              }
              return now; // 새 값 기록 → 허용
            });
    return resolved != null && resolved == now;
  }

  /** 5분 이상 유휴 키 제거. */
  @Scheduled(fixedDelay = 5 * 60 * 1_000L)
  public void cleanup() {
    long threshold = System.currentTimeMillis() - EVICT_AFTER_MS;
    int before = lastCalls.size();
    lastCalls.entrySet().removeIf((Map.Entry<String, Long> e) -> e.getValue() < threshold);
    int removed = before - lastCalls.size();
    if (removed > 0) {
      log.debug("SimpleRateLimiter cleanup: removed={}, remaining={}", removed, lastCalls.size());
    }
  }
}
