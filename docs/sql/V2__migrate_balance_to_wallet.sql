-- ============================================
-- 결제/과금 시스템 리팩토링 - 마이그레이션 스크립트
-- 기존 BALANCE 테이블 → wallet 테이블로 이관
-- ============================================

-- 1. wallet 테이블 초기화 (기존 balance 최신값으로)
-- 주의: 기존에 POINT/BONUS가 없었으므로 CASH만 이관
INSERT INTO wallet (user_id, currency_type, balance)
SELECT
    USER_ID,
    'CASH',
    TOTAL_BALANCE
FROM (
    SELECT
        USER_ID,
        TOTAL_BALANCE,
        ROW_NUMBER() OVER (PARTITION BY USER_ID ORDER BY SEQ DESC) as rn
    FROM BALANCE
) t
WHERE rn = 1
ON DUPLICATE KEY UPDATE balance = VALUES(balance);

-- 2. transaction 테이블로 기존 이력 마이그레이션 (선택적)
-- 주의: 데이터가 많을 경우 배치로 처리 필요
/*
INSERT INTO transaction (
    user_id,
    currency_type,
    tx_type,
    amount,
    balance_after,
    comment,
    reg_date
)
SELECT
    USER_ID,
    'CASH',
    CASE
        WHEN OPERATION = 'P' THEN 'CHARGE'
        WHEN OPERATION = 'M' THEN 'DEDUCT'
        WHEN OPERATION = 'R' THEN 'REFUND'
        ELSE 'ETC'
    END,
    ABS(BALANCE),
    TOTAL_BALANCE,
    COMMENT,
    REG_DATE
FROM BALANCE
ORDER BY SEQ;
*/

-- 3. 마이그레이션 검증 쿼리
-- 사용자별 잔액 일치 여부 확인
/*
SELECT
    b.USER_ID,
    b.TOTAL_BALANCE AS balance_table,
    w.balance AS wallet_table,
    CASE WHEN b.TOTAL_BALANCE = w.balance THEN 'OK' ELSE 'MISMATCH' END AS status
FROM (
    SELECT USER_ID, TOTAL_BALANCE
    FROM BALANCE
    WHERE (USER_ID, SEQ) IN (
        SELECT USER_ID, MAX(SEQ)
        FROM BALANCE
        GROUP BY USER_ID
    )
) b
LEFT JOIN wallet w ON b.USER_ID = w.user_id AND w.currency_type = 'CASH'
ORDER BY status DESC, USER_ID;
*/