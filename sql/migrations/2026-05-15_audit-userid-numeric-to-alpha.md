# 2026-05-15 audit-userid numeric → alpha 마이그레이션

## 변경 요약
audit 컬럼 7개의 numeric(`user.SEQ` 문자열) row 를 user_id (`user.USER_ID`, alpha) 로 일괄 정규화.

대상 컬럼 (상용 분포 기준):

| 테이블 | 컬럼 | numeric | alpha | numeric% |
|---|---|---:|---:|---:|
| sms_send | REG_ID | 4180 | 13644 | 23% |
| survey_user | REG_ID | 1919 | 17459 | 9% |
| auth_user_mapping | REG_ID | 1516 | 14874 | 9% |
| survey_user | UPT_ID | 53 | 26 | (소수) |
| user | UPT_ID | 6 | 4 | (소수) |
| customer_company | UPT_ID | 2 | 10 | (소수) |
| transaction | reg_id | 1 | 4308 | <1% |

총 ~7677 row 영향 예상.

## 배포 순서 (엄수)
1. **BE 배포** (PR #40 머지) — audit 컬럼에 alpha 저장하도록 코드 변경. 신규 row alpha 보장.
2. **DML 적용** (본 SQL 파일) — 운영 DB 에 직접 실행. 기존 numeric row 정규화.
3. **사후 검증** — 분포 SQL 로 numeric 0 건 확인.

PR #40 머지 전 실행 시 신규 INSERT 가 다시 numeric 으로 들어와 효과 상쇄됨.

## 재시작 필요?
불필요. UPDATE 만 수행, 스키마 변경 없음. 애플리케이션 무중단.

## 안전 장치 (SQL 내장)
- `WHERE REGEXP '^[0-9]+$'`: 이미 alpha 인 row 안 건드림 (idempotent)
- `JOIN user u ON u.SEQ = CAST(...)`: orphan (user.SEQ 매칭 실패) 자동 스킵
- NULL row 변경 안 함

## 롤백
**불가능**. UPDATE 후 원래 numeric 값 추적 안 됨. 적용 전 백업 권장.

```sh
mysqldump -h 127.0.0.1 -u <user> -p wise_ad \
  sms_send survey_user auth_user_mapping user customer_company transaction \
  > backup_audit_$(date +%Y%m%d_%H%M%S).sql
```

## 사전 검증
```sql
-- numeric 분포 확인
SELECT 'sms_send' tbl, COUNT(*) numeric_cnt FROM sms_send WHERE REG_ID REGEXP '^[0-9]+$'
UNION ALL SELECT 'survey_user.REG_ID', COUNT(*) FROM survey_user WHERE REG_ID REGEXP '^[0-9]+$'
UNION ALL SELECT 'survey_user.UPT_ID', COUNT(*) FROM survey_user WHERE UPT_ID REGEXP '^[0-9]+$'
UNION ALL SELECT 'auth_user_mapping', COUNT(*) FROM auth_user_mapping WHERE REG_ID REGEXP '^[0-9]+$'
UNION ALL SELECT 'user.UPT_ID', COUNT(*) FROM `user` WHERE UPT_ID REGEXP '^[0-9]+$'
UNION ALL SELECT 'customer_company.UPT_ID', COUNT(*) FROM customer_company WHERE UPT_ID REGEXP '^[0-9]+$'
UNION ALL SELECT 'transaction.reg_id', COUNT(*) FROM `transaction` WHERE reg_id REGEXP '^[0-9]+$';
```

```sql
-- orphan 식별 (변환 대상 외 잔존 — JOIN 으로 자동 스킵)
SELECT 'sms_send' tbl, COUNT(*) orphan_cnt FROM sms_send
 WHERE REG_ID REGEXP '^[0-9]+$'
   AND NOT EXISTS (SELECT 1 FROM `user` u WHERE u.SEQ = CAST(sms_send.REG_ID AS UNSIGNED));
```

dev 검증: sms_send numeric=101, orphan=0 (2026-05-15).

## 사후 검증
```sql
-- 위 사전 검증 SQL 동일 실행 — 모든 numeric_cnt = 0 또는 orphan 만 잔존
```

## 의도적 미처리
- **transaction.reg_id NULL ~5572 row** (2026-02~05 cutover 손실, PR #41 으로 신규 NULL 차단됨): 원래 actor 정보 없음. 회복 불가. NULL 유지. 'UNKNOWN' sentinel 백필은 의미 없으므로 미수행.
- **survey_master.REG_ID/UPT_ID NULL**: 의미적으로 시스템 등록 또는 누락. 본 마이그레이션 대상 외.
- **orphan numeric** (JOIN 매칭 실패): 자동 스킵. 삭제된 user / 시스템 계정 / 깨진 row 가능성. 별도 추적.

## 관련 PR
- PR #40: audit user_id 통일 (Message 계열 코드) — 선행 머지 필수
- PR #41: transaction.reg_id NULL 회귀 복구 — 신규 NULL 차단
