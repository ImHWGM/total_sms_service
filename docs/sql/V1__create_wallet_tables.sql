-- ============================================
-- 결제/과금 시스템 리팩토링 - 테이블 생성 스크립트
-- ============================================

-- 1. wallet 테이블 (CASH 전용)
CREATE TABLE IF NOT EXISTS `wallet` (
    `user_id` varchar(20) NOT NULL COMMENT '사용자 ID',
    `currency_type` varchar(20) NOT NULL DEFAULT 'CASH' COMMENT '통화 유형',
    `balance` decimal(19,2) NOT NULL DEFAULT 0 COMMENT '현재 잔액',
    `upt_date` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정일시',
    PRIMARY KEY (`user_id`, `currency_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='지갑 (CASH 전용)';

-- 2. wallet_lot 테이블 (POINT/BONUS 유효기간 관리)
CREATE TABLE IF NOT EXISTS `wallet_lot` (
    `lot_seq` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'Lot 시퀀스',
    `user_id` varchar(20) NOT NULL COMMENT '사용자 ID',
    `currency_type` varchar(20) NOT NULL COMMENT '통화 유형 (POINT, BONUS)',
    `amount` decimal(19,2) NOT NULL COMMENT '적립 금액',
    `remaining` decimal(19,2) NOT NULL COMMENT '잔여 금액',
    `expire_date` date NOT NULL COMMENT '만료일',
    `status` varchar(10) NOT NULL DEFAULT 'ACTIVE' COMMENT '상태 (ACTIVE, EXPIRED, USED)',
    `source` varchar(50) DEFAULT NULL COMMENT '적립 사유',
    `reg_date` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '등록일시',
    PRIMARY KEY (`lot_seq`),
    KEY `idx_user_currency_status` (`user_id`, `currency_type`, `status`, `expire_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='지갑 Lot (POINT/BONUS 유효기간 관리)';

-- 3. transaction 테이블 (거래 내역)
CREATE TABLE IF NOT EXISTS `transaction` (
    `seq` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '시퀀스',
    `tx_group_id` varchar(50) DEFAULT NULL COMMENT '복합결제 그룹 ID',
    `user_id` varchar(20) NOT NULL COMMENT '사용자 ID',
    `currency_type` varchar(20) NOT NULL COMMENT '통화 유형 (CASH, POINT, BONUS)',
    `tx_type` varchar(20) NOT NULL COMMENT '거래 유형 (CHARGE, DEDUCT, REFUND)',
    `amount` decimal(19,2) NOT NULL COMMENT '거래 금액',
    `balance_after` decimal(19,2) NOT NULL COMMENT '거래 후 잔액',
    `service_id` varchar(50) DEFAULT NULL COMMENT '서비스 ID',
    `unit_price` decimal(19,2) DEFAULT NULL COMMENT '단가',
    `quantity` decimal(10,3) DEFAULT NULL COMMENT '수량 (소수점 허용)',
    `lot_seq` bigint(20) DEFAULT NULL COMMENT '사용한 wallet_lot 시퀀스',
    `lot_expire_date` date DEFAULT NULL COMMENT '해당 Lot의 만료일 (환불 검증용)',
    `ref_tx_seq` bigint(20) DEFAULT NULL COMMENT '환불 시 원거래 시퀀스',
    `comment` varchar(500) DEFAULT NULL COMMENT '비고',
    `reg_date` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '등록일시',
    PRIMARY KEY (`seq`),
    KEY `idx_tx_group` (`tx_group_id`),
    KEY `idx_user_date` (`user_id`, `reg_date`),
    KEY `idx_lot` (`lot_seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='거래 내역';

-- 4. user_service_rate 테이블 (사용자별 단가)
CREATE TABLE IF NOT EXISTS `user_service_rate` (
    `seq` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '시퀀스',
    `user_id` varchar(20) NOT NULL COMMENT '사용자 ID',
    `service_id` varchar(50) NOT NULL COMMENT '서비스 ID',
    `rate` decimal(19,2) NOT NULL COMMENT '단가 (VAT 포함)',
    `start_date` date NOT NULL COMMENT '적용 시작일',
    `end_date` date DEFAULT NULL COMMENT '적용 종료일',
    `reg_date` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '등록일시',
    PRIMARY KEY (`seq`),
    KEY `idx_user_service_date` (`user_id`, `service_id`, `start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자별 서비스 단가';