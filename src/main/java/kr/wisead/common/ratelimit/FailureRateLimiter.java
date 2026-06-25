package kr.wisead.common.ratelimit;

import org.springframework.stereotype.Component;

/** 단일 인스턴스 전제의 실패 기반 카운터. 정상이 아닌 조회 실패만 누적해 열거 공격을 제한한다. */
@Component
public class FailureRateLimiter extends AbstractInMemoryRateLimiter<FailureRateLimiter.FailureBucket> {

  private static final int DEFAULT_MAX_FAILURES = 10;
  private static final long DEFAULT_WINDOW_MS = 60_000L;

  public boolean recordFailureAndCheckAllowed(String key) {
    return recordFailureAndCheckAllowed(key, DEFAULT_MAX_FAILURES, DEFAULT_WINDOW_MS);
  }

  public boolean recordFailureAndCheckAllowed(String key, int maxFailures, long windowMs) {
    if (atCapacity(key)) {
      return false;
    }

    long now = System.currentTimeMillis();
    FailureBucket bucket =
        entries.compute(
            key,
            (k, current) -> {
              if (current == null || now - current.windowStartMs >= windowMs) {
                return new FailureBucket(now, 1);
              }
              current.count++;
              return current;
            });

    return bucket != null && bucket.count <= maxFailures;
  }

  @Override
  protected long timestampOf(FailureBucket value) {
    return value.windowStartMs;
  }

  static class FailureBucket {
    private final long windowStartMs;
    private int count;

    private FailureBucket(long windowStartMs, int count) {
      this.windowStartMs = windowStartMs;
      this.count = count;
    }
  }
}
