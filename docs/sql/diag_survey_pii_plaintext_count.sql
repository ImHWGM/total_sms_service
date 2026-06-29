-- =============================================================================
-- [READ-ONLY 진단] 설문답변 PII 레거시 평문 행 집계  (HeidiSQL용)
-- =============================================================================
-- 사용법(HeidiSQL)
--   - 전체 실행: 이 파일을 쿼리 탭에 붙여넣고 F9 → 결과 그리드가 쿼리별 탭으로 여러 개 뜸.
--   - 개별 실행: 원하는 SELECT 문(세미콜론까지)만 드래그 선택 후 F9.
--   - 모든 쿼리는 독립 실행 가능(세션변수 미사용, 배포 컷오프 시각을 직접 박아둠).
--   - SELECT/COUNT 만 수행 → 데이터 변경 없음(완전 read-only).
--
-- ⚠ 배포 컷오프 시각: 아래 5곳의 '2026-06-26 19:00:00' 을 실제 배포시각으로 필요시 수정.
--
-- 판별 원리
--   NE/AD/CU/EM 암호화 저장값은 항상 'PII:' 접두 → 접두 없는 비어있지 않은 값 = 레거시 평문.
--   SO(주민번호)는 무접두 AES256+Base64 → 접두 판별 불가, 정규식 휴리스틱으로 분리 집계만.

-- -----------------------------------------------------------------------------
-- 1) ANSWER 컬럼 — 주관식 NE/AD/CU/EM 평문 건수 (유형별)
--    after_cutoff_LEAK 는 0 이어야 정상(신규 저장은 암호화됨)
-- -----------------------------------------------------------------------------
SELECT
    'ANSWER'                                                       AS col,
    QUESTION_TYPE_DETAIL                                           AS pii_type,
    COUNT(*)                                                       AS plaintext_rows,
    SUM(REG_DATE <  '2026-06-26 19:00:00')                         AS before_cutoff,
    SUM(REG_DATE >= '2026-06-26 19:00:00')                         AS after_cutoff_LEAK
FROM survey_answer
WHERE QUESTION_TYPE_DETAIL IN ('NE','AD','CU','EM')
  AND ANSWER IS NOT NULL AND ANSWER <> ''
  AND ANSWER NOT LIKE 'PII:%'
GROUP BY QUESTION_TYPE_DETAIL
ORDER BY QUESTION_TYPE_DETAIL;

-- -----------------------------------------------------------------------------
-- 2) OTHER_TEXT 컬럼 — 기타답변 NE/AD/CU/EM 평문 건수 (유형별)
--    PII 유형은 답변 row 가 아니라 해당 문항의 '기타' 항목(SURVEY_ITEM.OTHER_TYPE)으로 결정.
-- -----------------------------------------------------------------------------
SELECT
    'OTHER_TEXT'                                                   AS col,
    si.OTHER_TYPE                                                  AS pii_type,
    COUNT(*)                                                       AS plaintext_rows,
    SUM(sa.REG_DATE <  '2026-06-26 19:00:00')                      AS before_cutoff,
    SUM(sa.REG_DATE >= '2026-06-26 19:00:00')                      AS after_cutoff_LEAK
FROM survey_answer sa
JOIN survey_item si
      ON si.EVENT_SEQ = sa.EVENT_SEQ
     AND si.QUESTION_SEQ = sa.QUESTION_SEQ
     AND si.OTHER_YN = 'Y'
     AND si.OTHER_TYPE IN ('NE','AD','CU','EM')
WHERE sa.OTHER_TEXT IS NOT NULL AND sa.OTHER_TEXT <> ''
  AND sa.OTHER_TEXT NOT LIKE 'PII:%'
GROUP BY si.OTHER_TYPE
ORDER BY si.OTHER_TYPE;

-- -----------------------------------------------------------------------------
-- 3-a) ANSWER 의 SO(주민번호) — 휴리스틱 분류
--    PLAINTEXT_JUMIN  : 6자리(-)7자리 주민번호 패턴 = 확실한 평문
--    RSA_UNDECRYPTED  : 'RSA:' 접두 = 복호화 실패 원문 저장(점검 필요)
--    LIKELY_ENCRYPTED : 그 외(무접두 AES256+Base64) = 이미 암호문 추정
-- -----------------------------------------------------------------------------
SELECT
    'ANSWER.SO' AS col,
    CASE
      WHEN ANSWER REGEXP '^[0-9]{6}-?[0-9]{7}$' THEN 'PLAINTEXT_JUMIN'
      WHEN ANSWER LIKE 'RSA:%'                   THEN 'RSA_UNDECRYPTED'
      ELSE 'LIKELY_ENCRYPTED'
    END AS classify,
    COUNT(*) AS rows_cnt,
    SUM(REG_DATE < '2026-06-26 19:00:00') AS before_cutoff
FROM survey_answer
WHERE QUESTION_TYPE_DETAIL = 'SO'
  AND ANSWER IS NOT NULL AND ANSWER <> ''
GROUP BY classify
ORDER BY classify;

-- -----------------------------------------------------------------------------
-- 3-b) OTHER_TEXT 의 SO (기타항목 OTHER_TYPE='SO')
-- -----------------------------------------------------------------------------
SELECT
    'OTHER_TEXT.SO' AS col,
    CASE
      WHEN sa.OTHER_TEXT REGEXP '^[0-9]{6}-?[0-9]{7}$' THEN 'PLAINTEXT_JUMIN'
      WHEN sa.OTHER_TEXT LIKE 'RSA:%'                   THEN 'RSA_UNDECRYPTED'
      ELSE 'LIKELY_ENCRYPTED'
    END AS classify,
    COUNT(*) AS rows_cnt,
    SUM(sa.REG_DATE < '2026-06-26 19:00:00') AS before_cutoff
FROM survey_answer sa
JOIN survey_item si
      ON si.EVENT_SEQ = sa.EVENT_SEQ
     AND si.QUESTION_SEQ = sa.QUESTION_SEQ
     AND si.OTHER_YN = 'Y'
     AND si.OTHER_TYPE = 'SO'
WHERE sa.OTHER_TEXT IS NOT NULL AND sa.OTHER_TEXT <> ''
GROUP BY classify
ORDER BY classify;

-- -----------------------------------------------------------------------------
-- 4) 총괄 요약 (NE/AD/CU/EM 평문 총건수, ANSWER + OTHER_TEXT)
-- -----------------------------------------------------------------------------
SELECT
    (SELECT COUNT(*) FROM survey_answer
       WHERE QUESTION_TYPE_DETAIL IN ('NE','AD','CU','EM')
         AND ANSWER IS NOT NULL AND ANSWER <> '' AND ANSWER NOT LIKE 'PII:%')
  AS answer_plaintext_total,
    (SELECT COUNT(*) FROM survey_answer sa
       JOIN survey_item si
         ON si.EVENT_SEQ = sa.EVENT_SEQ AND si.QUESTION_SEQ = sa.QUESTION_SEQ
        AND si.OTHER_YN = 'Y' AND si.OTHER_TYPE IN ('NE','AD','CU','EM')
       WHERE sa.OTHER_TEXT IS NOT NULL AND sa.OTHER_TEXT <> '' AND sa.OTHER_TEXT NOT LIKE 'PII:%')
  AS othertext_plaintext_total;

-- -----------------------------------------------------------------------------
-- 5) (선택) 평문 표본 10건 — 값 노출 방지: 접두 4자/길이만 표시
-- -----------------------------------------------------------------------------
SELECT ANSWER_SEQ, EVENT_SEQ, QUESTION_SEQ, QUESTION_TYPE_DETAIL,
       LEFT(ANSWER, 4) AS answer_prefix, CHAR_LENGTH(ANSWER) AS answer_len, REG_DATE
FROM survey_answer
WHERE QUESTION_TYPE_DETAIL IN ('NE','AD','CU','EM')
  AND ANSWER IS NOT NULL AND ANSWER <> '' AND ANSWER NOT LIKE 'PII:%'
ORDER BY REG_DATE DESC
LIMIT 10;
