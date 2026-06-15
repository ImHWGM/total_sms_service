-- =================================================================
-- WiseAd 보안/컴플라이언스 PR1: Data Migration
-- =================================================================
-- 적용 대상: 운영 DB (사용자 수동 실행)
-- 의존: PR0 (20260521_00_additive_schema.sql) 적용 완료 필수
--
-- 작업 내용:
--   1) user.STATUS 한글 값 → LIFECYCLE_STATUS 영문 enum 매핑
--   2) 기존 로그인 실패 5회 이상 사용자 → locked_until 설정 (신규 정책 이전)
--
-- 주의:
--   1. PR0 post-flight 통과 확인 후 실행
--   2. 실패 시 20260521_01_rollback.sql 실행
--   3. AC35: in-memory EmailAuthService purpose 는 SQL backfill 불필요
--      (email_auth DB 테이블 없음; PR0 주석 참고)
-- =================================================================

-- -----------------------------------------------------------------
-- 1) user.STATUS 한글 → LIFECYCLE_STATUS 영문 매핑
--    '승인'    → ACTIVE
--    '미승인'  → PENDING_APPROVAL
--    '탈퇴'    → WITHDRAWN
--    그 외     → ACTIVE (안전 기본값; 운영에서 '승인' 아닌 값 거의 없음)
-- -----------------------------------------------------------------
UPDATE `user`
SET `LIFECYCLE_STATUS` = CASE `STATUS`
    WHEN '승인'   THEN 'ACTIVE'
    WHEN '미승인' THEN 'PENDING_APPROVAL'
    WHEN '탈퇴'   THEN 'WITHDRAWN'
    ELSE 'ACTIVE'
END;

-- -----------------------------------------------------------------
-- 2) 기존 5회 이상 실패 잠금 사용자 → locked_until 신규 정책 이전
--    ACTIVE 상태 사용자에게만 적용 (이미 탈퇴/미승인은 제외)
--    UTC_TIMESTAMP() 사용 (DB session tz 비의존 — AC37 준수)
-- -----------------------------------------------------------------
UPDATE `user`
SET `LOCKED_UNTIL` = UTC_TIMESTAMP() + INTERVAL 10 MINUTE
WHERE `LOGIN_FAILURE_CNT` >= 5
  AND `LIFECYCLE_STATUS` = 'ACTIVE'
  AND `LOCKED_UNTIL` IS NULL;
