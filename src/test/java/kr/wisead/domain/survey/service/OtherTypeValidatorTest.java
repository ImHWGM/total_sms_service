package kr.wisead.domain.survey.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.domain.survey.entity.OtherType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * OtherTypeValidator 단위 테스트 — 6유형 boundary + SO shape 검증 (plan §3 Phase F-1, AC-10).
 *
 * <p>boundary 룰: SA 2000 / NE 60 / EM 120+regex / AD 500 / CU 2000 / SO RSA|FOREIGN|평문 jumin shape.
 */
class OtherTypeValidatorTest {

  @Nested
  class ValidateRaw {

    @Test
    void nullValue_throws() {
      assertThatThrownBy(() -> OtherTypeValidator.validateRawOtherText(OtherType.SA, null))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    void blankValue_throws() {
      assertThatThrownBy(() -> OtherTypeValidator.validateRawOtherText(OtherType.SA, "   "))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    void nullType_treatedAsSA() {
      assertThatCode(() -> OtherTypeValidator.validateRawOtherText(null, "x".repeat(2000)))
          .doesNotThrowAnyException();
      assertThatThrownBy(() -> OtherTypeValidator.validateRawOtherText(null, "x".repeat(2001)))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    void sa_atMaxLength_accepts() {
      assertThatCode(() -> OtherTypeValidator.validateRawOtherText(OtherType.SA, "x".repeat(2000)))
          .doesNotThrowAnyException();
    }

    @Test
    void sa_overMaxLength_throws() {
      assertThatThrownBy(
              () -> OtherTypeValidator.validateRawOtherText(OtherType.SA, "x".repeat(2001)))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    void cu_atMaxLength_accepts() {
      assertThatCode(() -> OtherTypeValidator.validateRawOtherText(OtherType.CU, "x".repeat(2000)))
          .doesNotThrowAnyException();
    }

    @Test
    void cu_overMaxLength_throws() {
      assertThatThrownBy(
              () -> OtherTypeValidator.validateRawOtherText(OtherType.CU, "x".repeat(2001)))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    void ne_atMaxLength_accepts() {
      assertThatCode(() -> OtherTypeValidator.validateRawOtherText(OtherType.NE, "이".repeat(60)))
          .doesNotThrowAnyException();
    }

    @Test
    void ne_overMaxLength_throws() {
      assertThatThrownBy(() -> OtherTypeValidator.validateRawOtherText(OtherType.NE, "이".repeat(61)))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    void ad_atMaxLength_accepts() {
      assertThatCode(() -> OtherTypeValidator.validateRawOtherText(OtherType.AD, "주".repeat(500)))
          .doesNotThrowAnyException();
    }

    @Test
    void ad_overMaxLength_throws() {
      assertThatThrownBy(() -> OtherTypeValidator.validateRawOtherText(OtherType.AD, "주".repeat(501)))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    void em_validFormat_accepts() {
      assertThatCode(() -> OtherTypeValidator.validateRawOtherText(OtherType.EM, "user@example.com"))
          .doesNotThrowAnyException();
    }

    @Test
    void em_invalidFormat_throws() {
      assertThatThrownBy(() -> OtherTypeValidator.validateRawOtherText(OtherType.EM, "no-at-symbol"))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    void em_overMaxLength_throws() {
      String longLocal = "a".repeat(115);
      String longEmail = longLocal + "@b.co";
      assertThatThrownBy(() -> OtherTypeValidator.validateRawOtherText(OtherType.EM, longEmail))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    void so_rsaPrefix_accepts() {
      assertThatCode(
              () ->
                  OtherTypeValidator.validateRawOtherText(
                      OtherType.SO, "RSA:881234:base64ciphertext"))
          .doesNotThrowAnyException();
    }

    @Test
    void so_foreignPrefix_accepts() {
      assertThatCode(
              () -> OtherTypeValidator.validateRawOtherText(OtherType.SO, "FOREIGN:passport123"))
          .doesNotThrowAnyException();
    }

    @Test
    void so_plainJumin_accepts() {
      assertThatCode(() -> OtherTypeValidator.validateRawOtherText(OtherType.SO, "881234-1234567"))
          .doesNotThrowAnyException();
    }

    @Test
    void so_arbitraryText_throws() {
      assertThatThrownBy(
              () -> OtherTypeValidator.validateRawOtherText(OtherType.SO, "not a jumin or rsa"))
          .isInstanceOf(BusinessException.class);
    }
  }

  @Nested
  class ValidatePlain {

    @Test
    void so_validJumin_accepts() {
      assertThatCode(() -> OtherTypeValidator.validatePlainOtherText(OtherType.SO, "881234-1234567"))
          .doesNotThrowAnyException();
    }

    @Test
    void so_noHyphen_throws() {
      assertThatThrownBy(
              () -> OtherTypeValidator.validatePlainOtherText(OtherType.SO, "8812341234567"))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    void so_null_throws() {
      assertThatThrownBy(() -> OtherTypeValidator.validatePlainOtherText(OtherType.SO, null))
          .isInstanceOf(BusinessException.class);
    }

    @Test
    void nonSO_isNoOp() {
      assertThatCode(() -> OtherTypeValidator.validatePlainOtherText(OtherType.SA, null))
          .doesNotThrowAnyException();
      assertThatCode(() -> OtherTypeValidator.validatePlainOtherText(OtherType.NE, "anything"))
          .doesNotThrowAnyException();
      assertThatCode(() -> OtherTypeValidator.validatePlainOtherText(OtherType.EM, "garbage"))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  class EmailPatternSnapshot {

    @Test
    void emailPattern_matchesEmailAuthServiceLine164() {
      // EmailAuthService.java:164 verbatim 회귀 방지 (plan §3 Phase A.0-1)
      String expected = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
      assertThatCode(
              () -> {
                if (!OtherTypeValidator.EMAIL_PATTERN.pattern().equals(expected)) {
                  throw new AssertionError(
                      "EMAIL_PATTERN drifted from EmailAuthService.java:164. expected="
                          + expected
                          + " actual="
                          + OtherTypeValidator.EMAIL_PATTERN.pattern());
                }
              })
          .doesNotThrowAnyException();
    }
  }
}
