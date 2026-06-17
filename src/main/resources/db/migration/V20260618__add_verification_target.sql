-- 로그인 2FA(SmsAuthService / EmailAuthService) in-memory → DB 이전을 위한 스키마 추가.
-- SMS 마이페이지 등록(verifyCodeAndGetPhone) 흐름에서 발송 대상 전화번호를 보관할 컬럼이 필요하다.
-- ⚠ 멱등: ADD COLUMN IF NOT EXISTS (MariaDB 10.1.4+)
ALTER TABLE verification
    ADD COLUMN IF NOT EXISTS target VARCHAR(255) NULL
        COMMENT 'SMS 발송 대상 (마이페이지 SMS 등록 시 전화번호 보관; nullable)'
        AFTER identifier;
