package kr.wisead.common.filter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * IpRateLimiterFilter 단위 테스트.
 *
 * <p>plan v5 §4 Phase C-Filter. 슬라이딩 윈도우 1분/5회 IP rate limit 검증.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("IpRateLimiterFilter (IP rate limit)")
class IpRateLimiterFilterTest {

  private static final String CLIENT_IP = "1.2.3.4";
  private static final String OTHER_IP = "5.6.7.8";
  private static final long WINDOW_MS = 60 * 1000L;

  private IpRateLimiterFilter sut;

  @BeforeEach
  void setUp() {
    sut = new IpRateLimiterFilter();
  }

  @Test
  @DisplayName("화이트리스트 path 만 필터링 한다 (N15 단언)")
  void appliesOnlyToWhitelistedPaths() {
    assertThat(sut.shouldNotFilter(buildRequest("/api/auth/login", CLIENT_IP))).isFalse();
    assertThat(sut.shouldNotFilter(buildRequest("/api/auth/switch-channel", CLIENT_IP))).isFalse();
    assertThat(sut.shouldNotFilter(buildRequest("/api/auth/resend-email-code", CLIENT_IP)))
        .isFalse();
    // 비대상 path 는 skip
    assertThat(sut.shouldNotFilter(buildRequest("/api/other", CLIENT_IP))).isTrue();
    assertThat(sut.shouldNotFilter(buildRequest("/api/auth/signup", CLIENT_IP))).isTrue();
    assertThat(sut.shouldNotFilter(buildRequest("/api/auth/refresh", CLIENT_IP))).isTrue();
  }

  @Test
  @DisplayName("1분 윈도우 안에서 5건은 모두 통과한다")
  void allows5RequestsInOneMinute() throws Exception {
    for (int i = 0; i < 5; i++) {
      MockHttpServletResponse response = invoke(CLIENT_IP);
      assertThat(response.getStatus()).isEqualTo(200);
    }
  }

  @Test
  @DisplayName("6번째 요청은 429 로 차단된다")
  void blocks6thRequestWith429() throws Exception {
    for (int i = 0; i < 5; i++) {
      invoke(CLIENT_IP);
    }

    MockHttpServletResponse response = invoke(CLIENT_IP);
    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getContentType()).contains("application/json");
    assertThat(response.getContentAsString()).contains("\"code\":\"C429\"");
    assertThat(response.getContentAsString()).contains("요청이 너무 많습니다");
  }

  @Test
  @DisplayName("60초 이상 지난 timestamps 는 슬라이딩 윈도우에서 제외된다")
  void slidingWindowRecoversAfter60Seconds() throws Exception {
    // 5건 채움
    for (int i = 0; i < 5; i++) {
      invoke(CLIENT_IP);
    }

    // 저장된 timestamps 를 61초 과거로 조작
    Deque<Long> log = getLog(CLIENT_IP);
    Deque<Long> aged = new ArrayDeque<>();
    long shift = WINDOW_MS + 1000L;
    for (Long ts : log) {
      aged.offerLast(ts - shift);
    }
    log.clear();
    log.addAll(aged);

    // 윈도우 밖 timestamps 가 제거되어 6번째 요청이 통과해야 함
    MockHttpServletResponse response = invoke(CLIENT_IP);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("서로 다른 IP 는 독립적으로 카운팅 된다")
  void differentIpsTrackedSeparately() throws Exception {
    // IP A 5건 소진
    for (int i = 0; i < 5; i++) {
      invoke(CLIENT_IP);
    }

    // IP B 는 영향 받지 않고 1건 통과
    MockHttpServletResponse responseB = invoke(OTHER_IP);
    assertThat(responseB.getStatus()).isEqualTo(200);

    // IP A 는 6건째 차단
    MockHttpServletResponse responseA = invoke(CLIENT_IP);
    assertThat(responseA.getStatus()).isEqualTo(429);
  }

  @Test
  @DisplayName("X-Forwarded-For 헤더의 첫 IP 가 우선 사용된다")
  void xForwardedForHeaderTakesPriority() throws Exception {
    String xff = "9.9.9.9, 10.0.0.1";
    String fallbackIp = "127.0.0.1";

    // X-Forwarded-For 헤더로 5건 소진 (remoteAddr 는 무관)
    for (int i = 0; i < 5; i++) {
      MockHttpServletRequest request = buildRequest("/api/auth/login", fallbackIp);
      request.addHeader("X-Forwarded-For", xff);
      MockHttpServletResponse response = new MockHttpServletResponse();
      sut.doFilter(request, response, new MockFilterChain());
    }

    // 동일 X-Forwarded-For 로 6건째 → 429
    MockHttpServletRequest sixth = buildRequest("/api/auth/login", fallbackIp);
    sixth.addHeader("X-Forwarded-For", xff);
    MockHttpServletResponse sixthResponse = new MockHttpServletResponse();
    sut.doFilter(sixth, sixthResponse, new MockFilterChain());
    assertThat(sixthResponse.getStatus()).isEqualTo(429);

    // 다른 X-Forwarded-For 는 별개 IP 로 추적되어 통과
    MockHttpServletRequest otherXffRequest = buildRequest("/api/auth/login", fallbackIp);
    otherXffRequest.addHeader("X-Forwarded-For", "8.8.8.8");
    MockHttpServletResponse otherXffResponse = new MockHttpServletResponse();
    sut.doFilter(otherXffRequest, otherXffResponse, new MockFilterChain());
    assertThat(otherXffResponse.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("cleanup() 은 윈도우 밖 entry 만 제거한다")
  void cleanupRemovesExpiredEntries() throws Exception {
    // IP A: 만료된 timestamps 만 보유
    invoke(CLIENT_IP);
    Deque<Long> oldLog = getLog(CLIENT_IP);
    long shift = WINDOW_MS + 5000L;
    Deque<Long> aged = new ArrayDeque<>();
    for (Long ts : oldLog) {
      aged.offerLast(ts - shift);
    }
    oldLog.clear();
    oldLog.addAll(aged);

    // IP B: 최근 timestamps 보유
    invoke(OTHER_IP);

    sut.cleanup();

    ConcurrentHashMap<String, Deque<Long>> store = getStore();
    assertThat(store).doesNotContainKey(CLIENT_IP);
    assertThat(store).containsKey(OTHER_IP);
  }

  // ---- helpers ----

  private MockHttpServletRequest buildRequest(String path, String remoteAddr) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(path);
    request.setRemoteAddr(remoteAddr);
    return request;
  }

  private MockHttpServletResponse invoke(String remoteAddr) throws ServletException, IOException {
    MockHttpServletRequest request = buildRequest("/api/auth/login", remoteAddr);
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = new MockFilterChain();
    sut.doFilter(request, response, chain);
    return response;
  }

  @SuppressWarnings("unchecked")
  private ConcurrentHashMap<String, Deque<Long>> getStore() {
    return (ConcurrentHashMap<String, Deque<Long>>) ReflectionTestUtils.getField(sut, "requestLog");
  }

  private Deque<Long> getLog(String ip) {
    ConcurrentHashMap<String, Deque<Long>> store = getStore();
    Deque<Long> log = store.get(ip);
    assertThat(log).as("log for ip=%s", ip).isNotNull();
    return log;
  }
}
