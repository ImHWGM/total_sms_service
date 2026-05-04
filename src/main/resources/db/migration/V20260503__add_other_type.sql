-- 기타옵션 다중 답변 유형 컬럼 추가 (plan §3 Phase A, AC-13)
-- SA(주관식, 기본값) / NE(이름) / SO(주민번호) / EM(이메일) / AD(주소) / CU(커스텀)

-- 1. SURVEY_ITEM.OTHER_TYPE 컬럼 추가 (NOT NULL DEFAULT 'SA')
--    MariaDB 10.3.2+ 에서 ALGORITHM=INSTANT 지원, 미지원 환경은 INPLACE 자동 fallback
ALTER TABLE SURVEY_ITEM
    ADD COLUMN OTHER_TYPE VARCHAR(2) NOT NULL DEFAULT 'SA'
    AFTER OTHER_PLACEHOLDER,
    ALGORITHM=INSTANT, LOCK=NONE;

-- 2. 기존 모든 레코드 'SA'로 백필 (DEFAULT가 적용되지 않은 잠재 케이스 대비, idempotent)
UPDATE SURVEY_ITEM SET OTHER_TYPE = 'SA' WHERE OTHER_TYPE IS NULL OR OTHER_TYPE = '';

-- 3. CHECK 제약 (MariaDB 10.2+; 6종 enum 외 거부)
ALTER TABLE SURVEY_ITEM
    ADD CONSTRAINT CK_SURVEY_ITEM_OTHER_TYPE
    CHECK (OTHER_TYPE IN ('SA','NE','SO','EM','AD','CU'));
