-- SMS 2FA 추가: login_phone 컬럼 (AES256+Base64 암호화, UNIQUE) + default_two_factor_method enum
-- 참고: 자동 실행 도구(Flyway/Liquibase) 미사용. 파일은 보관용이며 DBA가 수동 실행한다.
-- 기존 사용자는 DEFAULT 'EMAIL'로 백필됨 (별도 백필 쿼리 불필요)

ALTER TABLE user ADD COLUMN login_phone VARCHAR(255) NULL COMMENT '로그인 2FA용 휴대폰 번호(암호화)';
ALTER TABLE user ADD COLUMN default_two_factor_method VARCHAR(10) NOT NULL DEFAULT 'EMAIL' COMMENT 'EMAIL or SMS';
ALTER TABLE user ADD CONSTRAINT uk_user_login_phone UNIQUE (login_phone);
r