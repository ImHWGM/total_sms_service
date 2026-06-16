-- 회원가입 SMS 본인인증 상태 저장 테이블 (M3: in-memory → DB 이전)
-- 발송 코드/시도횟수/인증완료 도장을 인스턴스 메모리 대신 DB 에 보관해
-- 다중 인스턴스·재시작에도 인증 상태가 보존되도록 한다.
-- 참고: 자동 실행 도구(Flyway/Liquibase) 미사용. 파일은 보관용이며 DBA 가 수동 실행한다.

CREATE TABLE signup_sms_verification (
    seq          INT          NOT NULL AUTO_INCREMENT COMMENT 'PK (대리키)',
    purpose      VARCHAR(20)  NOT NULL COMMENT 'OTP 용도 (예: SIGNUP)',
    phone        VARCHAR(20)  NOT NULL COMMENT '정규화 휴대폰번호(숫자만)',
    code         VARCHAR(6)   NULL     COMMENT '6자리 인증코드(검증 성공/만료 시 NULL)',
    attempts     INT          NOT NULL DEFAULT 0 COMMENT '검증 시도 횟수',
    created_at   DATETIME     NOT NULL COMMENT '코드 발송 시각(만료 5분/재발송 60초 기준)',
    verified_at  DATETIME     NULL     COMMENT '인증 완료 시각(=도장, NULL 이면 미인증; 가입 유예 30분)',
    PRIMARY KEY (seq),
    UNIQUE KEY uk_purpose_phone (purpose, phone)
) COMMENT='회원가입 SMS 본인인증 상태';
