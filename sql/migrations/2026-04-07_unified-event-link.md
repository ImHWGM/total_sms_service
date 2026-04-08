# 2026-04-07 unified-event-link 마이그레이션

## 변경 요약
`SURVEY_MASTER`에 사전설문 기간 컬럼 2개 추가.

## 배포 순서 (엄수)
1. **DDL 적용** (본 SQL 파일) — 운영 DB에 직접 실행
2. **BE 배포** — 신규 컬럼을 read/write
3. **FE 배포** — 폼/통합 링크 페이지

검증 결과 `SurveyMasterMapper.xml`/`EventParticipantMapper.xml` 모두 `SELECT *` 미사용 → 구버전 BE는 신규 컬럼을 안전하게 무시. 따라서 DDL을 BE 배포 전에 적용해도 무중단 가능.

## 재시작 필요?
불필요. 컬럼 추가는 MySQL ALTER online schema change(가능 시).

## 롤백
```sql
ALTER TABLE SURVEY_MASTER
  DROP COLUMN PRE_SURVEY_START_DATE,
  DROP COLUMN PRE_SURVEY_END_DATE;
```
주의: 롤백 전 BE를 구버전으로 먼저 되돌릴 것 (신버전 BE는 두 컬럼 read/write).

## 검증 쿼리
```sql
SHOW COLUMNS FROM SURVEY_MASTER LIKE 'PRE_SURVEY_%';
```
