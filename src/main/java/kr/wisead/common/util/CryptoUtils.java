package kr.wisead.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;

/** 암호화 유틸리티 클래스 - AES 128/256 암호화/복호화 - SHA-256/512 해싱 - Base64 인코딩/디코딩 - HMAC-SHA256/512 */
@Slf4j
public class CryptoUtils {

  // 암호화 키 (기존 프로젝트와 동일한 키 사용)
  private static final String KEY = "EPOPKONSCRACHA!EVENTEPOPKONENMADA@";
  // 128bit (16자리)
  private static final String KEY_128 = KEY.substring(0, 128 / 8);
  // 256bit (32자리)
  private static final String KEY_256 = KEY.substring(0, 256 / 8);

  private static final Pattern B64 = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");

  private CryptoUtils() {
    // 유틸리티 클래스
  }

  /** AES 128 암호화 */
  public static String encryptAES128(String str) {
    if (CommonUtils.isNullOrEmpty(str)) {
      return "";
    }
    try {
      byte[] keyData = KEY_128.getBytes(StandardCharsets.UTF_8);
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(
          Cipher.ENCRYPT_MODE, new SecretKeySpec(keyData, "AES"), new IvParameterSpec(keyData));

      byte[] encrypted = cipher.doFinal(str.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(encrypted);
    } catch (Exception e) {
      log.error("AES128 암호화 실패", e);
      return "";
    }
  }

  /** AES 128 복호화 (실패 시 원본 데이터 반환) */
  public static String decryptAES128(String str) {
    if (CommonUtils.isNullOrEmpty(str)) {
      return "";
    }
    String trimmed = str.trim();
    if (trimmed.length() < 4 || trimmed.length() % 4 != 0 || !isBase64(trimmed)) {
      return str;
    }
    try {
      byte[] keyData = KEY_128.getBytes(StandardCharsets.UTF_8);
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(
          Cipher.DECRYPT_MODE, new SecretKeySpec(keyData, "AES"), new IvParameterSpec(keyData));

      byte[] decoded = Base64.getDecoder().decode(trimmed);
      byte[] decrypted = cipher.doFinal(decoded);
      return new String(decrypted, StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.debug("AES128 복호화 실패, 원본 데이터 반환: {}", e.getMessage());
      return str;
    }
  }

  /** AES 256 암호화 */
  public static String encryptAES256(String str) {
    if (CommonUtils.isNullOrEmpty(str)) {
      return "";
    }
    try {
      byte[] key256Data = KEY_256.getBytes(StandardCharsets.UTF_8);
      byte[] key128Data = KEY_128.getBytes(StandardCharsets.UTF_8);

      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(key256Data, "AES"),
          new IvParameterSpec(key128Data));

      byte[] encrypted = cipher.doFinal(str.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(encrypted);
    } catch (Exception e) {
      log.error("AES256 암호화 실패", e);
      return "";
    }
  }

  /** AES 256 복호화 (실패 시 원본 데이터 반환) */
  public static String decryptAES256(String str) {
    if (CommonUtils.isNullOrEmpty(str)) {
      return "";
    }
    String trimmed = str.trim();
    if (trimmed.length() < 4 || trimmed.length() % 4 != 0 || !isBase64(trimmed)) {
      return str;
    }
    try {
      byte[] key256Data = KEY_256.getBytes(StandardCharsets.UTF_8);
      byte[] key128Data = KEY_128.getBytes(StandardCharsets.UTF_8);

      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(key256Data, "AES"),
          new IvParameterSpec(key128Data));

      byte[] decoded = Base64.getDecoder().decode(trimmed);
      byte[] decrypted = cipher.doFinal(decoded);
      return new String(decrypted, StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.debug("AES256 복호화 실패, 원본 데이터 반환: {}", e.getMessage());
      return str;
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
