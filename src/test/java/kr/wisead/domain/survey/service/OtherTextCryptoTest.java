package kr.wisead.domain.survey.service;

import static org.assertj.core.api.Assertions.assertThat;

import kr.wisead.common.util.CryptoUtils;
import org.junit.jupiter.api.Test;

/** 주관식 ANSWER 컬럼 PII detail별 암복호화 단위 테스트. */
class OtherTextCryptoTest {

  @Test
  void nePiiAnswers_encryptWithPrefixAndRoundTrip() {
    for (String detail : new String[] {"NE", "AD", "CU"}) {
      String plain = "값-" + detail;
      String stored = OtherTextCrypto.encryptAnswerByDetail(detail, plain);

      assertThat(stored).as(detail + " 접두사 암호화").startsWith("PII:").isNotEqualTo(plain);
      assertThat(OtherTextCrypto.decryptAnswerByDetail(detail, stored))
          .as(detail + " 복호화 복원")
          .isEqualTo(plain);
    }
  }

  @Test
  void emAnswer_encryptsLocalPartOnly() {
    String stored = OtherTextCrypto.encryptAnswerByDetail("EM", "ex@e.com");

    assertThat(stored).startsWith("PII:").endsWith("@e.com");
    assertThat(stored).doesNotContain("ex@");
    assertThat(OtherTextCrypto.decryptAnswerByDetail("EM", stored)).isEqualTo("ex@e.com");
  }

  @Test
  void soAnswer_alreadyEncryptedUpstream_notDoubleEncrypted() {
    // SO ANSWER는 SurveyService RSA 흐름에서 이미 AES256+Base64(무접두)로 암호화됨.
    String soEncrypted = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256("881234-1234567"));

    assertThat(OtherTextCrypto.encryptAnswerByDetail("SO", soEncrypted))
        .as("SO는 재암호화하지 않고 그대로 통과")
        .isEqualTo(soEncrypted);
    assertThat(OtherTextCrypto.decryptAnswerByDetail("SO", soEncrypted))
        .as("기존 무접두 포맷 복호화")
        .isEqualTo("881234-1234567");
  }

  @Test
  void nonPiiDetail_unchanged() {
    assertThat(OtherTextCrypto.encryptAnswerByDetail("SA", "자유 의견")).isEqualTo("자유 의견");
    assertThat(OtherTextCrypto.decryptAnswerByDetail("GE", "나여")).isEqualTo("나여");
    assertThat(OtherTextCrypto.encryptAnswerByDetail(null, "x")).isEqualTo("x");
  }

  @Test
  void legacyPlaintextAnswers_readBackUnchanged() {
    // 접두사 없는 기존 평문 데이터는 복호화 대상에서 제외되어 raw 유지 (마이그레이션 안전).
    assertThat(OtherTextCryptoDecrypt("NE", "아아아")).isEqualTo("아아아");
    assertThat(OtherTextCryptoDecrypt("CU", "010-3333-3333")).isEqualTo("010-3333-3333");
    assertThat(OtherTextCryptoDecrypt("EM", "ex@e.com")).isEqualTo("ex@e.com");
    assertThat(OtherTextCryptoDecrypt("AD", "(03054) 서울 종로구 청와대로 73 1"))
        .isEqualTo("(03054) 서울 종로구 청와대로 73 1");
  }

  private static String OtherTextCryptoDecrypt(String detail, String stored) {
    return OtherTextCrypto.decryptAnswerByDetail(detail, stored);
  }
}
