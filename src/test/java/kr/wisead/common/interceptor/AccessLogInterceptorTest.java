package kr.wisead.common.interceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import kr.wisead.common.annotation.AccessLog;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.admin.service.ActionLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

@ExtendWith(MockitoExtension.class)
class AccessLogInterceptorTest {

  @Mock ActionLogService actionLogService;
  @Mock UserIdResolver userIdResolver;
  @InjectMocks AccessLogInterceptor interceptor;

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  static class SampleController {
    @AccessLog(menuName = "개인정보취합 발송조회")
    public void annotated() {}

    public void plain() {}
  }

  private HandlerMethod handler(String method) throws NoSuchMethodException {
    Method m = SampleController.class.getMethod(method);
    return new HandlerMethod(new SampleController(), m);
  }

  private void authenticate() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("123", null, List.of()));
  }

  @Test
  @DisplayName("어노테이션 있는 핸들러: 검색조건/사용자 캡처 후 logAccess 호출")
  void annotated_logsAccess() throws Exception {
    authenticate();
    when(userIdResolver.resolveUserId("123")).thenReturn("admin01");
    when(userIdResolver.resolveUserName("admin01")).thenReturn("홍길동");

    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setRequestURI("/api/survey/users");
    req.setQueryString("eventType=P&keyword=%ED%99%8D%EA%B8%B8%EB%8F%99");
    MockHttpServletResponse res = new MockHttpServletResponse();
    res.setStatus(200);

    interceptor.afterCompletion(req, res, handler("annotated"), null);

    verify(actionLogService)
        .logAccess(
            eq("admin01"), eq("홍길동"), eq("개인정보취합 발송조회"), eq("R"),
            eq("eventType=P&keyword=홍길동"), eq("200"), any());
  }

  @Test
  @DisplayName("어노테이션 없는 핸들러: 기록하지 않음")
  void plain_doesNotLog() throws Exception {
    authenticate();
    interceptor.afterCompletion(
        new MockHttpServletRequest(), new MockHttpServletResponse(), handler("plain"), null);
    verify(actionLogService, never()).logAccess(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("미인증: 기록하지 않음")
  void unauthenticated_doesNotLog() throws Exception {
    interceptor.afterCompletion(
        new MockHttpServletRequest(), new MockHttpServletResponse(), handler("annotated"), null);
    verify(actionLogService, never()).logAccess(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("핸들러가 HandlerMethod가 아니면 무시")
  void nonHandlerMethod_ignored() {
    interceptor.afterCompletion(
        new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);
    verify(actionLogService, never()).logAccess(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("익명 인증(AnonymousAuthenticationToken): 기록하지 않음")
  void anonymous_doesNotLog() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
    interceptor.afterCompletion(
        new MockHttpServletRequest(),
        new MockHttpServletResponse(),
        handler("annotated"), null);
    verify(actionLogService, never()).logAccess(any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @DisplayName("쿼리스트링 없음: searchCondition=null로 기록")
  void nullQueryString_passesNull() throws Exception {
    authenticate();
    when(userIdResolver.resolveUserId("123")).thenReturn("admin01");
    when(userIdResolver.resolveUserName("admin01")).thenReturn("홍길동");
    var req = new MockHttpServletRequest();
    req.setRequestURI("/api/survey/users"); // no query string
    var res = new MockHttpServletResponse();
    res.setStatus(200);
    interceptor.afterCompletion(req, res, handler("annotated"), null);
    verify(actionLogService).logAccess(eq("admin01"), eq("홍길동"), any(), eq("R"), eq(null), eq("200"), any());
  }

  @Test
  @DisplayName("긴 쿼리스트링: 1000자로 절단")
  void longQueryString_truncatedTo1000() throws Exception {
    authenticate();
    when(userIdResolver.resolveUserId("123")).thenReturn("admin01");
    when(userIdResolver.resolveUserName("admin01")).thenReturn("홍길동");
    String longQuery = "q=" + "a".repeat(1500);
    var req = new MockHttpServletRequest();
    req.setRequestURI("/api/survey/users");
    req.setQueryString(longQuery);
    var res = new MockHttpServletResponse();
    res.setStatus(200);
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    interceptor.afterCompletion(req, res, handler("annotated"), null);
    verify(actionLogService).logAccess(any(), any(), any(), any(), captor.capture(), any(), any());
    assertEquals(1000, captor.getValue().length());
  }
}
