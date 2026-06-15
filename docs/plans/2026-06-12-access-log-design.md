# 개인정보처리시스템 접근 로그 설계

> 컴플라이언스 항목 "20. 개인정보처리시스템 접근권한 및 접속기록 검토 내역" 대응.
> 개인정보가 노출되는 관리자 페이지 진입/조회 시 누가/언제/어디서/무엇을 자동 기록한다.

작성일: 2026-06-12

## 1. 배경 / 요구사항

개인정보가 표시되는 관리자 페이지(`/admin/survey/sent`, `/admin/event/sent`,
`/admin/history/list|reserved|blocked`, `/admin/stats/user` 등)에 접근할 때 다음을 기록해야 한다.

- **누가**: 이메일/이름 (관리자 식별)
- **언제**: 접속 시각
- **어디서**: 접속 페이지
- **무엇을**: 검색 조건 데이터 (검색했을 경우)

## 2. 결정 사항 (브레인스토밍 결과)

| 항목 | 결정 |
|---|---|
| 수집 방식 | 혼합 — 백엔드 자동 기록 기본, 접속 페이지는 FE Referer로 보강 |
| 저장소 | 기존 `ACTION_LOG` 테이블 재사용 |
| 구현 방식 | `@AccessLog` 어노테이션 + `HandlerInterceptor` (옵인) |
| 검색조건 저장 | 신규 `SEARCH_CONDITION` 컬럼 추가 (쿼리스트링 원문 저장) |
| 기록 범위 | 목록/검색 조회 + 단건 상세 조회 포함 |

## 3. 아키텍처

```
@AccessLog(menuName="개인정보취합 발송")   ← PII 조회 컨트롤러 메서드에 부착
        │
   AccessLogInterceptor (afterCompletion)
        │  · 인증정보  → userId(복호화), userName(복호화)
        │  · request   → URI, queryString(검색조건), Referer(접속 페이지), client IP
        │  · response  → status code
        ▼
   ActionLogService.logAccess(...)  →  ACTION_LOG insert
        (fire-and-forget: 실패해도 요청 흐름 차단하지 않음)
```

- 옵인 어노테이션이라 `@AccessLog`가 붙은 핸들러만 기록 → PII 엔드포인트만 정확히 선별.
- `afterCompletion` 단계에서 처리하여 응답 상태코드까지 캡처, 비즈니스 로직과 분리.

## 4. 데이터 매핑

| 요구 | ACTION_LOG 컬럼 | 출처 |
|---|---|---|
| 누가 | `USER_ID`, `USER_NAME` | JWT subject(seq) → `UserIdResolver.resolveUserId` / `resolveUserName`(복호화) |
| 언제 | `REG_DATE` | DB insert 시각 |
| 어디서(접속 페이지) | `REFERER` | 요청 `Referer` 헤더 (예: `/admin/survey/sent`) |
| API 경로 | `MENU_URL` | `request.getRequestURI()` |
| 무엇을(검색조건) | `SEARCH_CONDITION` (신규) | `request.getQueryString()` 원문 |
| 메뉴명 | `MENU_NAME` | `@AccessLog(menuName=...)` |
| 결과 | `CODE` | 응답 status code |

## 5. 구성 요소

| 구분 | 파일 | 내용 |
|---|---|---|
| 신규 | `common/annotation/AccessLog.java` | `menuName`, `actionType`(기본 `"R"`) |
| 신규 | `common/interceptor/AccessLogInterceptor.java` | `HandlerInterceptor`. `HandlerMethod`에서 어노테이션 읽고 기록 |
| 수정 | `config/WebMvcConfig.java` | `addInterceptors`로 인터셉터 등록 (`/api/**`) |
| 수정 | `admin/service/ActionLogService.java` | `logAccess(...)` 추가 (try/catch fire-and-forget) |
| 수정 | `admin/entity/ActionLog.java` | `searchCondition` 필드 추가 |
| 수정 | `mapper/primary/ActionLogMapper.xml` | insert + 검토화면 조회에 `SEARCH_CONDITION` 반영 |
| 수정 | `admin/dto/ActionLogResponse.java` | `searchCondition` 노출 |
| 신규 | 마이그레이션 SQL | `ALTER TABLE ACTION_LOG ADD SEARCH_CONDITION` |

## 6. 대상 엔드포인트 (계획 단계에서 정독해 확정)

각 컨트롤러를 정독하여 PII 노출 GET 메서드를 빠짐없이 식별한다. 1차 후보:

- `SurveyUserController` (`/api/survey/users`): 목록, `/{userSeq}`, `/key/{userKey}`, `/completed`, `/absentees`
- `StatisticsController` (`/api/statistics`): `/user`, `/user-stats`, `/user-stats/msg`
- `SendHistoryController` (`/api/history`): `/send`(발송내역), `/optout`(차단) 등 + 예약발송
- 이벤트 발송(`/admin/event/sent`) 대응 컨트롤러

## 7. 에러 처리 / 보안

- 기록 실패는 `AuditEventService` 패턴과 동일하게 fire-and-forget (warn 로그만, 요청 흐름 비차단).
- `SEARCH_CONDITION`은 검색어 자체가 PII(이름/전화)일 수 있으나 요구사항상 원문 저장.
  컬럼 길이 VARCHAR(1000), 초과 시 절단.

## 8. 마이그레이션 (운영 수동 실행)

```sql
ALTER TABLE ACTION_LOG ADD COLUMN SEARCH_CONDITION VARCHAR(1000) NULL AFTER ACTION_REASON;
```

> 운영 DB에 수동 실행 필요. 배포 가이드에 명시.

## 9. 테스트

- 인터셉터 단위 테스트: 어노테이션 있는/없는 핸들러, 검색조건·Referer 캡처, 기록 실패 시 요청 비차단.
- 검토화면 조회에 `searchCondition` 노출 확인.
