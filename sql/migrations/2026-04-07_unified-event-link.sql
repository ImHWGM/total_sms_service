-- 행사 통합 링크: 사전설문 기간 컬럼 추가
-- 적용 순서: 본 DDL → BE 배포 → FE 배포
-- 재시작 불필요. 두 mapper 모두 명시 컬럼 사용 → 구버전 코드는 신규 컬럼을 안전하게 무시.

ALTER TABLE SURVEY_MASTER
  ADD COLUMN PRE_SURVEY_START_DATE DATETIME NULL COMMENT '사전설문 시작일',
  ADD COLUMN PRE_SURVEY_END_DATE   DATETIME NULL COMMENT '사전설문 종료일';

-- Rollback:
-- ALTER TABLE SURVEY_MASTER
--   DROP COLUMN PRE_SURVEY_START_DATE,
--   DROP COLUMN PRE_SURVEY_END_DATE;
