package kr.wisead.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SimpleRateLimiterTest {

  @Test
  @DisplayName("동일 키는 1초에 1회만 허용한다")
  void tryAcquire_allowsSameKeyOncePerSecond() {
    SimpleRateLimiter limiter = new SimpleRateLimiter();

    assertThat(limiter.tryAcquire("key")).isTrue();
    assertThat(limiter.tryAcquire("key")).isFalse();

    entries(limiter).put("key", System.currentTimeMillis() - 1_001L);

    assertThat(limiter.tryAcquire("key")).isTrue();
  }

  @Test
  @DisplayName("capacity 도달 시 신규 키는 거부하고 기존 키는 기존 정책을 유지한다")
  void tryAcquire_rejectsNewKeyAtCapacity() {
    SimpleRateLimiter limiter = new SimpleRateLimiter();
    ConcurrentHashMap<String, Long> entries = entries(limiter);
    long oldTimestamp = System.currentTimeMillis() - 1_001L;
    entries.put("existing", oldTimestamp);
    for (int i = 0; i < AbstractInMemoryRateLimiter.MAX_ENTRIES - 1; i++) {
      entries.put("key-" + i, oldTimestamp);
    }

    assertThat(limiter.tryAcquire("new-key")).isFalse();
    assertThat(limiter.tryAcquire("existing")).isTrue();
  }

  @Test
  @DisplayName("cleanup은 5분 이상 유휴 키만 제거한다")
  void cleanup_removesOnlyIdleEntries() {
    SimpleRateLimiter limiter = new SimpleRateLimiter();
    ConcurrentHashMap<String, Long> entries = entries(limiter);
    entries.put("old", System.currentTimeMillis() - (5 * 60 * 1_000L) - 1_000L);
    entries.put("recent", System.currentTimeMillis());

    limiter.cleanup();

    assertThat(entries).doesNotContainKey("old");
    assertThat(entries).containsKey("recent");
  }

  @SuppressWarnings("unchecked")
  private ConcurrentHashMap<String, Long> entries(SimpleRateLimiter limiter) {
    return (ConcurrentHashMap<String, Long>) ReflectionTestUtils.getField(limiter, "entries");
  }
}
