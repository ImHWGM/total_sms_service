package kr.wisead.batch;

import java.util.List;
import kr.wisead.domain.survey.service.OtherTextCrypto;
import kr.wisead.mapper.primary.SurveyAnswerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * [일회성] 설문답변 레거시 평문 PII 백필.
 *
 * <p>6/26 암호화 배포 이전에 평문으로 저장된 NE/AD/CU/EM 설문답변(ANSWER/OTHER_TEXT)을 운영 코드와 동일한 {@link
 * OtherTextCrypto#encryptForStorage}로 암호화한다. SO(주민번호)는 무접두 스킴이라 평문/암호문 구분이 모호해 대상에서 제외한다(집계 결과
 * 평문 SO 0건).
 *
 * <p>안전장치:
 *
 * <ul>
 *   <li>기본 비활성. {@code backfill.survey-pii.enabled=true} 일 때만 동작.
 *   <li>기본 dry-run. {@code backfill.survey-pii.dry-run=false} 여야 실제 갱신.
 *   <li>멱등: 이미 'PII:' 접두가 붙은 값은 SQL 후보 단계에서 제외 → 재실행 안전.
 *   <li>행별 라운드트립 자가검증: {@code decrypt(encrypt(x)) == x} 가 아니면 그 행은 쓰지 않고 보류 로그만 남긴다.
 *   <li>autocommit 단건 갱신: 중단 시 재실행하면 처리된 행은 건너뛰고 이어서 진행.
 * </ul>
 *
 * <p>실행 예(상용, 별도 1회 실행 — 웹서버 미기동):
 *
 * <pre>
 *   # 1) dry-run (변경 없이 건수만)
 *   java -jar -Dspring.profiles.active=prod -Dspring.main.web-application-type=none \
 *        -Dbackfill.survey-pii.enabled=true wisead.jar
 *   # 2) 실제 실행
 *   java -jar -Dspring.profiles.active=prod -Dspring.main.web-application-type=none \
 *        -Dbackfill.survey-pii.enabled=true -Dbackfill.survey-pii.dry-run=false wisead.jar
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "backfill.survey-pii.enabled", havingValue = "true")
public class SurveyPiiBackfillRunner implements ApplicationRunner {

  private final SurveyAnswerMapper mapper;
  private final ConfigurableApplicationContext applicationContext;

  @Value("${backfill.survey-pii.dry-run:true}")
  private boolean dryRun;

  /** 백필 종료 후 애플리케이션을 내릴지 여부 (1회성 실행 기본 true). */
  @Value("${backfill.survey-pii.exit-after:true}")
  private boolean exitAfter;

  @Override
  public void run(ApplicationArguments args) {
    log.warn("================ [설문 PII 백필] 시작 (dryRun={}) ================", dryRun);

    Stats answer = process("ANSWER", mapper.selectBackfillAnswerCandidates(), true);
    Stats other = process("OTHER_TEXT", mapper.selectBackfillOtherTextCandidates(), false);

    log.warn("================ [설문 PII 백필] 종료 (dryRun={}) ================", dryRun);
    log.warn("  ANSHER     : {}", answer);
    log.warn("  OTHER_TEXT : {}", other);
    log.warn(
        "  TOTAL      : 대상 {}건 / {} {}건 / 무변경 {}건 / 검증실패 {}건 / 갱신실패 {}건",
        answer.total + other.total,
        dryRun ? "예상갱신" : "갱신",
        answer.applied() + other.applied(),
        answer.skippedNoChange + other.skippedNoChange,
        answer.failedVerify + other.failedVerify,
        answer.failedUpdate + other.failedUpdate);

    if (exitAfter) {
      int code = (answer.failedVerify + other.failedVerify + answer.failedUpdate + other.failedUpdate) > 0 ? 1 : 0;
      log.warn("[설문 PII 백필] exit-after=true → 종료 (exitCode={})", code);
      System.exit(org.springframework.boot.SpringApplication.exit(applicationContext, () -> code));
    }
  }

  private Stats process(String label, List<SurveyPiiBackfillRow> rows, boolean isAnswer) {
    Stats s = new Stats();
    s.total = rows.size();
    for (SurveyPiiBackfillRow r : rows) {
      String plain = r.getValue();
      String enc = OtherTextCrypto.encryptForStorage(r.getType(), plain);

      // 암호화로 값이 바뀌지 않았다면(유형 미해당 또는 암호화 실패) 절대 쓰지 않는다.
      if (enc == null || enc.equals(plain)) {
        s.skippedNoChange++;
        log.warn("[{}] 무변경-skip answerSeq={} type={}", label, r.getAnswerSeq(), r.getType());
        continue;
      }

      // 라운드트립 자가검증: 복원값이 원문과 같아야만 갱신한다.
      String roundtrip = OtherTextCrypto.decryptForDisplay(r.getType(), enc);
      if (!plain.equals(roundtrip)) {
        s.failedVerify++;
        log.error(
            "[{}] 라운드트립 검증실패-쓰기보류 answerSeq={} type={}",
            label, r.getAnswerSeq(), r.getType());
        continue;
      }

      if (dryRun) {
        s.wouldUpdate++;
        continue;
      }

      int n =
          isAnswer
              ? mapper.updateAnswerValueById(r.getAnswerSeq(), enc)
              : mapper.updateOtherTextValueById(r.getAnswerSeq(), enc);
      if (n == 1) {
        s.updated++;
      } else {
        s.failedUpdate++;
        log.error("[{}] 갱신실패(affected={}) answerSeq={}", label, n, r.getAnswerSeq());
      }
    }
    return s;
  }

  /** 처리 통계. */
  private static final class Stats {
    int total;
    int updated;
    int wouldUpdate;
    int skippedNoChange;
    int failedVerify;
    int failedUpdate;

    int applied() {
      return updated + wouldUpdate;
    }

    @Override
    public String toString() {
      return "대상="
          + total
          + ", 갱신="
          + updated
          + ", 예상갱신="
          + wouldUpdate
          + ", 무변경="
          + skippedNoChange
          + ", 검증실패="
          + failedVerify
          + ", 갱신실패="
          + failedUpdate;
    }
  }
}
