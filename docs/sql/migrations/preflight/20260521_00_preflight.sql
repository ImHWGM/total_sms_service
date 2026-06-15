-- =================================================================
-- PR0 Preflight: 적용 가능 상태 검증
-- =================================================================
-- 실행 결과:
--   - audit_event 가 NOT EXISTS 이어야 함 (0 rows)
--   - user 신규 컬럼들이 NOT EXISTS 이어야 함 (각 0 rows)
-- 1행이라도 반환되면 forward 중복 적용 위험이 있으니 중단할 것
-- =================================================================

-- audit_event 테이블 존재 여부
SELECT TABLE_NAME AS already_exists_table
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'audit_event';

-- user 신규 컬럼 존재 여부
SELECT COLUMN_NAME AS already_exists_column
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user'
  AND COLUMN_NAME IN ('LOCKED_UNTIL', 'LIFECYCLE_STATUS', 'DORMANT_AT', 'DORMANT_NOTIFIED_AT', 'WITHDRAWN_AT', 'ANONYMIZED_AT');

-- 기존 STATUS 값 분포 확인 (PR1 data migration 사전 데이터)
SELECT STATUS, COUNT(*) AS user_count
FROM `user`
GROUP BY STATUS;

-- 기존 5회+ 잠금 사용자 (PR1 backfill 대상 개수)
SELECT COUNT(*) AS locked_user_count
FROM `user`
WHERE LOGIN_FAILURE_CNT >= 5;
