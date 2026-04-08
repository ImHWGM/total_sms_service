package kr.wisead.domain.event.security;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RSVP nonce 저장소. 5분 TTL 1회용. 단일 인스턴스 메모리 전제. 멀티 인스턴스 환경에서는 Redis 기반으로 마이그레이션 필요.
 *
 * <p>메모리 보호: 엔트리 수가 {@link #MAX_ENTRIES}를 초과하면 신규 발급을 거부한다. validateAndConsume은 원자적 remove(key,
 * expected)로 TOCTOU를 방지한다.
 */
@Slf4j
@Component
public class RsvpNonceStore {

  private static final long TTL_MS = 5 * 60 * 1_000L;
  private static final int MAX_ENTRIES = 100_000;

  private final ConcurrentHashMap<String, NonceEntry> store = new ConcurrentHashMap<>();

  /** nonce 발급 후 반환. 저장소가 가득 찬 경우 null 반환(호출자는 실패로 처리). */
  public String issue(int eventSeq, String checkCode) {
    String k = key(eventSeq, checkCode);
    if (store.size() >= MAX_ENTRIES && !store.containsKey(k)) {
      log.warn("RsvpNonceStore at capacity: size={}, rejecting new issue", store.size());
      return null;
    }
    String nonce = UUID.randomUUID().toString().replace("-", "");
    long expireAt = System.currentTimeMillis() + TTL_MS;
    store.put(k, new NonceEntry(nonce, expireAt));
    return nonce;
  }

  /** nonce 검증 후 1회용 소비. 원자적 remove(key, expected)로 TOCTOU 방지. */
  public boolean validateAndConsume(int eventSeq, String checkCode, String nonce) {
    if (nonce == null) return false;
    String k = key(eventSeq, checkCode);
    NonceEntry entry = store.get(k);
    if (entry == null) return false;
    if (System.currentTimeMillis() > entry.expireAtMs) {
      store.remove(k, entry);
      return false;
    }
    if (!entry.nonce.equals(nonce)) {
      return false;
    }
    // 원자적 제거: 다른 스레드가 이미 소비했다면 false로 이어진다.
    return store.remove(k, entry);
  }

  @Scheduled(fixedDelay = 5 * 60 * 1_000L)
  public void cleanup() {
    long now = System.currentTimeMillis();
    int before = store.size();
    store.entrySet().removeIf((Map.Entry<String, NonceEntry> e) -> e.getValue().expireAtMs < now);
    int removed = before - store.size();
    if (removed > 0) {
      log.debug("RsvpNonceStore cleanup: removed={}, remaining={}", removed, store.size());
    }
  }

  private static String key(int eventSeq, String checkCode) {
    return eventSeq + ":" + checkCode;
  }

  private static final class NonceEntry {
    final String nonce;
    final long expireAtMs;

    NonceEntry(String nonce, long expireAtMs) {
      this.nonce = nonce;
      this.expireAtMs = expireAtMs;
    }
  }
}
