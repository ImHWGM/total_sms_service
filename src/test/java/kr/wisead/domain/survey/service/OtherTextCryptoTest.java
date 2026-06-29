package kr.wisead.domain.survey.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kr.wisead.common.util.CryptoUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** PII 답변(ANSWER/OTHER_TEXT) 유형별 암복호화 단위 테스트. 유형 키는 detail/OtherType.name() 문자열. */
class OtherTextCryptoTest {

  @BeforeAll
  static void setUpKeys() {
    CryptoUtils.configureKeys(List.of("EPOPKONSCRACHA!EVENTEPOPKONENMADA@"));
  }

  @Test
  void neAdCu_encryptWithPrefixAndRoundTrip() {
    for (String detail : new String[] {"NE", "AD", "CU"}) {
      String plain = "값-" + detail;
      String stored = OtherTextCrypto.encryptForStorage(detail, plain);

      assertThat(stored).as(detail + " 접두사 암호화").startsWith("PII:").isNotEqualTo(plain);
      assertThat(OtherTextCrypto.decryptForDisplay(detail, stored))
          .as(detail + " 복호화 복원")
          .isEqualTo(plain);
    }
  }

  @Test
  void em_encryptsLocalPartOnly_domainPlaintext() {
    String stored = OtherTextCrypto.encryptForStorage("EM", "ex@e.com");

    assertThat(stored).startsWith("PII:").endsWith("@e.com");
    assertThat(stored).doesNotContain("ex@");
    assertThat(OtherTextCrypto.decryptForDisplay("EM", stored)).isEqualTo("ex@e.com");
  }

  @Test
  void so_notEncryptedHere_butDecryptsLegacyFormat() {
    // SO는 SurveyService RSA 흐름에서 이미 AES256+Base64(무접두) 암호화됨.
    String soEncrypted = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256("881234-1234567"));

    assertThat(OtherTextCrypto.encryptForStorage("SO", soEncrypted))
        .as("SO는 재암호화하지 않고 통과")
        .isEqualTo(soEncrypted);
    assertThat(OtherTextCrypto.decryptForDisplay("SO", soEncrypted))
        .as("기존 무접두 포맷 복호화")
        .isEqualTo("881234-1234567");
  }

  @Test
  void nonPiiDetail_unchanged() {
    assertThat(OtherTextCrypto.encryptForStorage("SA", "자유 의견")).isEqualTo("자유 의견");
    assertThat(OtherTextCrypto.decryptForDisplay("GE", "나여")).isEqualTo("나여");
    assertThat(OtherTextCrypto.encryptForStorage(null, "x")).isEqualTo("x");
  }

  @Test
  void legacyPlaintext_readBackUnchanged() {
    // 접두사 없는 기존 평문 데이터는 복호화 대상에서 제외되어 raw 유지 (마이그레이션 안전).
    assertThat(OtherTextCrypto.decryptForDisplay("NE", "아아아")).isEqualTo("아아아");
    assertThat(OtherTextCrypto.decryptForDisplay("NE", "John")).isEqualTo("John");
    assertThat(OtherTextCrypto.decryptForDisplay("CU", "010-3333-3333")).isEqualTo("010-3333-3333");
    assertThat(OtherTextCrypto.decryptForDisplay("EM", "ex@e.com")).isEqualTo("ex@e.com");
    assertThat(OtherTextCrypto.decryptForDisplay("AD", "(03054) 서울 종로구 청와대로 73 1"))
        .isEqualTo("(03054) 서울 종로구 청와대로 73 1");
  }
}
