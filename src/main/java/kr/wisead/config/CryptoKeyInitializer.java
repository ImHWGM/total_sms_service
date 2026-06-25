package kr.wisead.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import kr.wisead.common.util.CryptoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 데이터 암호키(DEK)를 설정/환경변수에서 읽어 {@link CryptoUtils}에 주입한다.
 *
 * <ul>
 *   <li>{@code crypto.data-key} (필수): 현재 암호화 키. 운영은 Jenkins Credentials {@code ENC_DATA_KEY}(신규 키).
 *   <li>{@code crypto.data-key-legacy} (선택): 콤마 구분 레거시 키 목록. 기존 데이터 복호화용({@code
 *       ENC_DATA_KEY_LEGACY}).
 * </ul>
 *
 * <p>소스/설정 파일에 평문 키를 두지 않는다(보안점검 대응). 현재 키 미설정 시 부팅 실패(fail-fast).
 */
@Slf4j
@Component
public class CryptoKeyInitializer {

  private final String primaryKey;
  private final String legacyKeys;

  public CryptoKeyInitializer(
      @Value("${crypto.data-key:}") String primaryKey,
      @Value("${crypto.data-key-legacy:}") String legacyKeys) {
    this.primaryKey = primaryKey;
    this.legacyKeys = legacyKeys;
  }

  @PostConstruct
  void init() {
    if (!StringUtils.hasText(primaryKey)) {
      throw new IllegalStateException(
          "crypto.data-key(ENC_DATA_KEY)가 설정되지 않았습니다. 부팅을 중단합니다.");
    }
    List<String> keys = new ArrayList<>();
    keys.add(primaryKey.trim());
    if (StringUtils.hasText(legacyKeys)) {
      for (String k : legacyKeys.split(",")) {
        if (StringUtils.hasText(k)) {
          keys.add(k.trim());
        }
      }
    }
    CryptoUtils.configureKeys(keys);
    log.info("데이터 암호키 초기화 완료 (레거시 키 {}개)", keys.size() - 1);
  }
}
