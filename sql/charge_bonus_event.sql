-- ============================================
-- 충전 보너스 이벤트 테이블
-- ============================================

CREATE TABLE IF NOT EXISTS charge_bonus_event (
    event_seq BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '이벤트 순번',
    event_name VARCHAR(100) NOT NULL COMMENT '이벤트명',
    event_type VARCHAR(20) NOT NULL COMMENT '이벤트 유형 (PERCENTAGE: 비율, FIXED: 고정금액)',
    bonus_rate DECIMAL(5, 4) DEFAULT NULL COMMENT '보너스 비율 (0.10 = 10%)',
    bonus_amount DECIMAL(15, 2) DEFAULT NULL COMMENT '고정 보너스 금액 (FIXED 타입용)',
    min_charge_amount DECIMAL(15, 2) DEFAULT NULL COMMENT '최소 충전 금액 (이상 충전 시 적용)',
    max_bonus_amount DECIMAL(15, 2) DEFAULT NULL COMMENT '최대 보너스 한도',
    start_date DATE DEFAULT NULL COMMENT '이벤트 시작일',
    end_date DATE DEFAULT NULL COMMENT '이벤트 종료일',
    bonus_expire_days INT DEFAULT 90 COMMENT '보너스 포인트 유효기간 (일)',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '상태 (ACTIVE, INACTIVE)',
    created_by VARCHAR(50) NOT NULL COMMENT '생성자',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성일시',
    updated_at DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시',

    INDEX idx_status_date (status, start_date, end_date),
    INDEX idx_min_charge (min_charge_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='충전 보너스 이벤트';

-- ============================================
-- 샘플 이벤트 데이터 (테스트용)
-- ============================================

-- 예시 1: 5만원 이상 충전 시 10% 포인트 추가 적립
INSERT INTO charge_bonus_event (
    event_name, event_type, bonus_rate, min_charge_amount, max_bonus_amount,
    start_date, end_date, bonus_expire_days, status, created_by
) VALUES (
    '5만원 이상 충전 시 10% 포인트 적립',
    'PERCENTAGE',
    0.10,           -- 10%
    50000,          -- 최소 5만원
    50000,          -- 최대 5만 포인트
    CURDATE(),      -- 오늘부터
    DATE_ADD(CURDATE(), INTERVAL 30 DAY),  -- 30일 후까지
    90,             -- 포인트 유효기간 90일
    'ACTIVE',
    'system'
);

-- 예시 2: 10만원 이상 충전 시 15% 포인트 추가 적립
INSERT INTO charge_bonus_event (
    event_name, event_type, bonus_rate, min_charge_amount, max_bonus_amount,
    start_date, end_date, bonus_expire_days, status, created_by
) VALUES (
    '10만원 이상 충전 시 15% 포인트 적립',
    'PERCENTAGE',
    0.15,           -- 15%
    100000,         -- 최소 10만원
    100000,         -- 최대 10만 포인트
    CURDATE(),
    DATE_ADD(CURDATE(), INTERVAL 30 DAY),
    90,
    'ACTIVE',
    'system'
);

-- 예시 3: 첫 충전 5000 포인트 고정 적립 (비활성)
INSERT INTO charge_bonus_event (
    event_name, event_type, bonus_amount, min_charge_amount,
    start_date, end_date, bonus_expire_days, status, created_by
) VALUES (
    '첫 충전 5000 포인트 지급',
    'FIXED',
    5000,           -- 고정 5000 포인트
    10000,          -- 최소 1만원 이상 충전 시
    CURDATE(),
    DATE_ADD(CURDATE(), INTERVAL 90 DAY),
    30,             -- 포인트 유효기간 30일
    'INACTIVE',     -- 비활성 상태
    'system'
);
