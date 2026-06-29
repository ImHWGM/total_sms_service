-- 통합 테스트용 verification 테이블 DDL (H2 in-memory)
DROP TABLE IF EXISTS verification;
CREATE TABLE verification (
    seq          INT          NOT NULL AUTO_INCREMENT,
    purpose      VARCHAR(20)  NOT NULL,
    channel      VARCHAR(10)  NOT NULL,
    identifier   VARCHAR(255) NOT NULL,
    target       VARCHAR(255) NULL,
    code         VARCHAR(10)  NULL,
    attempts     INT          NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL,
    verified_at  DATETIME     NULL,
    PRIMARY KEY (seq),
    UNIQUE (purpose, channel, identifier)
);
