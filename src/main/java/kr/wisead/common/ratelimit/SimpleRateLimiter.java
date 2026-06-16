package kr.wisead.common.ratelimit;

import org.springframework.stereotype.Component;

/**
 * 단일 인스턴스 전제의 메모리 기반 rate limiter. 동일 키에 대해 1초당 1회만 허용. 멀티 인스턴스 환경 마이그레이션 시 Redis 기반으로 교체 필요.
 *
 * <p>메모리 보호: 키 개수가 {@link #MAX_ENTRIES}를 초과하면 신규 acquire를 거부하며, 주기적 cleanup으로 유휴 키를 제거한다.
 */
@Component
public class SimpleRateLimiter extends AbstractInMemoryRateLimiter<Long> {

  private static final long MIN_INTERVAL_MS = 1_000L;

  /** 마지막 호출로부터 1초가 지났으면 true, 아니면 false. compute를 사용하여 rollback race condition 없이 원자적으로 처리한다. */
  public boolean tryAcquire(String key) {
    // 메모리 폭주 방어: 엔트리 수가 상한을 넘으면 모든 acquire 거부.
    if (atCapacity(key)) {
      return false;
    }
    long now = System.currentTimeMillis();
    // 허용 여부는 compute 람다가 '허용' 분기를 탔는지로 판정한다.
    // 타임스탬프 비교(resolved == now)는 동일 밀리초 재호출 시 옛 값과 now가 같아 오허용되는 결함이 있다.
    boolean[] allowed = {false};
    entries.compute(
        key,
        (k, v) -> {
          if (v != null && now - v < MIN_INTERVAL_MS) {
            return v; // 기존 값 유지 → 거부
          }
          allowed[0] = true;
          return now; // 새 값 기록 → 허용
        });
    return allowed[0];
  }

  @Override
  protected long timestampOf(Long value) {
    return value;
  }
}
