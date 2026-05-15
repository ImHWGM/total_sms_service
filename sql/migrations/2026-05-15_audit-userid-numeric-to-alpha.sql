-- audit 컬럼 numeric(user.seq 문자열) → alpha(user.user_id) 일괄 마이그레이션
--
-- 운영자 수동 실행용 SQL — Flyway 자동 마이그레이션 아님.
-- 동반 가이드: 2026-05-15_audit-userid-numeric-to-alpha.md (배포 순서/롤백/검증)
--
-- 배경: PR #40 (audit user_id 통일) 이전 일부 코드가 JWT subject seq 문자열을
-- audit 컬럼에 저장하면서 기존 alpha 데이터와 혼재. 상용 DB 조사 결과 표준은
-- user_id(alpha) 임을 확인. PR #40 으로 신규 row 는 alpha 보장. 본 마이그레이션은
-- 기존 numeric row 를 user_id 로 정규화.
--
-- 실행 순서: PR #40 머지/배포 → 본 SQL 수동 실행 → 사후 분포 검증
--
-- 안전 장치:
--   1) WHERE 절 REGEXP '^[0-9]+$' — 이미 alpha 인 row 는 안 건드림 (idempotent)
--   2) JOIN user u ON u.SEQ = CAST(...) — 매칭 안 되는 orphan row 자동 스킵
--   3) NULL row 는 변경 안 함 (transaction.reg_id NULL 5572 row 는 별도 검토)
--
-- 사전 검증 (참고용):
--   SELECT 'sms_send' tbl, COUNT(*) numeric_cnt
--     FROM sms_send WHERE REG_ID REGEXP '^[0-9]+$';
--   (동일 패턴으로 다른 테이블 확인)
--
--   dev 검증: sms_send numeric=101, orphan=0 (2026-05-15)
--   상용 예상: sms_send numeric=4180, orphan=0
--
-- orphan 식별 (변환 대상 외 잔존 numeric — JOIN 으로 자동 스킵되는 row):
--   SELECT REG_ID FROM sms_send
--    WHERE REG_ID REGEXP '^[0-9]+$'
--      AND NOT EXISTS (SELECT 1 FROM `user` u WHERE u.SEQ = CAST(sms_send.REG_ID AS UNSIGNED));

-- 1. sms_send.REG_ID (numeric ~4180 row 예상)
UPDATE sms_send s
JOIN `user` u ON u.SEQ = CAST(s.REG_ID AS UNSIGNED)
   SET s.REG_ID = u.USER_ID
 WHERE s.REG_ID REGEXP '^[0-9]+$';

-- 2. survey_user.REG_ID (numeric ~1919 row 예상)
UPDATE survey_user su
JOIN `user` u ON u.SEQ = CAST(su.REG_ID AS UNSIGNED)
   SET su.REG_ID = u.USER_ID
 WHERE su.REG_ID REGEXP '^[0-9]+$';

-- 3. survey_user.UPT_ID (numeric ~53 row 예상)
UPDATE survey_user su
JOIN `user` u ON u.SEQ = CAST(su.UPT_ID AS UNSIGNED)
   SET su.UPT_ID = u.USER_ID
 WHERE su.UPT_ID REGEXP '^[0-9]+$';

-- 4. auth_user_mapping.REG_ID (numeric ~1516 row 예상)
UPDATE auth_user_mapping a
JOIN `user` u ON u.SEQ = CAST(a.REG_ID AS UNSIGNED)
   SET a.REG_ID = u.USER_ID
 WHERE a.REG_ID REGEXP '^[0-9]+$';

-- 5. user.UPT_ID (numeric ~6 row 예상, 자기참조)
UPDATE `user` usr
JOIN `user` u ON u.SEQ = CAST(usr.UPT_ID AS UNSIGNED)
   SET usr.UPT_ID = u.USER_ID
 WHERE usr.UPT_ID REGEXP '^[0-9]+$';

-- 6. customer_company.UPT_ID (numeric ~2 row 예상)
UPDATE customer_company cc
JOIN `user` u ON u.SEQ = CAST(cc.UPT_ID AS UNSIGNED)
   SET cc.UPT_ID = u.USER_ID
 WHERE cc.UPT_ID REGEXP '^[0-9]+$';

-- 7. transaction.reg_id (numeric ~1 row 예상)
UPDATE `transaction` t
JOIN `user` u ON u.SEQ = CAST(t.reg_id AS UNSIGNED)
   SET t.reg_id = u.USER_ID
 WHERE t.reg_id REGEXP '^[0-9]+$';

-- 미처리:
--   * transaction.reg_id NULL row (~5572 건, 2026-02 ~ 2026-05): PR #41 (transaction
--     audit 회귀 복구) 이전 생성된 row. 원래 actor 정보 손실. 'UNKNOWN' sentinel
--     일괄 백필은 의미가 없으므로 NULL 유지. 필요 시 별도 보고서 SQL 작성.
--   * survey_master.REG_ID/UPT_ID NULL row: 의미적으로 시스템 등록 또는 누락.
--     본 마이그레이션 대상 외.
--   * orphan numeric (user.SEQ 매칭 안 됨): JOIN 으로 자동 스킵. 별도 추적 필요.
