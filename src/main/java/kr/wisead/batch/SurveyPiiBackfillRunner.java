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

  /**
   * 개인정보 파기/플레이스홀더 마커(콤마 구분). 이 값들은 더 이상 PII가 아니므로 암호화 대상에서 제외한다. 빈값/'-'/마스킹('*...')은 SQL에서 별도 제외.
   * 발견 쿼리로 확인된 마커를 추가하라.
   */
  @Value("${backfill.survey-pii.disposed-markers:파기처리}")
  private String disposedMarkersCsv;

  @Override
  public void run(ApplicationArguments args) {
    java.util.List<String> markers = parseMarkers(disposedMarkersCsv);
    log.warn(
        "================ [설문 PII 백필] 시작 (dryRun={}, 제외마커={}) ================",
        dryRun, markers);

    Stats answer = process("ANSWER", mapper.selectBackfillAnswerCandidates(markers), true);
    Stats other = process("OTHER_TEXT", mapper.selectBackfillOtherTextCandidates(markers), false);

    log.warn("================ [설문 PII 백필] 종료 (dryRun={}) ================", dryRun);
    log.warn("  ANSHER     : {}", answer);
    log.warn("  OTHER_TEXT : {}", other);
    log.warn(
        "  TOTAL      : 대상 {}건 / {} {}건 / 무변경 {}건 / 이미암호문 {}건 / 검증실패 {}건 / 갱신실패 {}건",
        answer.total + other.total,
        dryRun ? "예상갱신" : "갱신",
        answer.applied() + other.applied(),
        answer.skippedNoChange + other.skippedNoChange,
        answer.skippedEncrypted + other.skippedEncrypted,
        answer.failedVerify + other.failedVerify,
        answer.failedUpdate + other.failedUpdate);

    if (exitAfter) {
      int code = (answer.failedVerify + other.failedVerify + answer.failedUpdate + other.failedUpdate) > 0 ? 1 : 0;
      log.warn("[설문 PII 백필] exit-after=true → 종료 (exitCode={})", code);
      System.exit(org.springframework.boot.SpringApplication.exit(applicationContext, () -> code));
    }
  }

  private static java.util.List<String> parseMarkers(String csv) {
    java.util.List<String> out = new java.util.ArrayList<>();
    if (csv == null) {
      return out;
    }
    for (String m : csv.split(",")) {
      String t = m.trim();
      if (!t.isEmpty()) {
        out.add(t);
      }
    }
    return out;
  }

  /** base64 문자 집합 패턴 (이미 암호화된 값 구조 판별용) */
  private static final java.util.regex.Pattern B64 =
      java.util.regex.Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");

  /**
   * 이미 (레거시) AES 암호문인지 구조적으로 판별한다.
   *
   * <p>레거시 암호문 = base64(base64(16바이트 배수 AES 블록)) 형태(접두/매직 없음). 평문(이름/주소/이메일/전화)은 한글·공백·@·-·. 등으로
   * 이중 base64 + 16바이트 배수 조건을 거의 만족하지 않는다. 신규 포맷은 'PII:' 접두로 SQL 단계에서 이미 제외됨. 이 판별로 레거시 암호문을 백필에서
   * 건너뛰어 이중 암호화를 방지한다.
   */
  static boolean isLikelyAesCiphertext(String value) {
    if (value == null) {
      return false;
    }
    String t = value.trim();
    if (t.length() < 16 || t.length() % 4 != 0 || !B64.matcher(t).matches()) {
      return false;
    }
    try {
      byte[] outer = java.util.Base64.getDecoder().decode(t);
      String s1 = new String(outer, java.nio.charset.StandardCharsets.US_ASCII);
      if (s1.isEmpty() || s1.length() % 4 != 0 || !B64.matcher(s1).matches()) {
        return false;
      }
      byte[] inner = java.util.Base64.getDecoder().decode(s1);
      return inner.length > 0 && inner.length % 16 == 0;
    } catch (RuntimeException e) {
      return false;
    }
  }

  private Stats process(String label, List<SurveyPiiBackfillRow> rows, boolean isAnswer) {
    Stats s = new Stats();
    s.total = rows.size();
    for (SurveyPiiBackfillRow r : rows) {
      String plain = r.getValue();
      // 이미 (레거시) 암호문이면 절대 재암호화하지 않는다(이중 암호화 방지).
      if (isLikelyAesCiphertext(plain)) {
        s.skippedEncrypted++;
        log.warn("[{}] 이미암호문-skip answerSeq={} type={}", label, r.getAnswerSeq(), r.getType());
        continue;
      }

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
    int skippedEncrypted;
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
          + ", 이미암호문="
          + skippedEncrypted
          + ", 검증실패="
          + failedVerify
          + ", 갱신실패="
          + failedUpdate;
    }
  }
}
