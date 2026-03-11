-- 개인정보 제3자 제공 동의 분리를 위한 컬럼 추가
-- SURVEY_MASTER 테이블에 제3자 제공 동의 관련 컬럼 3개 추가

ALTER TABLE survey_master
    ADD COLUMN THIRD_PARTY_YN char(1) DEFAULT 'N' COMMENT '개인정보 제3자 제공 동의 사용여부' AFTER PRIVACY_POLICY_DESC,
    ADD COLUMN THIRD_PARTY_TTL varchar(50) DEFAULT NULL COMMENT '개인정보 제3자 제공 동의 타이틀' AFTER THIRD_PARTY_YN,
    ADD COLUMN THIRD_PARTY_DESC text DEFAULT NULL COMMENT '개인정보 제3자 제공 동의 내용' AFTER THIRD_PARTY_TTL;
