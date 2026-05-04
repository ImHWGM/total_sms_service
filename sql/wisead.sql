-- --------------------------------------------------------
-- 호스트:                          127.0.0.1
-- 서버 버전:                        10.11.4-MariaDB - mariadb.org binary distribution
-- 서버 OS:                        Win64
-- HeidiSQL 버전:                  12.3.0.6589
-- --------------------------------------------------------

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET NAMES utf8 */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;


-- wise_ad 데이터베이스 구조 내보내기
CREATE DATABASE IF NOT EXISTS `wise_ad` /*!40100 DEFAULT CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci */;
USE `wise_ad`;

-- 테이블 wise_ad.action_log 구조 내보내기
CREATE TABLE IF NOT EXISTS `action_log` (
    `SEQ` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '액션로그 시퀀스',
    `MENU_NAME` varchar(30) NOT NULL COMMENT '메뉴 명',
    `ACTION_TYPE` char(1) NOT NULL COMMENT '액션타입(C, R, U, D)',
    `ACTION_REASON` varchar(200) DEFAULT NULL COMMENT '다운로드 사유',
    `MENU_URL` varchar(150) NOT NULL COMMENT '메뉴 URL',
    `CODE` varchar(100) DEFAULT NULL COMMENT '코드(성공여부 등, 200, 300, 400)',
    `REFERER` varchar(300) NOT NULL COMMENT '리퍼러(이전 접근 주소)',
    `USER_ID` varchar(20) DEFAULT NULL COMMENT '사용자 아이디',
    `USER_NAME` varchar(100) DEFAULT NULL COMMENT '사용자 이름',
    `IP` varchar(20) DEFAULT NULL COMMENT 'IP주소',
    `REG_DATE` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '등록일',
  PRIMARY KEY (`SEQ`),
  UNIQUE KEY `SEQ_UNIQUE` (`SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=16978 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='로그 테이블';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.auth_user_mapping 구조 내보내기
CREATE TABLE IF NOT EXISTS `auth_user_mapping` (
    `SEQ` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '매핑 시퀀스',
    `EVENT_SEQ` int(10) unsigned NOT NULL COMMENT '이벤트 시퀀스',
    `USER_SEQ` int(10) unsigned NOT NULL COMMENT '이벤트 대상자 시퀀스',
    `AUTH_CODE` int(10) unsigned DEFAULT NULL COMMENT '간편인증코드',
    `GENERAL_AUTH_CODE` varchar(25) DEFAULT NULL COMMENT '범용인증코드',
    `USER_KEY` varchar(30) NOT NULL COMMENT '사용자 키',
    `REG_ID` varchar(20) NOT NULL COMMENT '등록 아이디',
    `REG_DATE` datetime NOT NULL DEFAULT current_timestamp() COMMENT '등록일',
  PRIMARY KEY (`SEQ`),
  UNIQUE KEY `SEQ_UNIQUE` (`SEQ`),
  KEY `fk_TB_AUTH_USER_MAPPING_TB_SURVEY_USER1_idx` (`USER_SEQ`),
  KEY `fk_TB_AUTH_USER_MAPPING_TB_SURVEY_MASTER1` (`EVENT_SEQ`),
  CONSTRAINT `fk_TB_AUTH_USER_MAPPING_TB_SURVEY_MASTER1` FOREIGN KEY (`EVENT_SEQ`) REFERENCES `survey_master` (`EVENT_SEQ`),
  CONSTRAINT `fk_TB_AUTH_USER_MAPPING_TB_SURVEY_USER1` FOREIGN KEY (`USER_SEQ`) REFERENCES `survey_user` (`SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=18554 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci COMMENT='간편인증번호 매핑 테이블';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.balance 구조 내보내기
CREATE TABLE IF NOT EXISTS `balance` (
    `SEQ` int(11) NOT NULL AUTO_INCREMENT COMMENT '시퀀스',
    `USER_ID` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '기업회원 아이디',
    `BALANCE` decimal(19,1) DEFAULT NULL COMMENT '변동금액',
    `TOTAL_BALANCE` decimal(19,1) NOT NULL DEFAULT 0.0 COMMENT '총 금액',
    `OPERATION` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '포인트 구분( M : 사용 /  P : 충전)',
    `COMMENT` varchar(1000) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '사용 내용',
    `REG_DATE` timestamp NULL DEFAULT NULL COMMENT '변동 일자',
    `REG_ID` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '변동한 아이디',
    `SMS_PRICE` decimal(19,1) DEFAULT NULL COMMENT 'SMS 가격',
    `LMS_PRICE` decimal(19,1) DEFAULT NULL COMMENT 'LMS 가격',
    `MMS_PRICE` decimal(19,1) DEFAULT NULL COMMENT 'MMS 가격',
    `SUBTRACT_UNIT_PRICE` decimal(19,1) DEFAULT NULL,
    `REFUND` varchar(100) DEFAULT NULL COMMENT '발송실패건 금액 환불',
  PRIMARY KEY (`SEQ`) USING BTREE,
  KEY `USER_ID` (`USER_ID`),
  CONSTRAINT `FK_tb_balance_tb_manager` FOREIGN KEY (`USER_ID`) REFERENCES `user` (`USER_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=4521 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.blocked_number 구조 내보내기
CREATE TABLE IF NOT EXISTS `blocked_number` (
    `SEQ` int(11) NOT NULL AUTO_INCREMENT,
    `BLOCKED_NUMBER` varchar(100) DEFAULT NULL COMMENT '080XXXXXXX',
  PRIMARY KEY (`SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.blocked_senders 구조 내보내기
CREATE TABLE IF NOT EXISTS `blocked_senders` (
    `T_TIME` varchar(100) DEFAULT NULL COMMENT '전송 날짜(요청 전문 전송 시간)',
    `ANI` varchar(100) NOT NULL COMMENT '발신번호(발신자의 발신번호 (수신거부 할 번호))',
    `DTMF_1` varchar(100) NOT NULL COMMENT '입력 DTMF(상점 코드)',
    `MENU_NAME` varchar(100) DEFAULT NULL COMMENT '080XXXXXXX',
  PRIMARY KEY (`ANI`,`DTMF_1`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.charge_bonus_event 구조 내보내기
CREATE TABLE IF NOT EXISTS `charge_bonus_event` (
    `event_seq` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '이벤트 순번',
    `event_name` varchar(100) NOT NULL COMMENT '이벤트명',
    `event_type` varchar(20) NOT NULL COMMENT '이벤트 유형 (PERCENTAGE: 비율, FIXED: 고정금액)',
    `bonus_rate` decimal(5,4) DEFAULT NULL COMMENT '보너스 비율 (0.10 = 10%)',
    `bonus_amount` decimal(15,2) DEFAULT NULL COMMENT '고정 보너스 금액 (FIXED 타입용)',
    `min_charge_amount` decimal(15,2) DEFAULT NULL COMMENT '최소 충전 금액 (이상 충전 시 적용)',
    `max_bonus_amount` decimal(15,2) DEFAULT NULL COMMENT '최대 보너스 한도',
    `start_date` date DEFAULT NULL COMMENT '이벤트 시작일',
    `end_date` date DEFAULT NULL COMMENT '이벤트 종료일',
    `bonus_expire_days` int(11) DEFAULT 90 COMMENT '보너스 포인트 유효기간 (일)',
    `status` varchar(20) DEFAULT 'ACTIVE' COMMENT '상태 (ACTIVE, INACTIVE)',
    `created_by` varchar(50) NOT NULL COMMENT '생성자',
    `created_at` datetime DEFAULT current_timestamp() COMMENT '생성일시',
    `updated_at` datetime DEFAULT NULL ON UPDATE current_timestamp() COMMENT '수정일시',
  PRIMARY KEY (`event_seq`),
  KEY `idx_status_date` (`status`,`start_date`,`end_date`),
  KEY `idx_min_charge` (`min_charge_amount`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='충전 보너스 이벤트';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.customer_company 구조 내보내기
CREATE TABLE IF NOT EXISTS `customer_company` (
    `SEQ` int(11) NOT NULL AUTO_INCREMENT COMMENT '고객사 시퀀스',
    `USER_ID` varchar(20) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '회원 아이디',
    `SELECTED_USER_ID` varchar(20) NOT NULL COMMENT '선택된 고객사의 회원 아이디',
    `CORP_NAME` varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL COMMENT '고객사 이름',
    `UPT_DATE` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '수정 일자',
    `UPT_ID` varchar(20) DEFAULT '' COMMENT '수정한 아이디',
    `CHECKED_YN` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL DEFAULT 'N' COMMENT '체크 여부',
    `SUBTRACT_UNIT_PRICE_YN` varchar(1) NOT NULL DEFAULT 'N' COMMENT '자동차감 대상 고객사',
  PRIMARY KEY (`SEQ`) USING BTREE,
  KEY `USER_ID` (`USER_ID`),
  CONSTRAINT `FK_tb_cust_comp_tb_manager` FOREIGN KEY (`USER_ID`) REFERENCES `user` (`USER_ID`)
) ENGINE=InnoDB AUTO_INCREMENT=141 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='고객사';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.email_unsubscribe 구조 내보내기
CREATE TABLE IF NOT EXISTS `email_unsubscribe` (
    `e_id` bigint(20) NOT NULL AUTO_INCREMENT,
    `e_email` varchar(100) DEFAULT NULL,
    `unsubscribe_date` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`e_id`),
  UNIQUE KEY `e_email` (`e_email`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.event_action_log 구조 내보내기
CREATE TABLE IF NOT EXISTS `event_action_log` (
    `SEQ` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '로그 시퀀스',
    `PARTICIPANT_SEQ` bigint(20) NOT NULL COMMENT '참가자 시퀀스        \r\n  (EVENT_PARTICIPANT)',
    `ACTION_TYPE_SEQ` bigint(20) NOT NULL COMMENT '액션 유형 시퀀스     \r\n  (EVENT_ACTION_TYPE)',
    `ACTION_TIME` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '액션 시간',
    `DEVICE_INFO` varchar(8000) DEFAULT NULL COMMENT '체크인 기기 정보',
    `CONFIRMED_BY` varchar(50) DEFAULT NULL COMMENT '관리자 인증 시     \r\n  관리자 ID',
    `MEMO` varchar(200) DEFAULT NULL COMMENT '메모',
  PRIMARY KEY (`SEQ`),
  KEY `idx_participant_seq` (`PARTICIPANT_SEQ`),
  KEY `idx_action_type_seq` (`ACTION_TYPE_SEQ`),
  KEY `idx_action_time` (`ACTION_TIME`),
  CONSTRAINT `fk_event_action_log_action_type` FOREIGN KEY (`ACTION_TYPE_SEQ`) REFERENCES `event_action_type` (`SEQ`),
  CONSTRAINT `fk_event_action_log_participant` FOREIGN KEY (`PARTICIPANT_SEQ`) REFERENCES `event_participant` (`SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=78 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='행사 액션 로그 테이블';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.event_action_type 구조 내보내기
CREATE TABLE IF NOT EXISTS `event_action_type` (
    `SEQ` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '액션 유형 시퀀스',
    `EVENT_SEQ` int(10) unsigned NOT NULL COMMENT '이벤트 시퀀스        \r\n  (SURVEY_MASTER)',
    `ACTION_CODE` varchar(30) NOT NULL COMMENT '액션 코드 (CHECK_IN,    \r\n  PRIZE, GIFT, MEAL 등)',
    `ACTION_NAME` varchar(100) NOT NULL COMMENT '액션 이름 (입장, 경품  \r\n  수령 등)',
    `REQUIRE_ADMIN_AUTH` char(1) NOT NULL DEFAULT 'N' COMMENT '관리자   \r\n  인증 필요 여부 (Y/N)',
    `ALLOW_MULTIPLE` char(1) NOT NULL DEFAULT 'N' COMMENT '중복 허용    \r\n  여부 (Y/N)',
    `SORT_ORDER` int(11) NOT NULL DEFAULT 0 COMMENT '정렬 순서',
    `USE_YN` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부',
    `REG_DATE` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '등록일',
  PRIMARY KEY (`SEQ`),
  UNIQUE KEY `uk_event_action` (`EVENT_SEQ`,`ACTION_CODE`),
  KEY `idx_event_seq` (`EVENT_SEQ`),
  CONSTRAINT `fk_event_action_type_survey_master` FOREIGN KEY (`EVENT_SEQ`) REFERENCES `survey_master` (`EVENT_SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='행사별 액션 유형 정의  \r\n  테이블';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.event_nametag_log 구조 내보내기
CREATE TABLE IF NOT EXISTS `event_nametag_log` (
    `SEQ` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '로그 시퀀스',
    `PARTICIPANT_SEQ` bigint(20) NOT NULL COMMENT '참가자 시퀀스        \r\n  (EVENT_PARTICIPANT)',
    `PRINT_TIME` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '출력 시간',
    `TEMPLATE_TYPE` varchar(50) DEFAULT NULL COMMENT '명찰 템플릿 유형',
    `PRINT_BY` varchar(1000) DEFAULT NULL COMMENT '출력자 ID',
  PRIMARY KEY (`SEQ`),
  KEY `idx_participant_seq` (`PARTICIPANT_SEQ`),
  CONSTRAINT `fk_event_nametag_log_participant` FOREIGN KEY (`PARTICIPANT_SEQ`) REFERENCES `event_participant` (`SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=226 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='명찰 출력 이력 테이블';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.event_participant 구조 내보내기
CREATE TABLE IF NOT EXISTS `event_participant` (
    `SEQ` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '참가자 시퀀스',
    `SURVEY_USER_SEQ` int(10) unsigned NOT NULL COMMENT 'SURVEY_USER    \r\n  시퀀스 (1:1)',
    `EVENT_SEQ` int(10) unsigned NOT NULL COMMENT '이벤트 시퀀스        \r\n  (SURVEY_MASTER)',
    `CHECK_CODE` varchar(50) NOT NULL COMMENT 'QR용 고유 코드 (UUID)',
    `DEPARTMENT` varchar(100) DEFAULT NULL COMMENT '소속/부서',
    `POSITION` varchar(100) DEFAULT NULL COMMENT '직책',
    `PARTICIPANT_TYPE` varchar(20) DEFAULT NULL COMMENT '참가자 유형    \r\n  (VIP, 일반, 스태프 등)',
    `MEMO` varchar(500) DEFAULT NULL COMMENT '메모',
    `NAMETAG_PRINTED` char(1) NOT NULL DEFAULT 'N' COMMENT '명찰 출력   \r\n  여부',
    `REG_DATE` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '등록일',
    `MOD_DATE` timestamp NULL DEFAULT NULL COMMENT '수정일',
    `ATTEND_TIME` varchar(10) DEFAULT NULL,
    `REGIST_TYPE` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`SEQ`),
  UNIQUE KEY `uk_survey_user_seq` (`SURVEY_USER_SEQ`),
  UNIQUE KEY `uk_check_code` (`CHECK_CODE`),
  KEY `idx_event_seq` (`EVENT_SEQ`),
  CONSTRAINT `fk_event_participant_survey_master` FOREIGN KEY (`EVENT_SEQ`) REFERENCES `survey_master` (`EVENT_SEQ`),
  CONSTRAINT `fk_event_participant_survey_user` FOREIGN KEY (`SURVEY_USER_SEQ`) REFERENCES `survey_user` (`SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=641 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='행사 참가자 확장 정보  \r\n  테이블';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.kg_payment_info 구조 내보내기
CREATE TABLE IF NOT EXISTS `kg_payment_info` (
    `kg_seq` bigint(20) NOT NULL AUTO_INCREMENT,
    `svc_id` varchar(100) NOT NULL,
    `mobil_id` varchar(100) NOT NULL,
    `trade_id` varchar(100) NOT NULL,
    `prdt_nm` varchar(100) NOT NULL,
    `prdt_price` varchar(100) NOT NULL,
    `result_cd` varchar(100) DEFAULT NULL,
    `sign_date` varchar(100) NOT NULL,
    `user_id` varchar(100) DEFAULT NULL,
    `user_name` varchar(100) DEFAULT NULL,
    `payer_email` varchar(100) DEFAULT NULL,
    `interest` varchar(100) DEFAULT NULL,
    `card_num` varchar(100) DEFAULT NULL,
    `card_code` varchar(100) DEFAULT NULL,
    `card_name` varchar(100) DEFAULT NULL,
    `appr_no` varchar(100) DEFAULT NULL,
    `own_div_cd` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`kg_seq`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.msg_template 구조 내보내기
CREATE TABLE IF NOT EXISTS `msg_template` (
    `TEMPLATE_SEQ` bigint(20) NOT NULL AUTO_INCREMENT,
    `USER_SEQ` int(10) unsigned NOT NULL,
    `TEMPLATE_ORDER` int(10) unsigned NOT NULL,
    `SENDING_FORM` varchar(1) DEFAULT NULL,
    `MSG_TYPE` varchar(3) DEFAULT NULL,
    `SUBJECT` varchar(128) DEFAULT NULL,
    `TEXT` varchar(4000) DEFAULT NULL,
    `INSERT_TIME` datetime DEFAULT NULL,
    `IMAGE_PATH` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`TEMPLATE_SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=131 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.password_reset_token 구조 내보내기
CREATE TABLE IF NOT EXISTS `password_reset_token` (
    `SEQ` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '고유번호',
    `USER_ID` varchar(50) NOT NULL COMMENT '사용자 아이디',
    `TOKEN` varchar(255) NOT NULL COMMENT '인증 토큰(UUID)',
    `EXPIRE_DATE` datetime NOT NULL COMMENT '만료 일시',
    `USED_YN` char(1) DEFAULT 'N' COMMENT '사용 여부(Y/N)',
    `REG_DATE` datetime DEFAULT current_timestamp() COMMENT '생성 일시',
  PRIMARY KEY (`SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.pdf_config 구조 내보내기
CREATE TABLE IF NOT EXISTS `pdf_config` (
    `config_id` int(11) NOT NULL AUTO_INCREMENT COMMENT '설정 ID',
    `config_name` varchar(100) NOT NULL COMMENT '설정 이름',
    `config_type` enum('DEFAULT','MEMBER','EVENT') NOT NULL DEFAULT 'DEFAULT' COMMENT '설정 타입 (DEFAULT: 기본설정, MEMBER: 회원별, EVENT: 설문별)',
    `target_id` int(11) DEFAULT NULL COMMENT '대상 ID (회원별: userSeq, 설문별: eventSeq, 기본설정: NULL)',
    `font_size` decimal(4,1) NOT NULL DEFAULT 13.0 COMMENT '폰트 크기',
    `title_font_size` decimal(4,1) NOT NULL DEFAULT 22.0 COMMENT '제목 폰트 크기',
    `small_font_size` decimal(4,1) NOT NULL DEFAULT 11.0 COMMENT '작은 폰트 크기',
    `margin` decimal(5,1) NOT NULL DEFAULT 50.0 COMMENT '페이지 여백',
    `line_height` decimal(4,1) NOT NULL DEFAULT 20.0 COMMENT '줄 간격',
    `page_width` decimal(6,2) NOT NULL DEFAULT 595.28 COMMENT '페이지 너비 (A4: 595.28)',
    `page_height` decimal(6,2) NOT NULL DEFAULT 841.89 COMMENT '페이지 높이 (A4: 841.89)',
    `use_yn` char(1) NOT NULL DEFAULT 'Y' COMMENT '사용 여부',
    `created_at` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '생성일시',
    `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp() COMMENT '수정일시',
    `created_by` int(11) DEFAULT NULL COMMENT '생성자 ID',
    `updated_by` int(11) DEFAULT NULL COMMENT '수정자 ID',
  PRIMARY KEY (`config_id`),
  KEY `idx_config_type_target` (`config_type`,`target_id`),
  KEY `idx_use_yn` (`use_yn`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='PDF 설정 정보 테이블';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.qr_visit_log 구조 내보내기
CREATE TABLE IF NOT EXISTS `qr_visit_log` (
    `SEQ` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '방문 시퀀스',
    `EVENT_SEQ` int(10) unsigned NOT NULL COMMENT '이벤트 시퀀스 (SURVEY_MASTER)',
    `EVENT_STATUS` char(1) NOT NULL COMMENT '방문 시점의 이벤트 상태 (P:진행중, F:종료 등)',
    `VISIT_DATE` datetime NOT NULL DEFAULT current_timestamp() COMMENT '방문 일시',
  PRIMARY KEY (`SEQ`),
  KEY `idx_qr_visit_log_event_seq` (`EVENT_SEQ`),
  KEY `idx_qr_visit_log_visit_date` (`VISIT_DATE`),
  CONSTRAINT `fk_qr_visit_log_survey_master` FOREIGN KEY (`EVENT_SEQ`) REFERENCES `survey_master` (`EVENT_SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=3825 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='QR 코드 방문 로그 테이블';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.sms_send 구조 내보내기
CREATE TABLE IF NOT EXISTS `sms_send` (
    `SEQ` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '시퀀스',
    `EVENT_SEQ` int(10) unsigned NOT NULL COMMENT '이벤트 시퀀스',
    `USER_SEQ` int(11) NOT NULL COMMENT '유저 시퀀스',
    `SUBJECT` varchar(45) DEFAULT NULL COMMENT '제목',
    `CONTENT` varchar(2000) DEFAULT NULL COMMENT '내용',
    `SEND_TYPE` char(1) DEFAULT NULL COMMENT '발송타입(1: 즉시발송, 2: 예약발송)',
    `RECIVED_NUM` varchar(20) DEFAULT NULL COMMENT '수신번호',
    `CALLBACK` varchar(45) DEFAULT NULL COMMENT '발송연락처',
    `REQ_DATE` datetime DEFAULT NULL COMMENT '발송요청일시(YYYY-MM-DD HH:MM:SS)',
    `SEND_YN` char(1) NOT NULL DEFAULT 'N' COMMENT '발송여부',
    `REG_DATE` datetime DEFAULT NULL COMMENT '등록일',
    `REG_ID` varchar(20) DEFAULT NULL COMMENT '등록 아이디',
  PRIMARY KEY (`SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=16912 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.standard_rate 구조 내보내기
CREATE TABLE IF NOT EXISTS `standard_rate` (
    `service_seq` int(11) NOT NULL AUTO_INCREMENT,
    `service_id` varchar(50) DEFAULT NULL,
    `service_name` varchar(50) DEFAULT NULL,
    `service_rate` decimal(19,1) DEFAULT NULL,
    `service_description` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`service_seq`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.survey_answer 구조 내보내기
CREATE TABLE IF NOT EXISTS `survey_answer` (
    `ANSWER_SEQ` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '설문응답 시퀀스',
    `NAME` varchar(20) DEFAULT NULL,
    `EMAIL` varchar(80) DEFAULT NULL COMMENT '응답자 이메일 주소',
    `EVENT_SEQ` int(11) NOT NULL COMMENT '이벤트 시퀀스',
    `QUESTION_SEQ` int(10) unsigned NOT NULL,
    `QUESTION_TYPE` char(3) DEFAULT NULL COMMENT '질문유형(MC: 객관식, MCC: 다중선택, SA:단답형, SAA: 정해진 단답형)',
    `QUESTION_TYPE_DETAIL` varchar(3) DEFAULT NULL,
    `ITEM_SEQ` int(10) unsigned NOT NULL,
    `ANSWER` varchar(2000) DEFAULT NULL COMMENT '답변(주관식/객관식)',
    `OTHER_TEXT` varchar(2000) DEFAULT NULL COMMENT '기타 항목 텍스트',
    `FILE_PATH` varchar(250) DEFAULT NULL COMMENT '첨부파일 위치',
    `USER_SEQ` int(10) unsigned NOT NULL,
    `REG_DATE` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '응답 등록일',
  PRIMARY KEY (`ANSWER_SEQ`),
  KEY `fk_TB_SURVEY_ANSWER_TB_SURVEY_QUESTION1_idx` (`QUESTION_SEQ`),
  KEY `fk_TB_SURVEY_ANSWER_TB_SURVEY_ITEM1_idx` (`ITEM_SEQ`),
  KEY `fk_TB_SURVEY_ANSWER_TB_USER1_idx` (`USER_SEQ`),
  CONSTRAINT `fk_TB_SURVEY_ANSWER_TB_SURVEY_ITEM1` FOREIGN KEY (`ITEM_SEQ`) REFERENCES `survey_item` (`ITEM_SEQ`),
  CONSTRAINT `fk_TB_SURVEY_ANSWER_TB_SURVEY_QUESTION1` FOREIGN KEY (`QUESTION_SEQ`) REFERENCES `survey_question` (`QUESTION_SEQ`),
  CONSTRAINT `fk_TB_SURVEY_ANSWER_TB_USER1` FOREIGN KEY (`USER_SEQ`) REFERENCES `survey_user` (`SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=31955 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci COMMENT='개인정보/설문 응답(답변) 테이블';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.survey_item 구조 내보내기
CREATE TABLE IF NOT EXISTS `survey_item` (
    `ITEM_SEQ` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '설문조사 항목 시퀀스',
    `EVENT_SEQ` int(10) unsigned NOT NULL COMMENT '이벤트 시퀀스',
    `QUESTION_SEQ` int(10) unsigned NOT NULL COMMENT '설문조사 문항 시퀀스',
    `ITEM` varchar(1000) DEFAULT NULL COMMENT '항목 내용',
    `ITEM_VALUE` varchar(10) DEFAULT NULL,
    `ITEM_IMG` varchar(5000) DEFAULT NULL,
    `ORDER` tinyint(1) DEFAULT NULL COMMENT '항목 순서',
    `JUMP_QUESTION` varchar(10) DEFAULT NULL COMMENT '해당 응답 시 건너뛰어 진행할 문항',
    `OTHER_YN` char(1) NOT NULL DEFAULT 'N' COMMENT '기타 항목 여부',
    `OTHER_PLACEHOLDER` varchar(200) DEFAULT NULL COMMENT '기타 항목 입력 안내 문구',
    `REG_DATE` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '항목 등록일',
    `REG_ID` varchar(20) DEFAULT NULL COMMENT '항목 등록 ID',
  PRIMARY KEY (`ITEM_SEQ`,`EVENT_SEQ`,`QUESTION_SEQ`),
  KEY `fk_TB_SURVEY_ITEM_TB_SURVEY_QUESTION1_idx` (`QUESTION_SEQ`,`EVENT_SEQ`),
  CONSTRAINT `fk_TB_SURVEY_ITEM_TB_SURVEY_QUESTION1` FOREIGN KEY (`QUESTION_SEQ`, `EVENT_SEQ`) REFERENCES `survey_question` (`QUESTION_SEQ`, `EVENT_SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=15028 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci COMMENT='개인정보/설문 항목 테이블';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.survey_master 구조 내보내기
CREATE TABLE IF NOT EXISTS `survey_master` (
    `EVENT_SEQ` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '개인정보/설문조사 마스터',
    `USER_SEQ` int(10) unsigned NOT NULL,
    `EVENT_CODE` varchar(100) NOT NULL,
    `EVENT_NAME` varchar(1000) NOT NULL COMMENT '이벤트 명',
    `EVENT_EMPHASIS_YN` char(1) NOT NULL DEFAULT 'N' COMMENT '이벤트 명 강조여부',
    `EVENT_DESC` varchar(2000) DEFAULT NULL COMMENT '이벤트 설명',
    `EVENT_DESC_IMG` varchar(2000) DEFAULT NULL COMMENT '이벤트 설명 이미지',
    `EVENT_TYPE` char(1) DEFAULT NULL COMMENT '이벤트 타입 (개인정보취합: P, 설문조사: S)',
    `START_DATE` varchar(20) DEFAULT NULL COMMENT '이벤트 시작일',
    `END_DATE` varchar(20) DEFAULT NULL COMMENT '이벤트 종료일',
    `STATUS` char(1) NOT NULL COMMENT '이벤트 진행상태 (대기: A, 중지: S, 진행: P, 완료: F)',
    `AUTH` char(2) DEFAULT NULL COMMENT '인증여부 (실명: UA, 휴대폰: PA, 범용: GA, 없음: NA)',
    `PRIVACY_POLICY_YN` char(1) DEFAULT 'N' COMMENT '개인정보 정책 동의 사용여부',
    `PRIVACY_POLICY_TTL` varchar(500) DEFAULT NULL,
    `PRIVACY_POLICY_DESC` text DEFAULT NULL COMMENT '개인정보 정책 내용',
    `THIRD_PARTY_YN` char(1) DEFAULT 'N' COMMENT '개인정보 제3자 제공 동의 사용여부',
    `THIRD_PARTY_TTL` varchar(50) DEFAULT NULL COMMENT '개인정보 제3자 제공 동의 타이틀',
    `THIRD_PARTY_DESC` text DEFAULT NULL COMMENT '개인정보 제3자 제공 동의 내용',
    `QR_CODE` char(1) DEFAULT 'N' COMMENT 'QR 간편인증 사용여부',
    `QR_CODE_IMG_PATH` varchar(150) DEFAULT NULL COMMENT 'QR 간편인증 이미지 패스',
    `AUTH_CODE_URL` varchar(100) DEFAULT NULL COMMENT '인증코드URL',
    `REG_DATE` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '이벤트 등록일',
    `REG_ID` varchar(20) DEFAULT NULL COMMENT '등록 ID',
    `UPT_DATE` timestamp NULL DEFAULT NULL COMMENT '수정일',
    `UPT_ID` varchar(20) DEFAULT NULL COMMENT '수정 ID',
    `END_MESSAGE` varchar(1000) DEFAULT NULL,
    `EVENT_END_IMG` varchar(2000) DEFAULT NULL,
    `AUTH_KEY_DESC` varchar(5000) DEFAULT NULL,
    `VENUE` varchar(200) DEFAULT NULL,
    `ORGANIZER` varchar(200) DEFAULT NULL,
    `BADGE_PRINT_TYPE` char(1) DEFAULT NULL COMMENT '기본값 - 출력안함, C-체크인, N-명찰출력',
    `NAMETAG_CONFIG` text DEFAULT NULL,
    `STAFF_AUTH_CODE` varchar(20) DEFAULT NULL COMMENT '스태프 체크인 인증코드',
    `PRE_SURVEY_START_DATE` datetime DEFAULT NULL COMMENT '사전설문 시작일',
    `PRE_SURVEY_END_DATE` datetime DEFAULT NULL COMMENT '사전설문 종료일',
  PRIMARY KEY (`EVENT_SEQ`),
  UNIQUE KEY `idTB_PI_SURVEY_UNIQUE` (`EVENT_SEQ`),
  KEY `fk_TB_SURVEY_MASTER_TB_COMPANY1_idx` (`USER_SEQ`) USING BTREE,
  CONSTRAINT `fk_TB_SURVEY_MASTER_TB_COMPANY1` FOREIGN KEY (`USER_SEQ`) REFERENCES `user` (`SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=683 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci COMMENT='개인정보/설문 마스터 테이블';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.survey_question 구조 내보내기
CREATE TABLE IF NOT EXISTS `survey_question` (
    `QUESTION_SEQ` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '설문조사 문항 시퀀스',
    `EVENT_SEQ` int(10) unsigned NOT NULL COMMENT '설문조사 시퀀스(survey_master)',
    `QUESTION_TYPE` char(3) NOT NULL COMMENT '질문유형(MC: 객관식, MCC: 다중선택, SA:단답형, SAA: 정해진 단답형)',
    `QUESTION_TYPE_DETAIL` varchar(3) DEFAULT NULL,
    `QUESTION` varchar(2000) DEFAULT NULL COMMENT '질문',
    `QUESTION_IMG` varchar(5000) DEFAULT NULL,
    `ORD` tinyint(1) DEFAULT NULL,
    `REG_DATE` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '질문 등록일',
    `REG_ID` varchar(20) DEFAULT NULL COMMENT '질문 등록 ID',
    `FOREIGN_ALLOW` char(1) DEFAULT 'N',
    `REQUIRED_YN` char(1) NOT NULL DEFAULT 'N' COMMENT '필수 응답 여부 (Y:필수, N:선택)',
  PRIMARY KEY (`QUESTION_SEQ`,`EVENT_SEQ`),
  KEY `fk_TB_SURVEY_QUESTION_TB_SURVEY_MASTER1_idx` (`EVENT_SEQ`),
  CONSTRAINT `fk_TB_SURVEY_QUESTION_TB_SURVEY_MASTER1` FOREIGN KEY (`EVENT_SEQ`) REFERENCES `survey_master` (`EVENT_SEQ`),
  CONSTRAINT `CK_SURVEY_QUESTION_REQUIRED_YN` CHECK (`REQUIRED_YN` IN ('Y','N'))
) ENGINE=InnoDB AUTO_INCREMENT=5896 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci COMMENT='개인정보/설문 문항 테이블';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.survey_user 구조 내보내기
CREATE TABLE IF NOT EXISTS `survey_user` (
    `SEQ` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '사용자 시퀀스',
    `EVENT_SEQ` int(10) unsigned NOT NULL,
    `USER_KEY` varchar(100) NOT NULL COMMENT '사용자 난수키',
    `USER_NAME` varchar(500) DEFAULT NULL COMMENT '사용자 이름',
    `JUMIN_NUM` varchar(5000) DEFAULT NULL COMMENT '주민번호(암호화)',
    `USER_PHONE` varchar(50) DEFAULT NULL COMMENT '전화번호/휴대전화(암호화)',
    `RESEND_USER_PHONE` varchar(50) DEFAULT NULL COMMENT '재발송 전화번호(암호화)',
    `USER_EMAIL` varchar(150) DEFAULT NULL COMMENT '사용자 이메일',
    `ADDRESS` varchar(250) DEFAULT NULL COMMENT '기본주소',
    `ADDRESS2` varchar(200) DEFAULT NULL COMMENT '상세주소',
    `DEL_YN` char(1) DEFAULT 'N' COMMENT '계정 사용여부',
    `REG_ID` varchar(45) DEFAULT NULL COMMENT '등록자 아이디',
    `REG_DATE` timestamp NULL DEFAULT current_timestamp() COMMENT '등록일',
    `UPT_ID` varchar(45) DEFAULT NULL COMMENT '수정자 아이디',
    `UPT_DATE` timestamp NULL DEFAULT NULL COMMENT '수정일',
    `RAND_NUM` varchar(15) DEFAULT NULL COMMENT '난수',
    `DEPOSIT_DATE` date DEFAULT NULL COMMENT '입금일자 (YYYY-MM-DD)',
    `DEPOSIT_AMOUNT` int(11) DEFAULT NULL COMMENT '입금금액',
    `SHIPMENT_DATE` date DEFAULT NULL COMMENT '출고일자 (YYYY-MM-DD)',
    `SUBMISSION_DATE` datetime DEFAULT NULL COMMENT '제출일/최종 완료일',
    `LAST_CON_DATE` datetime DEFAULT NULL COMMENT '최종접속일',
    `SURVEY_START_TIME` datetime DEFAULT NULL,
    `SURVEY_AUTH_TIME` datetime DEFAULT NULL,
  PRIMARY KEY (`SEQ`),
  UNIQUE KEY `SEQ_UNIQUE` (`SEQ`),
  UNIQUE KEY `USER_KEY` (`USER_KEY`),
  KEY `fk_TB_USER_TB_SURVEY_MASTER1_idx` (`EVENT_SEQ`),
  CONSTRAINT `fk_TB_SURVEY_USER_TB_SURVEY_MASTER1` FOREIGN KEY (`EVENT_SEQ`) REFERENCES `survey_master` (`EVENT_SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=22325 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='회원 테이블';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.transaction 구조 내보내기
CREATE TABLE IF NOT EXISTS `transaction` (
    `seq` bigint(20) NOT NULL AUTO_INCREMENT,
    `tx_group_id` varchar(50) DEFAULT NULL COMMENT '복합결제 그룹',
    `user_seq` int(10) unsigned NOT NULL,
    `currency_type` varchar(20) NOT NULL DEFAULT 'CASH',
    `tx_type` varchar(20) NOT NULL COMMENT 'CHARGE, DEDUCT, REFUND',
    `amount` decimal(19,2) NOT NULL,
    `balance_after` decimal(19,2) NOT NULL,
    `service_id` varchar(50) DEFAULT NULL,
    `unit_price` decimal(19,2) DEFAULT NULL,
    `quantity` decimal(10,3) DEFAULT NULL COMMENT '소수점 건수',
    `lot_seq` bigint(20) DEFAULT NULL,
    `lot_expire_date` date DEFAULT NULL COMMENT '환불 검증용',
    `ref_tx_seq` bigint(20) DEFAULT NULL COMMENT '환불 시 원거래',
    `comment` varchar(500) DEFAULT NULL,
    `reg_date` timestamp NOT NULL DEFAULT current_timestamp(),
    `reg_id` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`seq`),
  KEY `idx_tx_group` (`tx_group_id`),
  KEY `idx_user_date` (`reg_date`),
  KEY `idx_transaction_user_seq` (`user_seq`),
  CONSTRAINT `FK_transaction_user` FOREIGN KEY (`user_seq`) REFERENCES `user` (`SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=11790 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.user 구조 내보내기
CREATE TABLE IF NOT EXISTS `user` (
    `SEQ` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '기업정보 시퀀스',
    `USER_ID` varchar(20) NOT NULL COMMENT '기업회원 아이디',
    `USER_PASS` varchar(100) NOT NULL COMMENT '기업회원 패스워드',
    `CORP_NAME` varchar(30) NOT NULL COMMENT '기업명',
    `CORP_ADDR` varchar(250) NOT NULL,
    `BIZ_NUM` varchar(20) NOT NULL COMMENT '사업자 등록번호',
    `BIZ_TEL` varchar(20) NOT NULL,
    `PERSON` varchar(100) NOT NULL COMMENT '담당자명',
    `PHONE` varchar(100) NOT NULL COMMENT '담당자 연락처',
    `EMAIL` varchar(100) NOT NULL COMMENT '담당자 이메일',
    `USER_LEVEL` tinyint(4) NOT NULL COMMENT '사용자(관리자) 권한',
    `USE_YN` char(1) NOT NULL DEFAULT 'Y' COMMENT '기업회원 사용여부',
    `ALLOW_IP_YN` char(1) NOT NULL DEFAULT 'N' COMMENT '허용IP 사용유부',
    `ALLOW_IP` varchar(400) DEFAULT NULL COMMENT '접근허용 IP',
    `LAST_LOGIN` timestamp NULL DEFAULT NULL COMMENT '최근 로그인 일자',
    `LOGIN_FAILURE_CNT` tinyint(1) DEFAULT 0 COMMENT '로그인 실패횟수',
    `REG_DATE` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '기업정보 등록일',
    `REG_ID` varchar(20) DEFAULT NULL COMMENT '등록 아이디',
    `UPT_DATE` timestamp NULL DEFAULT NULL COMMENT '기업정보 수정일',
    `UPT_ID` varchar(20) DEFAULT NULL COMMENT '기업정보 수정 ID',
    `STATUS` varchar(10) NOT NULL DEFAULT '미승인' COMMENT '기업회원 상태(미승인/승인)',
    `EMAIL_CODE` varchar(8) DEFAULT NULL COMMENT '이메일 인증코드',
    `CODE_VALIDATE` datetime DEFAULT NULL COMMENT '인증코드 유효시간',
    `CALLBACK` varchar(250) DEFAULT NULL COMMENT '문자 발신 번호1',
    `BIZ_PDF_LOC` varchar(300) DEFAULT NULL COMMENT '사업자등록증 파일 위치',
    `STORE_CODE` varchar(5) DEFAULT NULL,
    `BLOCKED_SEQ` int(11) DEFAULT NULL,
  PRIMARY KEY (`SEQ`),
  UNIQUE KEY `USER_ID` (`USER_ID`),
  UNIQUE KEY `STORE_CODE` (`STORE_CODE`)
) ENGINE=InnoDB AUTO_INCREMENT=300 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci COMMENT='기업회원 테이블';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.user_notification_setting 구조 내보내기
CREATE TABLE IF NOT EXISTS `user_notification_setting` (
    `noti_seq` int(10) NOT NULL AUTO_INCREMENT,
    `user_seq` int(10) unsigned DEFAULT NULL,
    `is_marketing_agreed` varchar(1) DEFAULT NULL,
    `marketing_agreed_at` datetime DEFAULT current_timestamp(),
  PRIMARY KEY (`noti_seq`),
  KEY `FK_tb_user_notification_setting_tb_manager` (`user_seq`),
  CONSTRAINT `FK_tb_user_notification_setting_tb_manager` FOREIGN KEY (`user_seq`) REFERENCES `user` (`SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.user_password_hint 구조 내보내기
CREATE TABLE IF NOT EXISTS `user_password_hint` (
    `SEQ` int(10) unsigned NOT NULL AUTO_INCREMENT COMMENT '힌트 테이블 시퀀스',
    `USER_SEQ` int(10) unsigned NOT NULL COMMENT '매니저 시퀀스',
    `HINT_QUESTION` varchar(50) NOT NULL COMMENT '암호 힌트 질문',
    `HINT_ANSWER` varchar(80) NOT NULL COMMENT '암호 힌트 답변',
    `REG_DATE` timestamp NOT NULL DEFAULT current_timestamp() COMMENT '답변 등록일',
    `UPT_DATE` timestamp NULL DEFAULT NULL COMMENT '답변 수정일',
  PRIMARY KEY (`SEQ`) USING BTREE,
  UNIQUE KEY `MNG_SEQ` (`USER_SEQ`),
  KEY `fk_TB_USER_HINT_TB_MANAGER1_idx` (`USER_SEQ`),
  CONSTRAINT `fk_TB_MANAGER_PASS_HINT_TB_MANAGER1` FOREIGN KEY (`USER_SEQ`) REFERENCES `user` (`SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=70 DEFAULT CHARSET=utf8mb3 COLLATE=utf8mb3_general_ci COMMENT='관리자 암호 힌트';

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.user_service_rate 구조 내보내기
CREATE TABLE IF NOT EXISTS `user_service_rate` (
    `seq` bigint(20) NOT NULL AUTO_INCREMENT,
    `user_seq` int(10) unsigned NOT NULL,
    `service_id` varchar(50) NOT NULL,
    `rate` decimal(19,2) NOT NULL COMMENT 'VAT 포함 단가',
    `start_date` date NOT NULL,
    `end_date` date DEFAULT NULL COMMENT 'NULL=현재 유효',
    `reg_date` timestamp NOT NULL DEFAULT current_timestamp(),
    `reg_id` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`seq`),
  KEY `idx_user_service_date` (`service_id`,`start_date`),
  KEY `idx_user_service_rate_user_seq` (`user_seq`),
  CONSTRAINT `FK_user_service_rate_user` FOREIGN KEY (`user_seq`) REFERENCES `user` (`SEQ`)
) ENGINE=InnoDB AUTO_INCREMENT=108 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.wallet 구조 내보내기
CREATE TABLE IF NOT EXISTS `wallet` (
    `user_seq` int(10) unsigned NOT NULL,
    `currency_type` varchar(20) NOT NULL DEFAULT 'CASH',
    `balance` decimal(19,2) NOT NULL DEFAULT 0.00,
    `upt_date` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  PRIMARY KEY (`user_seq`,`currency_type`) USING BTREE,
  CONSTRAINT `FK_wallet_user` FOREIGN KEY (`user_seq`) REFERENCES `user` (`SEQ`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

-- 테이블 wise_ad.wallet_lot 구조 내보내기
CREATE TABLE IF NOT EXISTS `wallet_lot` (
    `lot_seq` bigint(20) NOT NULL AUTO_INCREMENT,
    `user_seq` int(10) unsigned DEFAULT NULL,
    `currency_type` varchar(20) NOT NULL COMMENT 'POINT, BONUS',
    `amount` decimal(19,2) NOT NULL COMMENT '적립 금액',
    `remaining` decimal(19,2) NOT NULL COMMENT '잔여 금액',
    `expire_date` date NOT NULL COMMENT '만료일',
    `status` varchar(10) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, EXPIRED, USED',
    `source` varchar(50) DEFAULT NULL COMMENT '적립 사유',
    `reg_date` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`lot_seq`),
  KEY `idx_user_currency_status` (`currency_type`,`status`,`expire_date`),
  KEY `FK_wallet_lot_user` (`user_seq`),
  CONSTRAINT `FK_wallet_lot_user` FOREIGN KEY (`user_seq`) REFERENCES `user` (`SEQ`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 내보낼 데이터가 선택되어 있지 않습니다.

/*!40103 SET TIME_ZONE=IFNULL(@OLD_TIME_ZONE, 'system') */;
/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;
/*!40014 SET FOREIGN_KEY_CHECKS=IFNULL(@OLD_FOREIGN_KEY_CHECKS, 1) */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40111 SET SQL_NOTES=IFNULL(@OLD_SQL_NOTES, 1) */;
