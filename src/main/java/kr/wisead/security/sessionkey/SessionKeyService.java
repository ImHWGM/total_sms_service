package kr.wisead.security.sessionkey;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CryptoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 1차 인증(ID/PW) 통과 후 OTP 검증 직전 단계를 보호하는 HMAC-SHA256 기반 임시 세션 토큰 저장소.
 *
 * <p>plan v5 §4 Phase D. 7분 만료 + 채널 전환 횟수 ≤ 3 + 단발성 폐기(재사용 방지).
 *
 * <ul>
 *   <li>{@code auth.session-key.secret} 은 {@code jwt.secret} 과 반드시 별도 값으로 운영 (#v3-4).
 *   <li>⚠ N13: {@code @Value} default 폴백 금지 — 미설정 시 부팅 실패 (fail-fast).
 * </ul>
 */
@Slf4j
@Service
public class SessionKeyService {

  /** ⚠ N13: default 폴백 절대 금지. 미설정 시 ApplicationContext 부팅 실패 (fail-fast). */
  @Value("${auth.session-key.secret}")
  private String secret;

  /** sessionKey 만료 시간: 7분 (1차 인증 통과 후 OTP 입력 제한 시간). */
  private static final long EXPIRY_MS = 7 * 60 * 1000L;

  /** 동일 sessionKey 로 채널 변경 가능 횟수 상한 (악용 방지). */
  private static final int MAX_SWITCH_COUNT = 3;

  /** sessionKey → SessionState 매핑 (단일 인스턴스 메모리 전제). */
  private final ConcurrentHashMap<String, SessionState> sessions = new ConcurrentHashMap<>();

  /**
   * 로그인 1차 인증 통과 후 sessionKey 발급. 입력은 {@code "{userId}|{timestamp}|{nonce}"} 의 HMAC-SHA256
   * (Base64URL no padding).
   */
  public String issue(Integer userId) {
    long timestamp = System.currentTimeMillis();
    String nonce = UUID.randomUUID().toString();
    String input = userId + "|" + timestamp + "|" + nonce;
    String key = computeHmac(input);
    sessions.put(key, new SessionState(userId, timestamp, 0, null));
    return key;
  }

  /** sessionKey 유효성 + 만료 검증. 유효하면 userId 반환, 아니면 {@link BusinessException}. */
  public Integer validate(String sessionKey) {
    if (sessionKey == null || sessionKey.isBlank()) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "sessionKey 가 필요합니다.");
    }
    SessionState state = sessions.get(sessionKey);
    if (state == null) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "유효하지 않은 sessionKey 입니다.");
    }
    if (System.currentTimeMillis() - state.issuedAt() > EXPIRY_MS) {
      sessions.remove(sessionKey);
      throw new BusinessException(ErrorCode.EXPIRED_TOKEN, "sessionKey 가 만료되었습니다. 다시 로그인해주세요.");
    }
    return state.userId();
  }

  /**
   * 채널 변경 시 호출. switchCount 증가 (원자적). 상한 초과 시 sessionKey 폐기 후 예외.
   *
   * <p>{@link ConcurrentHashMap#compute}로 get+put 비원자성 제거 (동시 채널 전환 race).
   */
  public void recordChannelSwitch(String sessionKey) {
    sessions.compute(
        sessionKey,
        (k, state) -> {
          if (state == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "유효하지 않은 sessionKey 입니다.");
          }
          if (state.switchCount() >= MAX_SWITCH_COUNT) {
            throw new BusinessException(
                ErrorCode.INVALID_INPUT_VALUE, "채널 변경 횟수를 초과했습니다. 다시 로그인해주세요.");
          }
          return state.withSwitchIncrement();
        });
  }

  /** 현재 sessionKey 의 channelSwitched 플래그 조회. 채널 전환 직후 첫 OTP 검증 시점 판정용. */
  public boolean isChannelRecentlySwitched(String sessionKey) {
    SessionState state = sessions.get(sessionKey);
    return state != null && state.channelSwitched();
  }

  /**
   * 채널 전환 시 활성 채널 기록. login() OTP 검증 시 user.defaultTwoFactorMethod 대신 이 값으로 채널 결정. null 이면 default
   * 사용.
   */
  public void setActiveChannel(String sessionKey, String channel) {
    sessions.compute(
        sessionKey,
        (k, state) -> {
          if (state == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "유효하지 않은 sessionKey 입니다.");
          }
          return state.withActiveChannel(channel);
        });
  }

  /** sessionKey 의 활성 채널 조회. 전환 이력 없으면 null (user.defaultTwoFactorMethod 사용). */
  public String getActiveChannel(String sessionKey) {
    SessionState state = sessions.get(sessionKey);
    return state == null ? null : state.activeChannel();
  }

  /** OTP 검증 성공 후 sessionKey 폐기 (재사용 방지). */
  public void invalidate(String sessionKey) {
    sessions.remove(sessionKey);
  }

  private String computeHmac(String input) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(CryptoUtils.hmacSha256(input, secret));
  }

  /** 만료된 sessionKey 메모리 정리 (1분 주기). */
  @Scheduled(fixedRate = 60 * 1000L)
  public void cleanup() {
    long cutoff = System.currentTimeMillis() - EXPIRY_MS;
    int before = sessions.size();
    sessions.entrySet().removeIf(e -> e.getValue().issuedAt() < cutoff);
    int removed = before - sessions.size();
    if (removed > 0) {
      log.debug("SessionKeyService cleanup: removed={}, remaining={}", removed, sessions.size());
    }
  }

  /**
   * sessionKey 의 in-memory 상태.
   *
   * @param userId 1차 인증 사용자 식별자
   * @param issuedAt 발급 시각 (epoch ms)
   * @param switchCount 채널 변경 누적 횟수 (≤ {@link #MAX_SWITCH_COUNT})
   * @param activeChannel 채널 전환으로 활성화된 채널("EMAIL"/"SMS"). 전환 이력 없으면 null
   *     (user.defaultTwoFactorMethod 사용)
   */
  private record SessionState(
      Integer userId, long issuedAt, int switchCount, String activeChannel) {

    /** 채널 변경 1회 이상 수행 여부 (강제 OTP 트리거용). switchCount 에서 파생. */
    boolean channelSwitched() {
      return switchCount > 0;
    }

    SessionState withSwitchIncrement() {
      return new SessionState(userId, issuedAt, switchCount + 1, activeChannel);
    }

    SessionState withActiveChannel(String channel) {
      return new SessionState(userId, issuedAt, switchCount, channel);
    }
  }
}
