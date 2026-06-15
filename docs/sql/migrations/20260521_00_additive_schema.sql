-- =================================================================
-- WiseAd 보안/컴플라이언스 PR0: Additive-only schema migration
-- =================================================================
-- 적용 대상: 운영 DB (사용자 수동 실행)
-- 안전성: ADD COLUMN / CREATE TABLE only, 기존 데이터 무변경
-- 의존 컬럼/테이블: action_log (보존만), user, email_unsubscribe, kg_payment_info ... (영향 없음)
--
-- 주의:
--   1. 적용 전 preflight/20260521_00_preflight.sql 실행하여 사전 상태 검증
--   2. 적용 후 postflight/20260521_00_postflight.sql 실행하여 컬럼/테이블 추가 확인
--   3. 실패 시 20260521_00_rollback.sql 실행
--   4. EmailAuthService 의 OTP purpose 는 in-memory 필드로 처리되므로 본 SQL 에 포함되지 않음
-- =================================================================

-- -----------------------------------------------------------------
-- 1) audit_event 테이블 신설 (action_log 와 분리)
--    이유: action_log.ACTION_TYPE 은 char(1) (R/C/U/D) 이라
--    SIGNUP/WITHDRAW/UNLOCK_AUTO 등 새 의미를 같은 컬럼에 담을 수 없음.
-- -----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `audit_event` (
    `SEQ`           bigint(20)    NOT NULL AUTO_INCREMENT,
    `EVENT_TYPE`    varchar(30)   NOT NULL  COMMENT 'SIGNUP/WITHDRAW/UNLOCK_AUTO/UNLOCK_OTP/UNLOCK_ADMIN/DORMANT/RECOVERY/WITHDRAWN_BATCH',
    `USER_ID`       int(10) unsigned DEFAULT NULL COMMENT '대상 사용자 seq',
    `ACTOR_ID`      int(10) unsigned DEFAULT NULL COMMENT '행위자 seq (NULL = SYSTEM)',
    `ACTOR_TYPE`    varchar(20)   NOT NULL  COMMENT 'USER/ADMIN/SYSTEM',
    `IP`            varchar(45)   DEFAULT NULL,
    `USER_AGENT`    varchar(255)  DEFAULT NULL,
    `REASON`        text          DEFAULT NULL,
    `PREV_STATUS`   varchar(20)   DEFAULT NULL,
    `NEW_STATUS`    varchar(20)   DEFAULT NULL,
    `METADATA`      json          DEFAULT NULL  COMMENT 'JSON 자유 필드 (예: signup_method, withdraw_method)',
    `CREATED_AT`    timestamp     NOT NULL DEFAULT current_timestamp(),
    PRIMARY KEY (`SEQ`),
    KEY `idx_audit_event_type_user` (`EVENT_TYPE`, `USER_ID`),
    KEY `idx_audit_event_created` (`CREATED_AT`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci
  COMMENT='계정 생명주기/잠금/휴면 등 컴플라이언스 감사 이벤트 (12개월+ 보존)';

-- -----------------------------------------------------------------
-- 2) user 테이블 컬럼 추가 (additive only)
--    기존 STATUS varchar(10) 컬럼은 절대 손대지 않는다.
--    신규 lifecycle_status 컬럼이 새 enum 의미를 담는다.
-- -----------------------------------------------------------------
ALTER TABLE `user`
    ADD COLUMN `LOCKED_UNTIL`         timestamp    NULL DEFAULT NULL COMMENT '로그인 잠금 해제 예정 시각 (NULL=미잠금)',
    ADD COLUMN `LIFECYCLE_STATUS`     varchar(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '계정 생명주기 상태: ACTIVE/PENDING_APPROVAL/DORMANT/WITHDRAWN',
    ADD COLUMN `DORMANT_AT`           timestamp    NULL DEFAULT NULL COMMENT '휴면 전환 시각',
    ADD COLUMN `DORMANT_NOTIFIED_AT`  timestamp    NULL DEFAULT NULL COMMENT '휴면 사전 알림 발송 시각 (1회 발송 보장)',
    ADD COLUMN `WITHDRAWN_AT`         timestamp    NULL DEFAULT NULL COMMENT '탈퇴 전환 시각',
    ADD COLUMN `ANONYMIZED_AT`        timestamp    NULL DEFAULT NULL COMMENT 'PIPA 익명화 처리 시각 (NULL=미익명화; idempotent guard)';

ALTER TABLE `user`
    ADD INDEX `idx_user_lifecycle_status_last_login` (`LIFECYCLE_STATUS`, `LAST_LOGIN`);
