package kr.wisead.domain.survey.service;

import java.util.regex.Pattern;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.survey.entity.OtherType;

/**
 * 설문 객관식 "기타" 답변 유형별 검증 (plan §3 Phase D-1, AC-10).
 *
 * <p>2단 분리: {@link #validateRawOtherText} (RSA 복호화 전 raw 검증) + {@link #validatePlainOtherText} (RSA
 * 복호화 후 평문 검증). SO 유형만 두 단계가 필요하고, 그 외 5종은 raw == plain이라 raw 검증만 호출하면 충분하다.
 *
 * <p>EM 정규식은 {@code EmailAuthService.java:164} verbatim. NE/AD/CU 길이 제한은 spec.md R8 기반 신규 정책.
 */
public final class OtherTypeValidator {

  /** EmailAuthService.java:164 verbatim — 단일 source-of-truth. */
  public static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

  /** SurveyService.resolveSoAnswer의 평문 주민번호 패턴. */
  public static final Pattern JUMIN_PATTERN = Pattern.compile("^\\d{6}-\\d{7}$");

  private static final int MAX_SA = 2000;
  private static final int MAX_NE = 60;
  private static final int MAX_EM = 120;
  private static final int MAX_AD = 500;
  private static final int MAX_CU = 2000;

  private OtherTypeValidator() {}

  /**
   * RSA 복호화 전 raw OTHER_TEXT 검증. SO는 prefix shape만 체크하고 RSA 복호화 + 평문 jumin 검증은 {@link
   * #validatePlainOtherText}에서 수행한다.
   */
  public static void validateRawOtherText(OtherType type, String raw) {
    if (raw == null || raw.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "기타 항목 선택 시 텍스트 입력은 필수입니다.");
    }
    OtherType resolved = type != null ? type : OtherType.SA;
    switch (resolved) {
      case SA -> requireMaxLen(raw, MAX_SA, "기타 텍스트");
      case CU -> requireMaxLen(raw, MAX_CU, "기타 텍스트");
      case NE -> requireMaxLen(raw, MAX_NE, "이름");
      case AD -> requireMaxLen(raw, MAX_AD, "주소");
      case EM -> {
        requireMaxLen(raw, MAX_EM, "이메일");
        if (!EMAIL_PATTERN.matcher(raw).matches()) {
          throw new BusinessException(ErrorCode.INVALID_INPUT, "이메일 형식이 올바르지 않습니다.");
        }
      }
      case SO -> requireSoShape(raw);
    }
  }

  /**
   * RSA 복호화 후 평문 OTHER_TEXT 검증. SO만 의미 있고 그 외는 raw == plain이라 no-op. PR #4의 SO 흐름에서 RSA 복호화 결과를 검증할
   * 때 호출된다.
   */
  public static void validatePlainOtherText(OtherType type, String plain) {
    if (type != OtherType.SO) {
      return;
    }
    if (plain == null || !JUMIN_PATTERN.matcher(plain).matches()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "주민번호 형식이 올바르지 않습니다.");
    }
  }

  private static void requireMaxLen(String value, int max, String label) {
    if (value.length() > max) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, label + "은(는) " + max + "자를 초과할 수 없습니다.");
    }
  }

  private static void requireSoShape(String raw) {
    boolean ok =
        raw.startsWith("RSA:")
            || raw.startsWith("ENC:")
            || raw.startsWith("FOREIGN:")
            || JUMIN_PATTERN.matcher(raw).matches();
    if (!ok) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "주민번호 입력 형식이 올바르지 않습니다.");
    }
  }
}
