-- 사전 인증(로그인 전) 휴대폰/이메일 인증 상태 저장 테이블 (M3: in-memory → DB 이전, 채널 통합)
-- 회원가입/아이디찾기/비밀번호찾기 등 용도(purpose) + 채널(SMS/EMAIL)별로 공용 사용한다.
-- 발송 코드/시도횟수/인증완료 도장을 인스턴스 메모리 대신 DB 에 보관해
-- 다중 인스턴스·재시작에도 인증 상태가 보존되도록 한다.
-- 참고: 자동 실행 도구(Flyway/Liquibase) 미사용. 파일은 보관용이며 DBA 가 수동 실행한다.
--
-- ⚠ 멱등(idempotent): 어느 서버에서 몇 번을 돌려도 동일 결과가 되도록 작성.
--   - 상용: 아무 테이블도 없음 → verification 만 생성.
--   - 개발: 과거 단계에서 만든 sms_verification 가 남아 있을 수 있음 → 제거.
--     (인증 상태는 전이성 데이터(최대 30분)라 데이터 이관 불필요 — 그냥 버린다.)
DROP TABLE IF EXISTS sms_verification;

CREATE TABLE IF NOT EXISTS verification (
    seq          INT          NOT NULL AUTO_INCREMENT COMMENT 'PK (대리키)',
    purpose      VARCHAR(20)  NOT NULL COMMENT '용도 (예: SIGNUP)',
    channel      VARCHAR(10)  NOT NULL COMMENT '채널 (SMS | EMAIL)',
    identifier   VARCHAR(255) NOT NULL COMMENT '대상 식별자 (정규화 휴대폰번호 또는 이메일; RFC 이메일 최대 254자 수용)',
    code         VARCHAR(10)  NULL     COMMENT '인증코드(SMS 6자리/이메일 8자, 검증 성공·만료 시 NULL)',
    attempts     INT          NOT NULL DEFAULT 0 COMMENT '검증 시도 횟수',
    created_at   DATETIME     NOT NULL COMMENT '코드 발송 시각(만료 5분/재발송 60초 기준)',
    verified_at  DATETIME     NULL     COMMENT '인증 완료 시각(=도장, NULL 이면 미인증; SMS 가입 유예 30분)',
    PRIMARY KEY (seq),
    UNIQUE KEY uk_purpose_channel_identifier (purpose, channel, identifier)
) COMMENT='사전 인증 휴대폰/이메일 인증 상태(용도·채널별)';
