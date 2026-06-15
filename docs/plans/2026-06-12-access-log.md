# 개인정보처리시스템 접근 로그 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 개인정보가 노출되는 관리자 조회 API에 진입할 때 누가/언제/어디서/무엇을(검색조건)을 `ACTION_LOG`에 자동 기록한다.

**Architecture:** `@AccessLog(menuName=...)` 어노테이션을 PII 조회 컨트롤러 메서드에 부착(옵인). `AccessLogInterceptor`(HandlerInterceptor)가 `afterCompletion`에서 어노테이션을 읽어 인증 사용자(복호화 ID/이름) · 요청 URI · 쿼리스트링(검색조건) · Referer(접속 페이지) · IP · 응답코드를 수집해 `ActionLogService.logAccess()`로 기록한다. 기록 실패는 fire-and-forget(요청 비차단).

**Tech Stack:** Java 21, Spring Boot 3.4, MyBatis, JUnit5 + Mockito + spring-test(MockHttpServletRequest), Maven.

**참고 파일(기존 패턴):**
- `domain/admin/service/ActionLogService.java` — 기존 ActionLog 기록/`getClientIp` 패턴
- `domain/audit/service/AuditEventService.java` — fire-and-forget try/catch 패턴
- `common/util/UserIdResolver.java` — `resolveUserId`/`resolveUserName`(복호화)
- `config/WebMvcConfig.java` — WebMvcConfigurer
- `security/jwt/CurrentUser.java` — 어노테이션 작성 참고

---

### Task 1: `@AccessLog` 어노테이션 생성

**Files:**
- Create: `src/main/java/kr/wisead/common/annotation/AccessLog.java`

**Step 1: 어노테이션 작성**

```java
package kr.wisead.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 개인정보 접근 로그 자동 기록 대상 표시.
 *
 * <p>이 어노테이션이 붙은 컨트롤러 메서드 호출 시 {@code AccessLogInterceptor}가 ACTION_LOG에
 * 접속 기록(누가/언제/어디서/검색조건)을 남긴다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AccessLog {

  /** 접속 메뉴명 (예: "개인정보취합 발송조회"). ACTION_LOG.MENU_NAME 에 기록된다. */
  String menuName();

  /** 액션 타입. 기본 "R"(조회). ACTION_LOG.ACTION_TYPE 에 기록된다. */
  String actionType() default "R";
}
```

**Step 2: 컴파일 확인 (사용자에게 빌드 요청 대신 Task 8에서 일괄)** — 이 단계는 코드 작성만.

**Step 3: Commit**

```bash
git add src/main/java/kr/wisead/common/annotation/AccessLog.java
git commit -m "feat: 개인정보 접근 로그용 @AccessLog 어노테이션 추가"
```

---

### Task 2: `ACTION_LOG` 스키마/엔티티/매퍼/응답에 SEARCH_CONDITION 추가

**Files:**
- Create: `src/main/resources/db/migration/2026-06-12-action-log-search-condition.sql`
- Modify: `src/main/java/kr/wisead/domain/admin/entity/ActionLog.java`
- Modify: `src/main/resources/mapper/primary/ActionLogMapper.xml`
- Modify: `src/main/java/kr/wisead/domain/admin/dto/ActionLogResponse.java`

**Step 1: 마이그레이션 SQL 작성**

```sql
-- 개인정보 접근 로그: 검색조건(무엇을) 저장 컬럼 추가
-- 운영 DB 수동 실행 필요
ALTER TABLE ACTION_LOG
    ADD COLUMN SEARCH_CONDITION VARCHAR(1000) NULL AFTER ACTION_REASON;
```

**Step 2: 엔티티에 필드 추가** — `ActionLog.java`의 `actionReason` 아래에 추가:

```java
    private String searchCondition; // 검색 조건 (쿼리스트링 원문)
```

**Step 3: 매퍼 resultMap + 조회컬럼 + 신규 insert 추가** — `ActionLogMapper.xml`

`actionLogResultMap`의 `actionReason` 매핑 아래에 추가:
```xml
        <result property="searchCondition" column="SEARCH_CONDITION"/>
```

`selectLogColumns`의 `L.ACTION_REASON,` 아래에 추가:
```xml
        L.SEARCH_CONDITION,
```

신규 insert 추가 (`insertDownloadLog` 아래):
```xml
    <insert id="insertAccessLog" parameterType="kr.wisead.domain.admin.entity.ActionLog" useGeneratedKeys="true" keyProperty="seq">
        INSERT INTO ACTION_LOG (
            MENU_NAME,
            ACTION_TYPE,
            SEARCH_CONDITION,
            MENU_URL,
            CODE,
            REFERER,
            USER_ID,
            USER_NAME,
            IP,
            REG_DATE
        ) VALUES (
            #{menuName},
            #{actionType},
            #{searchCondition},
            #{menuUrl},
            #{code},
            #{referer},
            #{userId},
            #{userName},
            #{ip},
            NOW()
        )
    </insert>
```

**Step 4: Mapper 인터페이스에 메서드 추가** — `mapper/primary/ActionLogMapper.java`에 `void insertAccessLog(ActionLog actionLog);` 추가 (기존 `insert`/`insertDownloadLog` 옆).

**Step 5: 응답 DTO에 필드 노출** — `ActionLogResponse.java`에 `searchCondition` 필드와 `from()` 매핑 추가 (기존 `actionReason` 처리부와 동일 형태로).

**Step 6: Commit**

```bash
git add src/main/resources/db/migration/2026-06-12-action-log-search-condition.sql \
        src/main/java/kr/wisead/domain/admin/entity/ActionLog.java \
        src/main/resources/mapper/primary/ActionLogMapper.xml \
        src/main/java/kr/wisead/mapper/primary/ActionLogMapper.java \
        src/main/java/kr/wisead/domain/admin/dto/ActionLogResponse.java
git commit -m "feat: ACTION_LOG에 검색조건(SEARCH_CONDITION) 컬럼 추가"
```

> ⚠️ 마이그레이션 SQL은 운영 DB에 **수동 실행** 필요 — 배포 가이드/PR 본문에 명시.

---

### Task 3: `ActionLogService.logAccess()` 추가 (단위 테스트 우선)

**Files:**
- Modify: `src/main/java/kr/wisead/domain/admin/service/ActionLogService.java`
- Test: `src/test/java/kr/wisead/domain/admin/service/ActionLogServiceAccessTest.java`

**Step 1: 실패 테스트 작성**

```java
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

    verify(actionLogMapper).insertAccessLog(any(ActionLog.class));
  }

  @Test
  @DisplayName("logAccess: insert 실패해도 예외 전파하지 않음(fire-and-forget)")
  void logAccess_swallowsException() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setRequestURI("/api/survey/users");
    doThrow(new RuntimeException("DB down")).when(actionLogMapper).insertAccessLog(any());

    // 예외가 전파되면 테스트 실패
    actionLogService.logAccess("admin01", "홍길동", "메뉴", "R", null, "200", req);
  }
}
```

**Step 2: 실패 확인**

Run: `mvn -q test -Dtest=ActionLogServiceAccessTest`
Expected: FAIL — `logAccess` / `insertAccessLog` 미정의 (컴파일 에러)

**Step 3: 구현** — `ActionLogService.java`에 추가 (getClientIp는 기존 private 재사용):

```java
  /**
   * 개인정보 접근 로그 기록 (fire-and-forget).
   *
   * <p>AccessLogInterceptor가 호출. 기록 실패가 요청 흐름을 막지 않도록 예외를 삼킨다.
   */
  public void logAccess(
      String userId,
      String userName,
      String menuName,
      String actionType,
      String searchCondition,
      String code,
      HttpServletRequest httpRequest) {
    try {
      ActionLog actionLog =
          ActionLog.builder()
              .menuName(menuName)
              .actionType(actionType)
              .searchCondition(searchCondition)
              .menuUrl(httpRequest.getRequestURI())
              .code(code)
              .referer(httpRequest.getHeader("Referer"))
              .userId(userId)
              .userName(userName)
              .ip(getClientIp(httpRequest))
              .build();
      actionLogMapper.insertAccessLog(actionLog);
      log.info("[접근로그] userId={}, menuName={}, uri={}", userId, menuName, httpRequest.getRequestURI());
    } catch (Exception e) {
      log.warn("[접근로그] 기록 실패: userId={}, menuName={}, 사유={}", userId, menuName, e.getMessage());
    }
  }
```

**Step 4: 통과 확인**

Run: `mvn -q test -Dtest=ActionLogServiceAccessTest`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/kr/wisead/domain/admin/service/ActionLogService.java \
        src/test/java/kr/wisead/domain/admin/service/ActionLogServiceAccessTest.java
git commit -m "feat: ActionLogService.logAccess 추가(개인정보 접근 로그 fire-and-forget)"
```

---

### Task 4: `AccessLogInterceptor` 구현 (단위 테스트 우선 — 핵심 로직)

**Files:**
- Create: `src/main/java/kr/wisead/common/interceptor/AccessLogInterceptor.java`
- Test: `src/test/java/kr/wisead/common/interceptor/AccessLogInterceptorTest.java`

**Step 1: 실패 테스트 작성**

```java
package kr.wisead.common.interceptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import kr.wisead.common.annotation.AccessLog;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.admin.service.ActionLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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

  // 테스트용 핸들러
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
        .setAuthentication(new UsernamePasswordAuthenticationToken("123", null, java.util.List.of()));
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
}
```

**Step 2: 실패 확인**

Run: `mvn -q test -Dtest=AccessLogInterceptorTest`
Expected: FAIL — `AccessLogInterceptor` 미정의

**Step 3: 구현**

```java
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
      if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
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
```

**Step 4: 통과 확인**

Run: `mvn -q test -Dtest=AccessLogInterceptorTest`
Expected: PASS

**Step 5: Commit**

```bash
git add src/main/java/kr/wisead/common/interceptor/AccessLogInterceptor.java \
        src/test/java/kr/wisead/common/interceptor/AccessLogInterceptorTest.java
git commit -m "feat: 개인정보 접근 로그 AccessLogInterceptor 추가"
```

---

### Task 5: `WebMvcConfig`에 인터셉터 등록

**Files:**
- Modify: `src/main/java/kr/wisead/config/WebMvcConfig.java`

**Step 1: 인터셉터 주입 + addInterceptors 추가**

```java
  private final AccessLogInterceptor accessLogInterceptor; // 필드 추가

  @Override
  public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
    registry.addInterceptor(accessLogInterceptor).addPathPatterns("/api/**");
  }
```

import 정리: `kr.wisead.common.interceptor.AccessLogInterceptor`, `InterceptorRegistry`.

**Step 2: Commit**

```bash
git add src/main/java/kr/wisead/config/WebMvcConfig.java
git commit -m "feat: AccessLogInterceptor를 /api/** 경로에 등록"
```

---

### Task 6: PII 조회 컨트롤러 메서드에 `@AccessLog` 부착

> 각 메서드 위에 `@AccessLog(menuName="...")` 추가. import: `kr.wisead.common.annotation.AccessLog`.
> **부착 전 각 컨트롤러를 다시 정독**하여 PII 노출 메서드 누락/추가가 없는지 최종 확인한다.

**Files & 대상 메서드:**

`domain/survey/controller/SurveyUserController.java`:
- `searchUsers` (GET `/`) → `menuName="개인정보취합/설문 발송조회 목록"`
- `getCompletedUsers` (GET `/completed`) → `"발송조회 완료자 목록"`
- `getAbsenteesAndLurkers` (GET `/absentees`) → `"발송조회 미참여자 목록"`
- `getUserBySeq` (GET `/{userSeq}`) → `"발송조회 참여자 상세"`
- `getUserByUserKey` (GET `/key/{userKey}`) → `"발송조회 참여자 상세(키)"`

`domain/history/controller/SendHistoryController.java`:
- `getSendHistory` (GET `/send`) → `"발송내역 조회"`
- `getOptOutList` (GET `/optout`) → `"수신차단 내역 조회"`

`domain/schedule/controller/ScheduledMessageController.java`:
- `getScheduledMessages` (GET `/`) → `"예약발송 내역 조회"`
- `getScheduledMessageById` (GET `/{mSeq}`) → `"예약발송 상세 조회"`

`domain/statistics/controller/StatisticsController.java`:
- `getUserStats` (GET `/user`) → `"통계 사용자별 조회"`
- `getUserStatsByServiceType` (GET `/user-stats`) → `"통계 사용자별 서비스 조회"`
- `getUserMsgStats` (GET `/user-stats/msg`) → `"통계 사용자별 메시지 조회"`
- `getUserSurveyStats` (GET `/user-stats/survey`) → `"통계 사용자별 설문 조회"`
- `getUserQrStats` (GET `/user-stats/qr`) → `"통계 사용자별 QR 조회"`

> 비-PII(개수/불리언/집계 totals) 메서드(`/count`, `/check-phone`, `/validate-key`, `/auth/**`, 통계 `daily`/`monthly`/`period`)는 제외.

**Step 1: 각 컨트롤러에 어노테이션 부착 + import 추가**

**Step 2: Commit**

```bash
git add src/main/java/kr/wisead/domain/survey/controller/SurveyUserController.java \
        src/main/java/kr/wisead/domain/history/controller/SendHistoryController.java \
        src/main/java/kr/wisead/domain/schedule/controller/ScheduledMessageController.java \
        src/main/java/kr/wisead/domain/statistics/controller/StatisticsController.java
git commit -m "feat: 개인정보 조회 API에 @AccessLog 부착(접속기록 자동화)"
```

---

### Task 7: 빌드 → simplify → 재빌드 → 검증 (프로젝트 CLAUDE.md 워크플로우)

1. **빌드 확인 요청** — 사용자에게 `mvn -q clean test` (또는 IDE 빌드) 실행 요청. Claude는 직접 빌드하지 않는다.
2. **실패 시** 에러 수정 후 재요청. **성공 시** 다음 단계.
3. **/simplify** 실행 — 추가 코드 품질/일관성 검토 및 개선.
4. **리팩토링 후 재빌드** 요청.
5. **커밋** — feature 브랜치(`feature/access-log`)에서 진행, PR base `develop`.

**검증 체크리스트(verification-before-completion):**
- [ ] `mvn test` 통과 (Task3·4 단위 테스트 포함)
- [ ] 어노테이션 부착 메서드 요청 시 ACTION_LOG에 row 생성(USER_ID/USER_NAME/REFERER/SEARCH_CONDITION/CODE 채워짐)
- [ ] 기록 실패가 정상 응답을 막지 않음(fire-and-forget)
- [ ] 검토화면 조회 응답에 `searchCondition` 노출
- [ ] 마이그레이션 SQL 운영 수동 실행 안내 PR 본문에 포함

---

## 미해결/확인 필요

- `/admin/event/sent`(이벤트 발송) 페이지의 백엔드 API 매핑 확인 — `/api/survey/users`(eventType 구분) 재사용이면 Task6의 `searchUsers` 부착으로 커버됨. 별도 컨트롤러면 해당 GET 메서드에 추가 부착.
- 검색조건이 평문 PII(이름/전화)를 포함할 수 있음 — 요구사항상 원문 저장. 접근로그 자체 열람 권한이 관리자로 제한되는지 확인 권장.
