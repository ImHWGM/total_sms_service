-- 설문 PII 답변 암호화 저장에 따른 컬럼 확장.
-- 주관식 ANSWER(detail NE/AD/CU/EM/SO)와 기타 OTHER_TEXT 모두 PII는 암호화 저장한다.
--   NE(이름)/AD(주소)/CU(연락처): AES256+Base64(2중) + 'PII:' 접두사
--   EM(이메일): 로컬파트(아이디)만 암호화, "@도메인"은 평문 유지
--   SO(주민번호): 기존 RSA 키패드 흐름(무접두 AES256+Base64) 유지
-- 암호화 결과는 평문보다 길어 긴 주소 등에서 기존 VARCHAR(2000)을 초과할 수 있으므로 TEXT로 확장한다.
-- 참고: 자동 실행 도구(Flyway/Liquibase) 미사용. 파일은 보관용이며 DBA가 수동 실행한다.
--
-- ⚠ 멱등(idempotent): VARCHAR(2000) → TEXT 확장은 기존 데이터 보존이며 재실행해도 동일 결과.
ALTER TABLE SURVEY_ANSWER
    MODIFY COLUMN ANSWER     TEXT NULL COMMENT '답변(주관식/객관식, PII detail은 암호화 저장)',
    MODIFY COLUMN OTHER_TEXT TEXT NULL COMMENT '기타 항목 텍스트 (PII 유형은 암호화 저장)';
