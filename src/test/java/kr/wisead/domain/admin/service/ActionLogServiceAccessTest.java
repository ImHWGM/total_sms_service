package kr.wisead.domain.admin.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import kr.wisead.domain.admin.entity.ActionLog;
import kr.wisead.mapper.primary.ActionLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class ActionLogServiceAccessTest {

  @Mock ActionLogMapper actionLogMapper;
  @InjectMocks ActionLogService actionLogService;

  @Test
  @DisplayName("logAccess: SEARCH_CONDITION 포함 ACTION_LOG insert 호출")
  void logAccess_insertsWithSearchCondition() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setRequestURI("/api/survey/users");
    req.addHeader("Referer", "https://wisead.kr/admin/survey/sent");

    actionLogService.logAccess(
        "admin01", "홍길동", "개인정보취합 발송조회", "R", "eventType=P&keyword=홍길동", "200", req);

    ArgumentCaptor<ActionLog> captor = ArgumentCaptor.forClass(ActionLog.class);
    verify(actionLogMapper).insertAccessLog(captor.capture());
    ActionLog saved = captor.getValue();
    assertEquals("admin01", saved.getUserId());
    assertEquals("홍길동", saved.getUserName());
    assertEquals("개인정보취합 발송조회", saved.getMenuName());
    assertEquals("R", saved.getActionType());
    assertEquals("eventType=P&keyword=홍길동", saved.getSearchCondition());
    assertEquals("/api/survey/users", saved.getMenuUrl());
    assertEquals("https://wisead.kr/admin/survey/sent", saved.getReferer());
    assertEquals("200", saved.getCode());
  }

  @Test
  @DisplayName("logAccess: insert 실패해도 예외 전파하지 않음(fire-and-forget)")
  void logAccess_swallowsException() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setRequestURI("/api/survey/users");
    doThrow(new RuntimeException("DB down")).when(actionLogMapper).insertAccessLog(any());

    actionLogService.logAccess("admin01", "홍길동", "메뉴", "R", null, "200", req);
  }
}
