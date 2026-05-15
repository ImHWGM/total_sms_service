package kr.wisead.security.sessionkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import kr.wisead.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * SessionKeyService 단위 테스트.
 *
 * <p>plan v5 §4 Phase D-8.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SessionKeyService (1차 인증 임시 세션 토큰)")
class SessionKeyServiceTest {

  private static final Integer USER_ID = 42;
  private static final String TEST_SECRET =
      "test-session-key-secret-for-unit-tests-only-must-be-at-least-256-bits-padding-padding";

  @InjectMocks private SessionKeyService sut;

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(sut, "secret", TEST_SECRET);
    // sessions 맵 클리어
    @SuppressWarnings("unchecked")
    Map<String, Object> sessions =
        (Map<String, Object>) ReflectionTestUtils.getField(sut, "sessions");
    if (sessions != null) {
      sessions.clear();
    }
  }

  @Test
  @DisplayName("issue_returnsBase64UrlWithoutPadding")
  void issue_returnsBase64UrlWithoutPadding() {
    String key = sut.issue(USER_ID);

    assertThat(key).isNotBlank();
    // Base64URL without padding: = 문자 없음
    assertThat(key).doesNotContain("=");
    // Base64URL: +, / 대신 -, _ 사용
    assertThat(key).doesNotContain("+").doesNotContain("/");
  }

  @Test
  @DisplayName("issue_differentCallsProduceDifferentKeys")
  void issue_differentCallsProduceDifferentKeys() {
    String key1 = sut.issue(USER_ID);
    String key2 = sut.issue(USER_ID);

    assertThat(key1).isNotEqualTo(key2);
  }

  @Test
  @DisplayName("validate_validKey_returnsUserId")
  void validate_validKey_returnsUserId() {
    String key = sut.issue(USER_ID);

    Integer result = sut.validate(key);

    assertThat(result).isEqualTo(USER_ID);
  }

  @Test
  @DisplayName("validate_invalidKey_throws")
  void validate_invalidKey_throws() {
    assertThatThrownBy(() -> sut.validate("nonexistent-key"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("유효하지 않은 sessionKey");
  }

  @Test
  @DisplayName("validate_nullKey_throws")
  void validate_nullKey_throws() {
    assertThatThrownBy(() -> sut.validate(null))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("sessionKey 가 필요합니다");
  }

  @Test
  @DisplayName("validate_blankKey_throws")
  void validate_blankKey_throws() {
    assertThatThrownBy(() -> sut.validate("  "))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("sessionKey 가 필요합니다");
  }

  @Test
  @DisplayName("validate_expiredKey_throws")
  void validate_expiredKey_throws() {
    String key = sut.issue(USER_ID);
    expireSession(key, 8);

    assertThatThrownBy(() -> sut.validate(key))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("만료");
  }

  @Test
  @DisplayName("recordChannelSwitch_incrementsSwitchCount")
  void recordChannelSwitch_incrementsSwitchCount() {
    String key = sut.issue(USER_ID);

    sut.recordChannelSwitch(key);

    // validate 는 여전히 성공해야 함 (폐기 아님)
    assertThat(sut.validate(key)).isEqualTo(USER_ID);
  }

  @Test
  @DisplayName("recordChannelSwitch_setsChannelSwitchedFlag")
  void recordChannelSwitch_setsChannelSwitchedFlag() {
    String key = sut.issue(USER_ID);
    assertThat(sut.isChannelRecentlySwitched(key)).isFalse();

    sut.recordChannelSwitch(key);

    assertThat(sut.isChannelRecentlySwitched(key)).isTrue();
  }

  @Test
  @DisplayName("recordChannelSwitch_exceeds3Count_throwsAndInvalidates")
  void recordChannelSwitch_exceeds3Count_throwsAndInvalidates() {
    String key = sut.issue(USER_ID);
    sut.recordChannelSwitch(key); // 1
    sut.recordChannelSwitch(key); // 2
    sut.recordChannelSwitch(key); // 3

    // 4번째 시도 → 상한 초과 → 폐기 + 예외
    assertThatThrownBy(() -> sut.recordChannelSwitch(key))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("채널 변경 횟수를 초과");

    // sessionKey 폐기됐으므로 validate 도 실패
    assertThatThrownBy(() -> sut.validate(key))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("유효하지 않은 sessionKey");
  }

  @Test
  @DisplayName("isChannelRecentlySwitched_freshSession_returnsFalse")
  void isChannelRecentlySwitched_freshSession_returnsFalse() {
    String key = sut.issue(USER_ID);

    assertThat(sut.isChannelRecentlySwitched(key)).isFalse();
  }

  @Test
  @DisplayName("isChannelRecentlySwitched_afterSwitch_returnsTrue")
  void isChannelRecentlySwitched_afterSwitch_returnsTrue() {
    String key = sut.issue(USER_ID);
    sut.recordChannelSwitch(key);

    assertThat(sut.isChannelRecentlySwitched(key)).isTrue();
  }

  @Test
  @DisplayName("isChannelRecentlySwitched_unknownKey_returnsFalse")
  void isChannelRecentlySwitched_unknownKey_returnsFalse() {
    assertThat(sut.isChannelRecentlySwitched("no-such-key")).isFalse();
  }

  @Test
  @DisplayName("invalidate_removesSession")
  void invalidate_removesSession() {
    String key = sut.issue(USER_ID);

    sut.invalidate(key);

    assertThatThrownBy(() -> sut.validate(key))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("유효하지 않은 sessionKey");
  }

  @Test
  @DisplayName("invalidate_nonExistentKey_doesNotThrow")
  void invalidate_nonExistentKey_doesNotThrow() {
    // 없는 key 폐기 시 예외 없음 (idempotent)
    sut.invalidate("ghost-key");
  }

  @Test
  @DisplayName("cleanup_removesExpiredSessions")
  void cleanup_removesExpiredSessions() {
    String key = sut.issue(USER_ID);
    expireSession(key, 8);

    sut.cleanup();

    assertThat(sessionMap()).doesNotContainKey(key);
  }

  @Test
  @DisplayName("cleanup_keepsValidSessions")
  void cleanup_keepsValidSessions() {
    String key = sut.issue(USER_ID);

    sut.cleanup();

    // 유효한 세션은 cleanup 후에도 남아야 함
    assertThat(sut.validate(key)).isEqualTo(USER_ID);
  }

  /**
   * N13 핵심: secret 미설정 시 HMAC 연산에서 NullPointerException 또는 IllegalStateException 발생 검증.
   *
   * <p>ApplicationContext 부팅 실패는 {@code @Value} 에 의해 보장되므로 여기서는 secret=null 주입 후 issue() 호출 시 예외
   * 전파를 검증한다.
   */
  @Test
  @DisplayName("failsFastWhenSecretIsNull_N13")
  void failsFastWhenSecretIsNull_N13() {
    ReflectionTestUtils.setField(sut, "secret", null);

    assertThatThrownBy(() -> sut.issue(USER_ID)).isInstanceOf(RuntimeException.class);
  }

  // --- helpers ---

  /** sessions 내부 맵을 반환한다 (reflection). */
  @SuppressWarnings("unchecked")
  private Map<String, Object> sessionMap() {
    return (Map<String, Object>) ReflectionTestUtils.getField(sut, "sessions");
  }

  /**
   * 특정 sessionKey 의 issuedAt 을 {@code minutesAgo} 분 전으로 역산하여 만료 상태로 만든다. SessionState 는 private
   * record 이므로 reflection 으로 교체한다.
   */
  private void expireSession(String key, int minutesAgo) {
    Map<String, Object> sessions = sessionMap();
    Object state = sessions.get(key);
    long expiredAt = System.currentTimeMillis() - ((long) minutesAgo * 60 * 1000L);
    try {
      Object expiredState =
          state.getClass().getDeclaredConstructors()[0].newInstance(USER_ID, expiredAt, 0, null);
      sessions.put(key, expiredState);
    } catch (Exception e) {
      throw new RuntimeException("SessionState 조작 실패: " + e.getMessage(), e);
    }
  }
}
