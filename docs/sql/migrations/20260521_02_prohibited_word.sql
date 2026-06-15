-- =================================================================
-- WiseAd 보안/컴플라이언스 PR2: 금칙어 테이블 생성
-- =================================================================
-- 적용 대상: 운영 DB (사용자 수동 실행)
-- 의존성: PR0 (audit_event 테이블) 적용 완료 후 실행
-- Charset: utf8mb3 / utf8mb3_general_ci (기존 스키마 일치)
-- Rollback: 20260521_02_rollback.sql

-- -----------------------------------------------------------------
-- 1. 금칙어 마스터 테이블
-- -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS prohibited_word (
    SEQ          BIGINT AUTO_INCREMENT PRIMARY KEY,
    WORD         VARCHAR(200) NOT NULL UNIQUE                          COMMENT '금칙어',
    CATEGORY     VARCHAR(50)  NULL                                     COMMENT '분류 (성인/도박/금융 등)',
    ACTIVE       BOOLEAN      NOT NULL DEFAULT TRUE                    COMMENT '활성 여부',
    REASON       TEXT         NULL                                     COMMENT '등록 사유',
    CREATED_AT   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP       COMMENT '등록 일시',
    UPDATED_AT   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
                              ON UPDATE CURRENT_TIMESTAMP              COMMENT '수정 일시',
    VERSION      BIGINT       NOT NULL DEFAULT 0                       COMMENT '캐시 폴링용 버전 스탬프'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci
  COMMENT='금칙어 마스터';

-- -----------------------------------------------------------------
-- 2. 금칙어 변경 이력 테이블
-- -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS prohibited_word_history (
    SEQ         BIGINT AUTO_INCREMENT PRIMARY KEY,
    WORD_ID     BIGINT       NOT NULL                                  COMMENT 'prohibited_word.SEQ',
    ACTION      VARCHAR(10)  NOT NULL                                  COMMENT '변경 유형: ADD / MODIFY / DELETE',
    PREV_VALUE  JSON         NULL                                      COMMENT '변경 전 금칙어 JSON',
    NEW_VALUE   JSON         NULL                                      COMMENT '변경 후 금칙어 JSON',
    ACTOR_ID    INT UNSIGNED NOT NULL                                  COMMENT '처리 관리자 user.SEQ',
    REASON      TEXT         NOT NULL                                  COMMENT '변경 사유',
    CREATED_AT  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP        COMMENT '기록 일시',
    INDEX idx_pw_hist_word_created (WORD_ID, CREATED_AT),
    INDEX idx_pw_hist_created      (CREATED_AT)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci
  COMMENT='금칙어 변경 이력';

-- -----------------------------------------------------------------
-- 3. 금칙어 차단 로그 테이블 (12개월 보존 대상)
-- -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS profanity_block_log (
    SEQ              BIGINT AUTO_INCREMENT PRIMARY KEY,
    USER_ID          INT UNSIGNED NOT NULL                             COMMENT '발송 요청 user.SEQ',
    MATCHED_WORD     VARCHAR(200) NOT NULL                             COMMENT '매칭된 금칙어',
    CONTENT_SNIPPET  VARCHAR(500) NULL                                 COMMENT '메시지 내용 앞 500자',
    FULL_CONTENT     TEXT         NULL                                 COMMENT '원본 전체 내용 (archive 후 삭제)',
    SOURCE           VARCHAR(20)  NOT NULL                             COMMENT '발생 경로: DRAFT / SEND / SCHEDULE',
    MESSAGE_ID       BIGINT       NULL                                 COMMENT '관련 메시지 ID',
    RECIPIENT_COUNT  INT          NULL                                 COMMENT '발송 대상 수',
    IP               VARCHAR(45)  NULL                                 COMMENT '요청 IP (IPv4/IPv6)',
    USER_AGENT       VARCHAR(255) NULL                                 COMMENT '요청 User-Agent',
    CREATED_AT       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '차단 일시',
    INDEX idx_pbl_user_created (USER_ID, CREATED_AT),
    INDEX idx_pbl_created      (CREATED_AT)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci
  COMMENT='금칙어 차단 로그 (12개월 보존)';

-- -----------------------------------------------------------------
-- 4. 시드 데이터 (기본 금칙어)
-- -----------------------------------------------------------------
INSERT IGNORE INTO prohibited_word (WORD, CATEGORY, ACTIVE, REASON) VALUES
    ('시발',   '욕설',   TRUE, '기본 금칙어'),
    ('성적언어', '성인',   TRUE, '기본 금칙어'),
    ('새끼',   '욕설',   TRUE, '기본 금칙어'),
    ('대출',   '금융',   TRUE, '기본 금칙어'),
    ('카지노',  '도박',   TRUE, '기본 금칙어'),
    ('경마',   '도박',   TRUE, '기본 금칙어'),
    ('출장',   '성인',   TRUE, '기본 금칙어'),
    ('안마',   '성인',   TRUE, '기본 금칙어'),
    ('성인용품', '성인',   TRUE, '기본 금칙어');
