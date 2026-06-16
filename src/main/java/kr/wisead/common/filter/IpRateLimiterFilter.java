package kr.wisead.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kr.wisead.common.util.ClientIpExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * IP 기반 Rate Limiting 필터.
 *
 * <p>plan v5 §4 Phase C-Filter. 인증 시도/OTP 발송 엔드포인트에 대해 IP 기반 슬라이딩 윈도우 rate limit 적용.
 *
 * <p>제한: 1분당 5회. 초과 시 HTTP 429 반환. 메모리 누수 방지를 위해 5분마다 만료 entry 정리.
 */
@Slf4j
@Component
public class IpRateLimiterFilter extends OncePerRequestFilter {

  /** 1분 윈도우당 최대 요청 수 */
  private static final int MAX_REQUESTS = 5;

  /** 슬라이딩 윈도우 크기 (밀리초) */
  private static final long WINDOW_MS = 60 * 1000L;

  /** Rate limit 적용 대상 path (인증 시도/OTP 발송 엔드포인트) */
  private static final List<String> RATE_LIMITED_PATHS =
      List.of(
          "/api/auth/login", // 1차 인증 + OTP 발송
          "/api/auth/resend-email-code", // 이메일 OTP 재발송
          "/api/auth/switch-channel", // OTP 채널 전환 (재발송 포함)
          "/api/sms/verification", // 회원가입 SMS 인증 코드 발송
          "/api/sms/verification/resend", // 회원가입 SMS 인증 코드 재발송
          "/api/sms/verification/verify", // 회원가입 SMS 인증 코드 검증 (brute-force 방어)
          "/api/email/verification", // 회원가입 이메일 인증 코드 발송
          "/api/email/verification/resend", // 회원가입 이메일 인증 코드 재발송
          "/api/email/verification/verify" // 회원가입 이메일 인증 코드 검증 (brute-force 방어)
          );

  /** key=clientIp, value=요청 timestamps (밀리초) */
  private final ConcurrentHashMap<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return RATE_LIMITED_PATHS.stream().noneMatch(path::equals);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String clientIp = ClientIpExtractor.extract(request);
    long now = System.currentTimeMillis();

    Deque<Long> bucket = requestLog.computeIfAbsent(clientIp, k -> new ArrayDeque<>());
    synchronized (bucket) {
      // 1분 이전 timestamps 제거 (슬라이딩 윈도우)
      while (!bucket.isEmpty() && now - bucket.peekFirst() > WINDOW_MS) {
        bucket.pollFirst();
      }

      if (bucket.size() >= MAX_REQUESTS) {
        log.warn(
            "Rate limit 초과: ip={}, path={}, count={}",
            clientIp,
            request.getRequestURI(),
            bucket.size());
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response
            .getWriter()
            .write(
                "{\"success\":false,\"code\":\"C429\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시"
                    + " 시도해주세요.\"}");
        return;
      }

      bucket.offerLast(now);
    }

    chain.doFilter(request, response);
  }

  /**
   * 만료된 entry 정리 (메모리 누수 방지).
   *
   * <p>5분마다 실행. 윈도우 이전 timestamps 제거 후 비어 있으면 ip entry 삭제.
   */
  @Scheduled(fixedRate = 5 * 60 * 1000L)
  public void cleanup() {
    long cutoff = System.currentTimeMillis() - WINDOW_MS;
    requestLog.entrySet().removeIf(this::pruneAndCheckEmpty);
    log.debug(
        "IpRateLimiterFilter cleanup 완료: cutoff={}, remainingIps={}", cutoff, requestLog.size());
  }

  private boolean pruneAndCheckEmpty(Map.Entry<String, Deque<Long>> entry) {
    Deque<Long> bucket = entry.getValue();
    long cutoff = System.currentTimeMillis() - WINDOW_MS;
    synchronized (bucket) {
      while (!bucket.isEmpty() && bucket.peekFirst() < cutoff) {
        bucket.pollFirst();
      }
      return bucket.isEmpty();
    }
  }
}
