# 설문답변 레거시 평문 PII 백필 런북 (일회성)

## 배경
6/26 암호화 배포 **이전**에 저장된 설문답변 PII(NE 이름 / AD 주소 / CU 연락처 / EM 이메일)가
상용 DB에 **평문**으로 남아있다. 배포 이후 신규 저장분은 정상 암호화됨(진단 쿼리 `after_cutoff_LEAK=0` 확인).

진단 집계(2026-06-29 기준):

| 대상 | 평문 건수 |
|---|---|
| ANSWER NE/AD/CU/EM | 12,967 |
| OTHER_TEXT NE/AD/CU/EM | 1 |
| **합계** | **12,968** |
| SO(주민번호) 평문 | 0 (대상 아님) |

## 동작 방식
`SurveyPiiBackfillRunner`(Spring `ApplicationRunner`)가 운영 코드와 **동일한** `OtherTextCrypto.encryptForStorage`로
평문을 암호화한다. 키는 prod SSM에서 자동 주입(`ENC_DATA_KEY`)되므로 키가 외부에 노출되지 않는다.

안전장치:
- 기본 **비활성** (`backfill.survey-pii.enabled=true` 일 때만 동작)
- 기본 **dry-run** (`dry-run=false` 여야 실제 갱신)
- **멱등**: 이미 `PII:` 접두 붙은 값은 후보에서 제외 → 재실행 안전
- **행별 라운드트립 검증**: `decrypt(encrypt(x)) == x` 아니면 그 행은 쓰지 않고 보류 로그
- **autocommit 단건 갱신**: 중단 시 재실행하면 이어서 진행

## 절차

### 0. (필수) 백업
```sql
-- 전체 테이블 스냅샷 (복구용). 한 번만.
CREATE TABLE survey_answer_bak_20260629 AS SELECT * FROM survey_answer;
SELECT COUNT(*) FROM survey_answer_bak_20260629;  -- 원본과 동일한지 확인
```

### 1. 규모 재확인 (read-only)
`docs/sql/diag_survey_pii_plaintext_count.sql` 의 쿼리 ④ 실행 → `answer_plaintext_total` 확인.

### 2. dry-run (변경 없음, 건수만 로그)
상용 서버에서 **별도 1회 실행**(웹서버 미기동, 운영 서비스와 분리):
```bat
java -jar -Dspring.profiles.active=prod -Dspring.main.web-application-type=none ^
     -Dbackfill.survey-pii.enabled=true ^
     C:\app\wisead\wisead.jar
```
로그의 `예상갱신` 건수가 진단 ④ 총건수와 일치하는지, `검증실패=0` 인지 확인.

### 3. 실제 실행
```bat
java -jar -Dspring.profiles.active=prod -Dspring.main.web-application-type=none ^
     -Dbackfill.survey-pii.enabled=true -Dbackfill.survey-pii.dry-run=false ^
     C:\app\wisead\wisead.jar
```
로그의 `갱신` 건수 = 대상 건수, `검증실패=0`, `갱신실패=0` 확인.

### 4. 검증
```sql
-- 평문 잔량 0 이어야 함
SELECT COUNT(*) FROM survey_answer
WHERE QUESTION_TYPE_DETAIL IN ('NE','AD','CU','EM')
  AND ANSWER IS NOT NULL AND ANSWER <> '' AND ANSWER NOT LIKE 'PII:%';
```
+ 관리자 화면에서 해당 이벤트 응답 조회 시 이름/주소/이메일이 **정상 평문으로 복호화되어** 보이는지 확인.

### 5. 정리
- 백필 jar 실행 옵션은 1회성. 운영 서비스(WiseAdBE)에는 `backfill.survey-pii.enabled` 를 절대 켜지 않는다.
- 충분한 안정화 기간 후 `survey_answer_bak_20260629` 백업 테이블 삭제.

## 롤백
문제 발생 시 백업에서 복원:
```sql
-- 갱신된 행만 원복 (예시)
UPDATE survey_answer a
JOIN survey_answer_bak_20260629 b ON b.ANSWER_SEQ = a.ANSWER_SEQ
SET a.ANSWER = b.ANSWER, a.OTHER_TEXT = b.OTHER_TEXT;
```
