package kr.wisead.common.ratelimit;

/** Rate limit 초과 시 발생. 글로벌 핸들러에서 429로 매핑된다. */
public class RateLimitExceededException extends RuntimeException {
  public RateLimitExceededException(String message) {
    super(message);
  }
}
