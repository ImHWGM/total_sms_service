-- =================================================================
-- PR0 Postflight: forward 적용 후 검증
-- =================================================================
-- 기대 결과:
--   - audit_event 테이블 1 row
--   - user 신규 6 컬럼 모두 6 rows
--   - idx_user_lifecycle_status_last_login 인덱스 1 row
-- =================================================================

-- audit_event 테이블 확인
SELECT TABLE_NAME, ENGINE, TABLE_COLLATION
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'audit_event';

-- audit_event 컬럼 12개 확인
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'audit_event'
ORDER BY ORDINAL_POSITION;

-- user 신규 컬럼 6개 확인
SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user'
  AND COLUMN_NAME IN ('LOCKED_UNTIL', 'LIFECYCLE_STATUS', 'DORMANT_AT', 'DORMANT_NOTIFIED_AT', 'WITHDRAWN_AT', 'ANONYMIZED_AT')
ORDER BY ORDINAL_POSITION;

-- 인덱스 확인
SELECT INDEX_NAME, COLUMN_NAME, SEQ_IN_INDEX
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'user'
  AND INDEX_NAME = 'idx_user_lifecycle_status_last_login'
ORDER BY SEQ_IN_INDEX;

-- audit_event 인덱스 2개 확인
SELECT INDEX_NAME, COLUMN_NAME, SEQ_IN_INDEX
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'audit_event'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;
