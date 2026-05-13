package kr.wisead.common.util;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * HTTP 요청에서 클라이언트 IP를 추출하는 공용 유틸 (프록시/로드밸런서 환경 대응).
 *
 * <p>{@code X-Forwarded-For} → {@code Proxy-Client-IP} → {@code WL-Proxy-Client-IP} → {@code
 * HTTP_CLIENT_IP} → {@code HTTP_X_FORWARDED_FOR} → {@link HttpServletRequest#getRemoteAddr()} 순으로
 * 유효한 첫 값을 사용한다. 헤더 값에 콤마가 포함된 경우(다중 프록시) 첫 번째 IP만 반환한다.
 */
public final class ClientIpExtractor {

  private static final List<String> IP_HEADERS =
      List.of(
          "X-Forwarded-For",
          "Proxy-Client-IP",
          "WL-Proxy-Client-IP",
          "HTTP_CLIENT_IP",
          "HTTP_X_FORWARDED_FOR");

  private ClientIpExtractor() {
    // 유틸리티 클래스 인스턴스화 방지
  }

  /** 클라이언트 IP 추출. 유효 헤더 없으면 {@link HttpServletRequest#getRemoteAddr()} 반환. */
  public static String extract(HttpServletRequest request) {
    String ip =
        IP_HEADERS.stream()
            .map(request::getHeader)
            .filter(ClientIpExtractor::isValid)
            .findFirst()
            .orElse(request.getRemoteAddr());

    // 다중 IP (proxy chain) → 첫 번째 IP
    if (ip != null && ip.contains(",")) {
      ip = ip.split(",")[0].trim();
    }
    return ip;
  }

  private static boolean isValid(String ip) {
    return ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip);
  }
}
