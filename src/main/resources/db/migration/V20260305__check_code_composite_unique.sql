-- CHECK_CODE 단독 UNIQUE 제거 → (EVENT_SEQ, CHECK_CODE) 복합 UNIQUE로 변경
-- 체크코드가 5자리로 짧아지면서 전역 unique 대신 이벤트 내 unique로 변경

-- 기존 UNIQUE 인덱스 제거
ALTER TABLE EVENT_PARTICIPANT DROP INDEX uk_check_code;

-- 복합 UNIQUE 인덱스 생성
ALTER TABLE EVENT_PARTICIPANT ADD CONSTRAINT uk_event_check_code UNIQUE (EVENT_SEQ, CHECK_CODE);
