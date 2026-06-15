-- 개인정보 접근 로그: 검색조건(무엇을) 저장 컬럼 추가
--
-- [배포 선행조건 / HARD PREREQUISITE]
-- 이 SQL은 코드 배포 *이전* 또는 *동시*에 운영 DB에 반드시 실행해야 한다.
-- ActionLogMapper.xml 의 selectLogColumns 가 L.SEARCH_CONDITION 을 무조건 SELECT 하므로,
-- 컬럼이 없는 상태로 신규 코드가 배포되면 접근 로그 검토 조회가 즉시 실패한다.
-- (Flyway 미연동 → 자동 실행 안 됨. 운영 DB 수동 실행 필수.)
ALTER TABLE ACTION_LOG
    ADD COLUMN SEARCH_CONDITION VARCHAR(1000) NULL AFTER ACTION_REASON;
