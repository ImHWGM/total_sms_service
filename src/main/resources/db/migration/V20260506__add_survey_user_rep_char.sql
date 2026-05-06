-- 설문 참여자별 치환문자 값 저장 테이블 추가
-- (plan: wisead-survey-question-replacement-2026-05-06 §3 Phase A)
--
-- 용도: 설문 발송 시점에 엑셀 E~I열로 입력된 #설문대치1~5# 토큰의 수신자별 값을 영속화하여,
--       수신자가 SMS 링크(userKey 진입)로 설문에 접속할 때 문항 텍스트의 토큰을 치환하는 데 사용한다.
--
-- 정책:
-- - First Write Wins: 재발송 시에는 기존 row를 덮어쓰지 않는다 (현재 재발송 UI에 입력란 부재).
-- - 빈 값(null/blank)은 row를 생성하지 않는다 → 조회 시 누락 = 빈 문자열 치환 (SMS 동작과 동일).
-- - QR/eventCode 진입자는 SurveyUser는 있으나 본 테이블에 row 없음 → 모든 토큰 빈 문자열 치환.

-- USER_SEQ는 SURVEY_USER.SEQ(int(10) unsigned)와 정확히 일치해야 FK 생성됨 (errno 150 회피).
-- ENGINE/CHARSET/COLLATE는 SURVEY_USER와 동일하게 명시 (InnoDB / utf8mb4 / utf8mb4_general_ci).
CREATE TABLE SURVEY_USER_REP_CHAR (
    USER_SEQ      INT(10) UNSIGNED NOT NULL                  COMMENT '설문 참여자 시퀀스 (SURVEY_USER.SEQ FK)',
    REP_CHAR_IDX  SMALLINT         NOT NULL                  COMMENT '치환 슬롯 인덱스 (1~5, 추후 확장 가능)',
    REP_CHAR_VAL  VARCHAR(500)     NOT NULL                  COMMENT '치환 값 (#설문대치N# 토큰을 대체할 문자열)',
    REG_DATE      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록일',
    PRIMARY KEY (USER_SEQ, REP_CHAR_IDX),
    CONSTRAINT FK_SURVEY_USER_REP_CHAR_USER
        FOREIGN KEY (USER_SEQ) REFERENCES SURVEY_USER(SEQ) ON DELETE CASCADE,
    CONSTRAINT CK_SURVEY_USER_REP_CHAR_IDX
        CHECK (REP_CHAR_IDX BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
  COMMENT '설문 참여자별 치환문자 값 (#설문대치N# 토큰 치환용, first-write-wins)';
