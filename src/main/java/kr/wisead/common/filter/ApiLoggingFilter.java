package kr.wisead.common.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.wisead.common.util.ClientIpExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** API 요청/응답 로깅 필터 - 요청 파라미터, 응답 결과 로깅 - 실행자 정보 (id, ip, userKey) 로깅 - 검색조건, 다운로드 사유 로깅 */
@Slf4j(topic = "API_LOG")
@Component
@RequiredArgsConstructor
public class ApiLoggingFilter extends OncePerRequestFilter {

  private final ObjectMapper objectMapper;

  private static final DateTimeFormatter DATETIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

  /** 로깅 제외 경로 패턴 */
  private static final List<String> EXCLUDE_PATTERNS =
      List.of(
          "/actuator",
          "/swagger",
          "/v3/api-docs",
          "/favicon.ico",
          "/files/",
          "/survey/",
          "/mmsfile/",
          "/template/",
          "/bizreg/",
          "/qrcode/");

  /** 민감 정보 마스킹 대상 필드 */
  private static final Set<String> SENSITIVE_FIELDS =
      Set.of(
          "password",
          "pwd",
          "passwd",
          "secret",
          "token",
          "accessToken",
          "refreshToken",
          "cardNumber",
          "cvv",
          "ssn",
          "주민등록번호");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // 제외 경로 체크
    if (shouldExclude(request.getRequestURI())) {
      filterChain.doFilter(request, response);
      return;
    }

    // OPTIONS(CORS preflight), Multipart 요청은 래핑하지 않음
    // - OPTIONS: ContentCachingResponseWrapper가 CORS 응답 헤더 전달을 방해
    // - Multipart: ContentCachingRequestWrapper가 InputStream 소비하여 파일 파싱 hang
    String contentType = request.getContentType();
    if ("OPTIONS".equalsIgnoreCase(request.getMethod())
        || (contentType != null && contentType.regionMatches(true, 0, "multipart/", 0, 10))) {
      filterChain.doFilter(request, response);
      return;
    }

    // 요청/응답 래핑 (body 재사용 가능하도록)
    ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
    ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

    long startTime = System.currentTimeMillis();
    String requestId = generateRequestId();

    try {
      filterChain.doFilter(wrappedRequest, wrappedResponse);
    } finally {
      long duration = System.currentTimeMillis() - startTime;
      logApiCall(wrappedRequest, wrappedResponse, requestId, duration);

      // 응답 본문을 클라이언트에 전달
      wrappedResponse.copyBodyToResponse();
    }
  }

  private void logApiCall(
      ContentCachingRequestWrapper request,
      ContentCachingResponseWrapper response,
      String requestId,
      long duration) {
    try {
      StringBuilder logBuilder = new StringBuilder();
      logBuilder.append("\n========== API LOG [").append(requestId).append("] ==========\n");

      // 시간 정보
      logBuilder
          .append("[Time] ")
          .append(LocalDateTime.now().format(DATETIME_FORMATTER))
          .append("\n");
      logBuilder.append("[Duration] ").append(duration).append("ms\n");

      // 요청 기본 정보
      logBuilder
          .append("[Request] ")
          .append(request.getMethod())
          .append(" ")
          .append(request.getRequestURI())
          .append("\n");

      // 실행자 정보
      logBuilder.append("[Executor] ").append(getExecutorInfo(request)).append("\n");

      // 요청 파라미터
      String queryParams = getQueryParameters(request);
      if (!queryParams.isEmpty()) {
        logBuilder.append("[Query Params] ").append(queryParams).append("\n");
      }

      // 요청 Body
      String requestBody = getRequestBody(request);
      if (!requestBody.isEmpty()) {
        logBuilder.append("[Request Body] ").append(maskSensitiveData(requestBody)).append("\n");
      }

      // 검색조건 / 다운로드 사유 추출
      String searchInfo = extractSearchInfo(request, requestBody);
      if (!searchInfo.isEmpty()) {
        logBuilder.append("[Search/Download Info] ").append(searchInfo).append("\n");
      }

      // 응답 정보
      logBuilder.append("[Response Status] ").append(response.getStatus()).append("\n");

      // 응답 Body
      String responseBody = getResponseBody(response);
      if (!responseBody.isEmpty()) {
        logBuilder
            .append("[Response Body] ")
            .append(truncateIfNeeded(responseBody, 2000))
            .append("\n");
      }

      logBuilder.append("================================================\n");

      // 로그 레벨 결정
      int status = response.getStatus();
      if (status >= 500) {
        log.error(logBuilder.toString());
      } else if (status >= 400) {
        log.warn(logBuilder.toString());
      } else {
        log.info(logBuilder.toString());
      }

    } catch (Exception e) {
      log.error("API 로깅 중 오류 발생: {}", e.getMessage(), e);
    }
  }

  /** 실행자 정보 추출 우선순위: userId > userKey (설문응답자) > IP */
  private String getExecutorInfo(HttpServletRequest request) {
    StringBuilder info = new StringBuilder();

    // 1. 인증된 사용자 ID
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
      info.append("userId=").append(auth.getName());
    }

    // 2. 설문응답자 userKey
    String userKey = request.getParameter("userKey");
    if (userKey == null) {
      userKey = request.getParameter("user_key");
    }
    if (userKey != null && !userKey.isEmpty()) {
      if (info.length() > 0) info.append(", ");
      info.append("userKey=").append(userKey);
    }

    // 3. IP 주소 (항상 포함)
    String clientIp = getClientIp(request);
    if (info.length() > 0) info.append(", ");
    info.append("ip=").append(clientIp);

    return info.toString();
  }

  /** 클라이언트 IP 추출 (프록시 고려) */
  private String getClientIp(HttpServletRequest request) {
    return ClientIpExtractor.extract(request);
  }

  /** 쿼리 파라미터 추출 */
  private String getQueryParameters(HttpServletRequest request) {
    Map<String, String[]> paramMap = request.getParameterMap();
    if (paramMap.isEmpty()) {
      return "";
    }

    StringBuilder params = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
      if (!first) params.append(", ");
      first = false;

      String key = entry.getKey();
      String value = String.join(",", entry.getValue());

      // 민감 정보 마스킹
      if (isSensitiveField(key)) {
        value = "****";
      }

      params.append(key).append("=").append(value);
    }
    params.append("}");
    return params.toString();
  }

  /** 요청 Body 추출 */
  private String getRequestBody(ContentCachingRequestWrapper request) {
    byte[] content = request.getContentAsByteArray();
    if (content.length == 0) {
      return "";
    }
    return new String(content, StandardCharsets.UTF_8);
  }

  /** 로깅 제외 Content-Type (바이너리 파일) */
  private static final Set<String> BINARY_CONTENT_TYPES =
      Set.of(
          "application/octet-stream",
          "application/pdf",
          "application/zip",
          "application/vnd.ms-excel",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/vnd.openxmlformats-officedocument.presentationml.presentation",
          "image/png",
          "image/jpeg",
          "image/gif",
          "image/webp",
          "audio/",
          "video/");

  /** 응답 Body 추출 (바이너리 제외) */
  private String getResponseBody(ContentCachingResponseWrapper response) {
    // 바이너리 Content-Type은 로깅 제외
    String contentType = response.getContentType();
    if (contentType != null && isBinaryContentType(contentType)) {
      return "[Binary content - "
          + contentType
          + ", size: "
          + response.getContentSize()
          + " bytes]";
    }

    byte[] content = response.getContentAsByteArray();
    if (content.length == 0) {
      return "";
    }
    return new String(content, StandardCharsets.UTF_8);
  }

  /** 바이너리 Content-Type 여부 확인 */
  private boolean isBinaryContentType(String contentType) {
    if (contentType == null) {
      return false;
    }
    String lowerType = contentType.toLowerCase();
    return BINARY_CONTENT_TYPES.stream().anyMatch(lowerType::startsWith);
  }

  /** 검색조건 / 다운로드 사유 추출 */
  private String extractSearchInfo(HttpServletRequest request, String requestBody) {
    StringBuilder info = new StringBuilder();
    String uri = request.getRequestURI().toLowerCase();

    // 다운로드 관련 엔드포인트
    if (uri.contains("download") || uri.contains("export") || uri.contains("excel")) {
      // 다운로드 사유
      String reason = request.getParameter("downloadReason");
      if (reason == null) reason = request.getParameter("reason");
      if (reason == null) reason = request.getParameter("purpose");

      if (reason != null && !reason.isEmpty()) {
        info.append("다운로드 사유: ").append(reason);
      } else {
        info.append("다운로드 요청 (사유 미기재)");
      }
    }

    // 검색/조회 관련 엔드포인트
    if (uri.contains("search") || uri.contains("list") || uri.contains("statistics")) {
      List<String> searchParams = new ArrayList<>();

      // 날짜 범위
      String startDate = request.getParameter("startDate");
      String endDate = request.getParameter("endDate");
      if (startDate != null || endDate != null) {
        searchParams.add("기간: " + startDate + " ~ " + endDate);
      }

      // 사용자 필터
      String userId = request.getParameter("userId");
      String[] userIds = request.getParameterValues("userIds[]");
      if (userId != null) {
        searchParams.add("userId: " + userId);
      }
      if (userIds != null && userIds.length > 0) {
        searchParams.add("userIds: " + String.join(",", userIds));
      }

      // 서비스 타입
      String serviceType = request.getParameter("serviceType");
      if (serviceType != null) {
        searchParams.add("serviceType: " + serviceType);
      }

      // 키워드
      String keyword = request.getParameter("keyword");
      String searchKeyword = request.getParameter("searchKeyword");
      if (keyword != null) searchParams.add("keyword: " + keyword);
      if (searchKeyword != null) searchParams.add("searchKeyword: " + searchKeyword);

      if (!searchParams.isEmpty()) {
        if (info.length() > 0) info.append(", ");
        info.append("검색조건: {").append(String.join(", ", searchParams)).append("}");
      }
    }

    // Request Body에서 검색조건 추출
    if (requestBody != null && !requestBody.isEmpty()) {
      try {
        JsonNode jsonNode = objectMapper.readTree(requestBody);
        List<String> bodyParams = new ArrayList<>();

        extractJsonField(jsonNode, "startDate", bodyParams);
        extractJsonField(jsonNode, "endDate", bodyParams);
        extractJsonField(jsonNode, "keyword", bodyParams);
        extractJsonField(jsonNode, "downloadReason", bodyParams);
        extractJsonField(jsonNode, "reason", bodyParams);

        if (!bodyParams.isEmpty()) {
          if (info.length() > 0) info.append(", ");
          info.append("Body 조건: {").append(String.join(", ", bodyParams)).append("}");
        }
      } catch (Exception ignored) {
        // JSON 파싱 실패 시 무시
      }
    }

    return info.toString();
  }

  private void extractJsonField(JsonNode node, String fieldName, List<String> result) {
    if (node.has(fieldName) && !node.get(fieldName).isNull()) {
      result.add(fieldName + ": " + node.get(fieldName).asText());
    }
  }

  /** 민감 정보 마스킹 */
  private String maskSensitiveData(String data) {
    if (data == null || data.isEmpty()) return data;

    try {
      JsonNode jsonNode = objectMapper.readTree(data);
      return maskJsonNode(jsonNode).toString();
    } catch (Exception e) {
      // JSON이 아닌 경우 원본 반환
      return data;
    }
  }

  private JsonNode maskJsonNode(JsonNode node) {
    if (node.isObject()) {
      var objectNode = objectMapper.createObjectNode();
      node.fields()
          .forEachRemaining(
              entry -> {
                if (isSensitiveField(entry.getKey())) {
                  objectNode.put(entry.getKey(), "****");
                } else {
                  objectNode.set(entry.getKey(), maskJsonNode(entry.getValue()));
                }
              });
      return objectNode;
    } else if (node.isArray()) {
      var arrayNode = objectMapper.createArrayNode();
      node.forEach(item -> arrayNode.add(maskJsonNode(item)));
      return arrayNode;
    }
    return node;
  }

  private boolean isSensitiveField(String fieldName) {
    if (fieldName == null) return false;
    String lowerField = fieldName.toLowerCase();
    return SENSITIVE_FIELDS.stream()
        .anyMatch(sensitive -> lowerField.contains(sensitive.toLowerCase()));
  }

  /** 긴 문자열 자르기 */
  private String truncateIfNeeded(String data, int maxLength) {
    if (data == null || data.length() <= maxLength) {
      return data;
    }
    return data.substring(0, maxLength) + "... [truncated, total: " + data.length() + " chars]";
  }

  /** 제외 경로 체크 */
  private boolean shouldExclude(String uri) {
    return EXCLUDE_PATTERNS.stream().anyMatch(uri::startsWith);
  }

  /** 요청 ID 생성 */
  private String generateRequestId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }
}
