-- 1. 현재 데이터 확인
SELECT EVENT_SEQ, EVENT_DESC_IMG, EVENT_END_IMG
FROM SURVEY_MASTER
WHERE EVENT_DESC_IMG IS NOT NULL OR EVENT_END_IMG IS NOT NULL;

-- 2. 물리 경로 형식 변환 (C:\upload\wisead_syscuss\survey\182\Desc.png →
/survey/182/Desc.png)
UPDATE SURVEY_MASTER
SET EVENT_DESC_IMG = CONCAT('/survey/',
                            SUBSTRING_INDEX(SUBSTRING_INDEX(EVENT_DESC_IMG, '\\survey\\', -1), '\\', 1), '/',
                            SUBSTRING_INDEX(EVENT_DESC_IMG, '\\', -1))
WHERE EVENT_DESC_IMG LIKE '%\\survey\\%';

UPDATE SURVEY_MASTER
SET EVENT_END_IMG = CONCAT('/survey/',
                           SUBSTRING_INDEX(SUBSTRING_INDEX(EVENT_END_IMG, '\\survey\\', -1), '\\', 1), '/',
                           SUBSTRING_INDEX(EVENT_END_IMG, '\\', -1))
WHERE EVENT_END_IMG LIKE '%\\survey\\%';

-- 3. 전체 URL 형식 변환 (https://twisead-api.epopkon.com/files/survey/181/Desc.png
→ /survey/181/Desc.png)
UPDATE SURVEY_MASTER
SET EVENT_DESC_IMG = CONCAT('/survey/', SUBSTRING_INDEX(EVENT_DESC_IMG, '/survey/',
                                                        -1))
WHERE EVENT_DESC_IMG LIKE 'http%/survey/%';

UPDATE SURVEY_MASTER
SET EVENT_END_IMG = CONCAT('/survey/', SUBSTRING_INDEX(EVENT_END_IMG, '/survey/',
                                                       -1))
WHERE EVENT_END_IMG LIKE 'http%/survey/%';

        문항/항목 이미지도 동일하게:
-- SURVEY_QUESTION 테이블
UPDATE SURVEY_QUESTION
SET QUESTION_IMG = CONCAT('/survey/', SUBSTRING_INDEX(QUESTION_IMG, '/survey/',
                                                      -1))
WHERE QUESTION_IMG LIKE 'http%/survey/%';

-- SURVEY_ITEM 테이블
UPDATE SURVEY_ITEM
SET ITEM_IMG = CONCAT('/survey/', SUBSTRING_INDEX(ITEM_IMG, '/survey/', -1))
WHERE ITEM_IMG LIKE 'http%/survey/%';