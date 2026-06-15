-- =================================================================
-- WiseAd 보안/컴플라이언스 PR2 Rollback
-- =================================================================
-- 적용 전 20260521_02_prohibited_word.sql 이 적용된 상태에서만 실행

DROP TABLE IF EXISTS profanity_block_log;
DROP TABLE IF EXISTS prohibited_word_history;
DROP TABLE IF EXISTS prohibited_word;
