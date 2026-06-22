-- 설문 "기타" 답변(OTHER_TEXT) PII 암호화 저장에 따른 컬럼 확장.
-- NE(이름)/AD(주소)/CU(연락처)는 AES256+Base64(2중) + 'PII:' 접두사로, EM(이메일)은 로컬파트만 암호화해
-- 저장한다(도메인 평문). 암호화 결과는 평문보다 길어, 검증 한도(AD 500자 등)를 채우면 기존 VARCHAR(2000)을
-- 초과할 수 있으므로 TEXT로 확장한다.
-- 참고: 자동 실행 도구(Flyway/Liquibase) 미사용. 파일은 보관용이며 DBA가 수동 실행한다.
--
-- ⚠ 멱등(idempotent): VARCHAR(2000) → TEXT 확장은 기존 데이터 보존이며 재실행해도 동일 결과.
ALTER TABLE SURVEY_ANSWER
    MODIFY COLUMN OTHER_TEXT TEXT NULL COMMENT '기타 항목 텍스트 (SO/NE/AD/CU/EM PII 유형은 암호화 저장)';
