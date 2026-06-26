-- 아이디찾기 이메일 인증 흐름에서 발송 시점에 특정된 사용자 SEQ 를 보관할 컬럼.
-- findByEmail 은 이메일에 UNIQUE 제약이 없어 중복 시 오조회 위험이 있으므로,
-- 인증 성공 후 target(seq) 으로 정확한 사용자를 조회한다.
-- ⚠ 멱등: ADD COLUMN IF NOT EXISTS (MariaDB 10.1.4+)
ALTER TABLE verification
    ADD COLUMN IF NOT EXISTS target VARCHAR(255) NULL
        COMMENT '발송 시점에 특정된 보조 값 (아이디찾기: user.seq, 미사용 흐름: NULL)'
        AFTER identifier;
