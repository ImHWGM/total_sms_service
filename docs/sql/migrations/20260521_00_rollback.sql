-- =================================================================
-- WiseAd PR0 Rollback: Additive-only schema 되돌리기
-- =================================================================
-- 사용 시점:
--   - PR0 forward 적용 후 PR1 코드 배포 전이라면 안전하게 reverse 가능
--   - PR1 이상이 이미 컬럼을 참조 중이면 코드 롤백 후 실행
-- =================================================================

-- user 인덱스 및 컬럼 제거
ALTER TABLE `user` DROP INDEX `idx_user_lifecycle_status_last_login`;

ALTER TABLE `user`
    DROP COLUMN `LOCKED_UNTIL`,
    DROP COLUMN `LIFECYCLE_STATUS`,
    DROP COLUMN `DORMANT_AT`,
    DROP COLUMN `DORMANT_NOTIFIED_AT`,
    DROP COLUMN `WITHDRAWN_AT`,
    DROP COLUMN `ANONYMIZED_AT`;

-- audit_event 테이블 제거
DROP TABLE IF EXISTS `audit_event`;
