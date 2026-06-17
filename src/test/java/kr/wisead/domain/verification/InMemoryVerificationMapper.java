package kr.wisead.domain.verification;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import kr.wisead.domain.verification.entity.Verification;
import kr.wisead.mapper.primary.VerificationMapper;

/**
 * VerificationMapper 의 SQL 의미(원자적 increment/markVerified, 만료 정리 등)를 자바로 재현한 인메모리 구현(테스트 전용).
 * SMS·이메일 사전인증 서비스 테스트가 공유한다.
 */
public class InMemoryVerificationMapper implements VerificationMapper {

  private final Map<String, Verification> store = new HashMap<>();

  private String key(String purpose, String channel, String identifier) {
    return purpose + ":" + channel + ":" + identifier;
  }

  @Override
  public Verification findByKey(String purpose, String channel, String identifier) {
    return store.get(key(purpose, channel, identifier));
  }

  @Override
  public int insert(Verification e) {
    store.put(key(e.getPurpose(), e.getChannel(), e.getIdentifier()), e);
    return 1;
  }

  @Override
  public int updateForSend(Verification e) {
    String k = key(e.getPurpose(), e.getChannel(), e.getIdentifier());
    Verification cur = store.get(k);
    if (cur == null) {
      return 0;
    }
    // code/created_at 갱신, attempts=0, verified_at=NULL (seq 유지)
    store.put(
        k,
        new Verification(
            cur.getSeq(),
            e.getPurpose(),
            e.getChannel(),
            e.getIdentifier(),
            e.getCode(),
            0,
            e.getCreatedAt(),
            null));
    return 1;
  }

  @Override
  public int incrementAttempts(String purpose, String channel, String identifier) {
    String k = key(purpose, channel, identifier);
    Verification e = store.get(k);
    if (e == null) {
      return 0;
    }
    store.put(
        k,
        new Verification(
            e.getSeq(),
            e.getPurpose(),
            e.getChannel(),
            e.getIdentifier(),
            e.getCode(),
            e.getAttempts() + 1,
            e.getCreatedAt(),
            e.getVerifiedAt()));
    return 1;
  }

  @Override
  public int markVerifiedIfCodeMatches(
      String purpose, String channel, String identifier, String code, LocalDateTime verifiedAt) {
    String k = key(purpose, channel, identifier);
    Verification e = store.get(k);
    if (e != null && code.equals(e.getCode()) && e.getVerifiedAt() == null) {
      store.put(
          k,
          new Verification(
              e.getSeq(),
              e.getPurpose(),
              e.getChannel(),
              e.getIdentifier(),
              null,
              e.getAttempts(),
              e.getCreatedAt(),
              verifiedAt));
      return 1;
    }
    return 0;
  }

  @Override
  public int deleteByKey(String purpose, String channel, String identifier) {
    return store.remove(key(purpose, channel, identifier)) != null ? 1 : 0;
  }

  @Override
  public int deleteExpired(LocalDateTime codeCutoff, LocalDateTime verifiedCutoff) {
    int[] count = {0};
    store
        .values()
        .removeIf(
            e -> {
              boolean codeExpired =
                  e.getVerifiedAt() == null && e.getCreatedAt().isBefore(codeCutoff);
              boolean stampExpired =
                  e.getVerifiedAt() != null && e.getVerifiedAt().isBefore(verifiedCutoff);
              if (codeExpired || stampExpired) {
                count[0]++;
                return true;
              }
              return false;
            });
    return count[0];
  }
}
