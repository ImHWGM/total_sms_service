package kr.wisead.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** DEK 멀티키(레거시 무매직 ↔ 신규 매직) 복호화 회귀 테스트. */
class CryptoUtilsTest {

  private static final String OLD = "EPOPKONSCRACHA!EVENTEPOPKONENMADA@";
  private static final String NEW = "5W4bbo6AJFd4KC4js71dyHgrE87BUThB"; // 32자

  /** 구버전(매직 없음, OLD 키)으로 암호화된 데이터의 고정 벡터. */
  private static final String LEGACY_CIPHERTEXT = "9NAAA2Px/0lP6MyYxBEhZQ==";

  @Test
  void legacyData_decryptsWithLegacyKey_underMultiKey() {
    CryptoUtils.configureKeys(List.of(NEW, OLD));
    assertThat(CryptoUtils.decryptAES256(LEGACY_CIPHERTEXT)).isEqualTo("010-1234-5678");
  }

  @Test
  void newData_roundTrips_underMultiKey() {
    CryptoUtils.configureKeys(List.of(NEW, OLD));
    String ct = CryptoUtils.encryptAES256("홍길동 010-9999-8888");
    assertThat(ct).isNotBlank();
    assertThat(CryptoUtils.decryptAES256(ct)).isEqualTo("홍길동 010-9999-8888");
  }

  @Test
  void singleKey_roundTripsAndReadsLegacyVector() {
    CryptoUtils.configureKeys(List.of(OLD));
    String ct = CryptoUtils.encryptAES256("테스트");
    assertThat(CryptoUtils.decryptAES256(ct)).isEqualTo("테스트");
    assertThat(CryptoUtils.decryptAES256(LEGACY_CIPHERTEXT)).isEqualTo("010-1234-5678");
  }

  @Test
  void configureKeys_rejectsShortKey() {
    assertThatThrownBy(() -> CryptoUtils.configureKeys(List.of("tooShortKey")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void configureKeys_rejectsEmpty() {
    assertThatThrownBy(() -> CryptoUtils.configureKeys(List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
