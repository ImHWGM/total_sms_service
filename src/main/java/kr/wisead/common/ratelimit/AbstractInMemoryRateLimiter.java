package kr.wisead.common.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public abstract class AbstractInMemoryRateLimiter<V> {

  protected static final int MAX_ENTRIES = 100_000;

  private static final long EVICT_AFTER_MS = 5 * 60 * 1_000L;
  private static final long CLEANUP_FIXED_DELAY_MS = 5 * 60 * 1_000L;

  protected final ConcurrentHashMap<String, V> entries = new ConcurrentHashMap<>();

  protected boolean atCapacity(String key) {
    if (entries.size() >= MAX_ENTRIES && !entries.containsKey(key)) {
      log.warn("{} at capacity: size={}, rejecting new key", getClass().getSimpleName(), entries.size());
      return true;
    }
    return false;
  }

  protected abstract long timestampOf(V value);

  @Scheduled(fixedDelay = CLEANUP_FIXED_DELAY_MS)
  public void cleanup() {
    long threshold = System.currentTimeMillis() - EVICT_AFTER_MS;
    int before = entries.size();
    entries.entrySet().removeIf((Map.Entry<String, V> e) -> timestampOf(e.getValue()) < threshold);
    int removed = before - entries.size();
    if (removed > 0) {
      log.debug("{} cleanup: removed={}, remaining={}", getClass().getSimpleName(), removed, entries.size());
    }
  }
}
