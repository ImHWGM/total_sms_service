package kr.wisead.domain.user.dto;

import java.time.Instant;

/**
 * 로그인 실패/잠금 응답 DTO.
 *
 * <p>비밀번호 불일치 또는 계정 잠금 시 로그인 API 응답에 포함된다. FE는 이 객체를 통해 "남은 시도 N회" 경고 또는 잠금 팝업(카운트다운 포함)을 표시한다.
 *
 * <ul>
 *   <li>3~4회 실패: remainingAttempts > 0, locked = false → 경고 텍스트 표시
 *   <li>5회 실패 (잠김): locked = true, lockedUntil/retryAfterSeconds 포함 → 차단 팝업
 * </ul>
 */
public record LoginFailureResponse(
    /** 남은 시도 가능 횟수 (잠금 후에는 0) */
    int remainingAttempts,

    /** 계정 잠금 여부 */
    boolean locked,

    /** 잠금 해제 예정 시각 (UTC Instant; 잠금 상태가 아닌 경우 null) */
    Instant lockedUntil,

    /** 잠금 해제까지 남은 초 (잠금 상태가 아닌 경우 0) */
    int retryAfterSeconds,

    /** 잠금 해제 안내 힌트 메시지 (잠금 상태가 아닌 경우 null) */
    String unlockHint) {

  /** 최대 허용 시도 횟수 */
  private static final int MAX_ATTEMPTS = 5;

  /** 잠금 지속 시간 (초) */
  private static final int LOCK_DURATION_SECONDS = 600;

  /**
   * 비밀번호 불일치 응답 — 아직 잠금 상태 아님.
   *
   * @param currentFailureCount 현재(이번 실패 포함 후) DB에 저장된 실패 횟수
   */
  public static LoginFailureResponse forFailure(int currentFailureCount) {
    int remaining = Math.max(0, MAX_ATTEMPTS - currentFailureCount);
    return new LoginFailureResponse(remaining, false, null, 0, null);
  }

  /**
   * 계정 잠금 응답.
   *
   * @param until 잠금 해제 예정 시각 (UTC)
   * @param retryAfterSecs 잠금 해제까지 남은 초
   */
  public static LoginFailureResponse forLocked(Instant until, int retryAfterSecs) {
    return new LoginFailureResponse(
        0,
        true,
        until,
        retryAfterSecs,
        "이메일 인증을 통해 즉시 잠금을 해제하거나 " + retryAfterSecs / 60 + "분 후에 다시 시도할 수 있습니다.");
  }
}
