-- 사업자등록번호(BIZ_NUM) 하이픈 제거 정규화.
-- 과거 회원가입 검증이 하이픈 형식("215-87-19169")을 강제했고, 이후 무하이픈("2158719169") 도
-- 유입되어 DB 에 두 형식이 혼재한다. 저장 표준을 "무하이픈" 으로 통일한다.
--   - 신규/수정 경로: UserMapper insert/update 에서 REPLACE(#{bizNum}, '-', '') 로 저장.
--   - 기존 데이터: 아래 UPDATE 로 일괄 정규화.
-- 참고: 자동 실행 도구(Flyway/Liquibase) 미사용. 파일은 보관용이며 DBA 가 수동 실행한다.
--
-- ⚠ 멱등(idempotent): 몇 번을 돌려도 동일 결과 (하이픈 없는 행은 매칭되지 않아 변경 없음).
UPDATE user
SET BIZ_NUM = REPLACE(BIZ_NUM, '-', '')
WHERE BIZ_NUM LIKE '%-%';
