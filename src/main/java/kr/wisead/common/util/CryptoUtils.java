package kr.wisead.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;

/** 암호화 유틸리티 클래스 - AES 256 암호화/복호화 - SHA-256/512 해싱 - Base64 인코딩/디코딩 - HMAC-SHA256/512 */
@Slf4j
public class CryptoUtils {

  /**
   * 데이터 암호키(DEK). keys().get(0) = 현재 키(암호화), 이후 = 레거시 키(기존 데이터 복호화용).
   * 소스/설정에 평문 키를 두지 않는다 — 운영은 ENC_DATA_KEY(신규)+ENC_DATA_KEY_LEGACY(기존) 환경변수 주입.
   */
  private static volatile List<String> dataKeys;

  /** AES-256 키 길이(바이트). DEK 문자열 앞 32바이트를 키로 사용. */
  private static final int AES256_KEY_BYTES = 32;

  /** 신규 키 암호화 데이터 식별용 매직(4바이트). 레거시(무매직)와 충돌 확률 2^-32. */
  private static final byte[] V1_MAGIC = {'W', 'A', 'K', '1'};

  /** DEK 주입(Spring 초기화/테스트). 첫 항목=암호화 키, 나머지=복호화 fallback. 각 키 최소 32자. */
  public static void configureKeys(List<String> keys) {
    if (keys == null || keys.isEmpty()) {
      throw new IllegalArgumentException("DEK가 비어 있습니다.");
    }
    List<String> validated = new ArrayList<>(keys.size());
    for (String k : keys) {
      if (k == null || k.length() < AES256_KEY_BYTES) {
        throw new IllegalArgumentException("DEK 길이는 최소 " + AES256_KEY_BYTES + "자 이상이어야 합니다.");
      }
      validated.add(k);
    }
    dataKeys = List.copyOf(validated);
    log.info("DEK 설정 완료 - 총 {}개(암호화 1 + 레거시 {})", validated.size(), validated.size() - 1);
  }

  private static List<String> keys() {
    List<String> k = dataKeys;
    if (k == null || k.isEmpty()) {
      throw new IllegalStateException(
          "DEK 미설정: CryptoUtils.configureKeys() 로 주입하세요 (Spring 부팅 시 CryptoKeyInitializer 자동 주입).");
    }
    return k;
  }

  private static byte[] keyBytes(String key) {
    return key.substring(0, AES256_KEY_BYTES).getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] ivBytes(String key) {
    return key.substring(0, 16).getBytes(StandardCharsets.UTF_8);
  }

  private static final Pattern B64 = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");

  private CryptoUtils() {
    // 유틸리티 클래스
  }

  /** AES 256 암호화 (현재 키 + 신규 포맷 매직 부착) */
  public static String encryptAES256(String str) {
    if (CommonUtils.isNullOrEmpty(str)) {
      return "";
    }
    try {
      String key = keys().get(0);
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(keyBytes(key), "AES"),
          new IvParameterSpec(ivBytes(key)));

      byte[] encrypted = cipher.doFinal(str.getBytes(StandardCharsets.UTF_8));
      byte[] tagged = new byte[V1_MAGIC.length + encrypted.length];
      System.arraycopy(V1_MAGIC, 0, tagged, 0, V1_MAGIC.length);
      System.arraycopy(encrypted, 0, tagged, V1_MAGIC.length, encrypted.length);
      return Base64.getEncoder().encodeToString(tagged);
    } catch (Exception e) {
      log.error("AES256 암호화 실패", e);
      return "";
    }
  }

  /**
   * AES 256 복호화. 신규 포맷(매직)=현재 키, 레거시(무매직)=레거시 키 우선. 실패 시 원본 반환.
   *
   * <p>매직으로 신규/레거시를 구분하므로 키 오선택으로 인한 오복호(패딩 우연 통과)가 발생하지 않는다.
   */
  public static String decryptAES256(String str) {
    if (CommonUtils.isNullOrEmpty(str)) {
      return "";
    }
    String trimmed = str.trim();
    if (trimmed.length() < 4 || trimmed.length() % 4 != 0 || !isBase64(trimmed)) {
      return str;
    }
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(trimmed);
    } catch (Exception e) {
      return str;
    }

    List<String> allKeys = keys();
    if (hasMagic(decoded)) {
      byte[] payload = new byte[decoded.length - V1_MAGIC.length];
      System.arraycopy(decoded, V1_MAGIC.length, payload, 0, payload.length);
      String result = tryDecryptAES256(payload, allKeys.get(0));
      return result != null ? result : str;
    }
    // 레거시(무매직): 레거시 키 우선 → 현재 키 순. 신규 키를 레거시 데이터에 적용하지 않아 오복호 없음.
    for (int i = 1; i < allKeys.size(); i++) {
      String result = tryDecryptAES256(decoded, allKeys.get(i));
      if (result != null) {
        return result;
      }
    }
    String result = tryDecryptAES256(decoded, allKeys.get(0));
    if (result != null) {
      return result;
    }
    log.debug("AES256 복호화 실패(모든 키), 원본 반환");
    return str;
  }

  private static boolean hasMagic(byte[] data) {
    if (data.length < V1_MAGIC.length) {
      return false;
    }
    for (int i = 0; i < V1_MAGIC.length; i++) {
      if (data[i] != V1_MAGIC[i]) {
        return false;
      }
    }
    return true;
  }

  private static String tryDecryptAES256(byte[] ciphertext, String key) {
    try {
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(keyBytes(key), "AES"),
          new IvParameterSpec(ivBytes(key)));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (Exception e) {
      return null;
    }
  }

  /** SHA-256 단방향 암호화 (Hex 출력) */
  public static String encryptSHA256(String str) {
    if (CommonUtils.isNullOrEmpty(str)) {
      return "";
    }
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
      byte[] encrypted = messageDigest.digest(str.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(encrypted);
    } catch (Exception e) {
      log.error("SHA256 암호화 실패", e);
      return "";
    }
  }

  /** SHA-512 단방향 암호화 (Base64 출력) */
  public static String encryptSHA512(String str) {
    if (CommonUtils.isNullOrEmpty(str)) {
      return "";
    }
    try {
      MessageDigest messageDigest = MessageDigest.getInstance("SHA-512");
      byte[] encrypted = messageDigest.digest(str.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(encrypted);
    } catch (Exception e) {
      log.error("SHA512 암호화 실패", e);
      return "";
    }
  }

  /** Base64 인코딩 */
  public static String encodeBase64(String str) {
    if (CommonUtils.isNullOrEmpty(str)) {
      return "";
    }
    try {
      return Base64.getEncoder().encodeToString(str.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      return "";
    }
  }

  /** Base64 디코딩 (실패 시 원본 데이터 반환) */
  public static String decodeBase64(String str) {
    if (CommonUtils.isNullOrEmpty(str)) {
      return "";
    }
    try {
      return new String(Base64.getDecoder().decode(str), StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.debug("Base64 디코딩 실패, 원본 데이터 반환: {}", e.getMessage());
      return str;
    }
  }

  /** HMAC-SHA512 */
  public static byte[] hmacSha512(String value, String key) {
    try {
      SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
      Mac mac = Mac.getInstance("HmacSHA512");
      mac.init(keySpec);
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** HMAC-SHA256 */
  public static byte[] hmacSha256(String value, String key) {
    try {
      SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(keySpec);
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** 바이트 배열을 Hex 문자열로 변환 */
  public static String asHex(byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
  }

  /** AES256 암호화된 데이터 복호화 (Base64 디코딩 후 복호화) */
  public static String getDecryptedAES256Data(String data) {
    if (data == null || data.isEmpty()) {
      return "";
    }

    String trimmed = data.trim();
    if (trimmed.length() < 4 || trimmed.length() % 4 != 0 || !isBase64(trimmed)) {
      return data;
    }

    try {
      String decoded = decodeBase64(trimmed);
      return decryptAES256(decoded);
    } catch (Exception e) {
      return data;
    }
  }

  /** 주소 정규화 (암호화된 주소 복호화) */
  public static String normalizeAddress(String raw) {
    if (raw == null) {
      return "";
    }

    raw = raw.trim();
    String[] parts = raw.split("\\s+");

    if (parts.length == 2 && isBase64(parts[0]) && isBase64(parts[1])) {
      try {
        String d1 = getDecryptedAES256Data(parts[0]);
        String d2 = getDecryptedAES256Data(parts[1]);
        return d1 + " " + d2;
      } catch (Exception e) {
        return raw;
      }
    }
    return raw;
  }

  /** 암호화된 이름 복호화 (Base64 디코딩 후 AES256 복호화, 실패 시 원본 반환) */
  public static String decryptName(String encryptedName) {
    if (encryptedName == null || encryptedName.isEmpty()) {
      return encryptedName;
    }
    try {
      return decryptAES256(decodeBase64(encryptedName));
    } catch (Exception e) {
      return encryptedName;
    }
  }

  /** Base64 형식 여부 확인 */
  private static boolean isBase64(String s) {
    return B64.matcher(s).matches();
  }
}
