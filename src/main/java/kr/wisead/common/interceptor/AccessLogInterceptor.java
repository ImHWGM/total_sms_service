package kr.wisead.common.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import kr.wisead.common.annotation.AccessLog;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.admin.service.ActionLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 개인정보 접근 로그 인터셉터.
 *
 * <p>{@link AccessLog} 어노테이션이 붙은 컨트롤러 메서드 요청에 대해 응답 완료 후 ACTION_LOG에 접속 기록을 남긴다.
 * 기록 실패는 요청 흐름을 막지 않는다(fire-and-forget).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessLogInterceptor implements HandlerInterceptor {

  /** 검색조건 저장 컬럼 길이 상한 */
  private static final int MAX_SEARCH_CONDITION = 1000;

  private final ActionLogService actionLogService;
  private final UserIdResolver userIdResolver;

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return;
    }
    AccessLog annotation = handlerMethod.getMethodAnnotation(AccessLog.class);
    if (annotation == null) {
      return;
    }
    try {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth == null
          || !auth.isAuthenticated()
          || auth instanceof AnonymousAuthenticationToken
          || auth.getName() == null) {
        return;
      }
      String userId = userIdResolver.resolveUserId(auth.getName());
      String userName = userIdResolver.resolveUserName(userId);
      String searchCondition = decodeQuery(request.getQueryString());
      String code = String.valueOf(response.getStatus());

      actionLogService.logAccess(
          userId, userName, annotation.menuName(), annotation.actionType(), searchCondition, code,
          request);
    } catch (Exception e) {
      log.warn("[접근로그] 인터셉터 처리 실패: uri={}, 사유={}", request.getRequestURI(), e.getMessage());
    }
  }

  /** 쿼리스트링을 읽기 좋게 디코딩하고 길이를 제한한다. */
  private String decodeQuery(String queryString) {
    if (queryString == null || queryString.isBlank()) {
      return null;
    }
    String decoded;
    try {
      decoded = URLDecoder.decode(queryString, StandardCharsets.UTF_8);
    } catch (Exception e) {
      decoded = queryString;
    }
    return decoded.length() > MAX_SEARCH_CONDITION
        ? decoded.substring(0, MAX_SEARCH_CONDITION)
        : decoded;
  }
}
