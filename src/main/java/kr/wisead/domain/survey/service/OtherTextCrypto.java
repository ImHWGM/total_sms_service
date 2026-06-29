package kr.wisead.domain.survey.service;

import kr.wisead.common.util.CommonUtils;
import kr.wisead.common.util.CryptoUtils;

/**
 * 설문 PII 답변(주관식 ANSWER / 기타 OTHER_TEXT) 유형별 저장/표시 암복호화.
 *
 * <p>유형 키는 questionTypeDetail / OtherType.name() 문자열을 그대로 사용한다.
 *
 * <ul>
 *   <li>NE(이름)/AD(주소)/CU(연락처): 전체 AES256+Base64 + {@value #ENC_PREFIX} 접두사
 *   <li>EM(이메일): 로컬파트(아이디)만 암호화하고 "@도메인"은 평문 유지
 *   <li>SO(주민번호): {@link SurveyService} RSA 키패드 흐름에서 이미 암호화(무접두) → 저장 시 무변경, 표시 시만 복호화
 *   <li>그 외(SA 등): 평문 그대로
 * </ul>
 *
 * <p>마이그레이션 안전: 접두사 없는 기존 평문 데이터는 복호화하지 않고 raw 그대로 반환한다.
 */
public final class OtherTextCrypto {

  /** 신규 암호화 데이터 식별용 접두사. 기존 평문과 구분해 안전한 복호화를 보장한다. */
  static final String ENC_PREFIX = "PII:";

  private OtherTextCrypto() {}

  /** 평문 → 저장값. NE/AD/CU 전체 암호화, EM 로컬파트만 암호화, 그 외(SA/SO 등)는 무변경. */
  public static String encryptForStorage(String detail, String plain) {
    if (CommonUtils.isNullOrEmpty(plain) || detail == null) {
      return plain;
    }
    switch (detail) {
      case "NE":
      case "AD":
      case "CU":
        return ENC_PREFIX + enc(plain);
      case "EM":
        int at = plain.lastIndexOf('@');
        return at <= 0
            ? ENC_PREFIX + enc(plain)
            : ENC_PREFIX + enc(plain.substring(0, at)) + plain.substring(at);
      default:
        return plain;
    }
  }

  /** 저장값 → 표시 평문. 접두사 없는 기존 평문이거나 복호화 실패 시 raw 유지. */
  public static String decryptForDisplay(String detail, String stored) {
    if (CommonUtils.isNullOrEmpty(stored) || detail == null) {
      return stored;
    }
    switch (detail) {
      case "SO":
        return dec(stored, stored); // 기존 무접두 더블 Base64
      case "NE":
      case "AD":
      case "CU":
        return stored.startsWith(ENC_PREFIX) ? dec(strip(stored), stored) : stored;
      case "EM":
        if (!stored.startsWith(ENC_PREFIX)) {
          return stored;
        }
        String body = strip(stored);
        int at = body.lastIndexOf('@');
        if (at <= 0) {
          return dec(body, stored);
        }
        String local = dec(body.substring(0, at), null);
        return local == null ? stored : local + body.substring(at);
      default:
        return stored;
    }
  }

  private static String strip(String s) {
    return s.substring(ENC_PREFIX.length());
  }

  /** AES256 + Base64(2중) 암호화. 실패 시 평문 그대로 (SurveyService.encryptSensitiveValue 미러). */
  private static String enc(String plain) {
    String e = CryptoUtils.encryptAES256(plain);
    return CommonUtils.isNullOrEmpty(e) ? plain : CryptoUtils.encodeBase64(e);
  }

  /** AES256+Base64 복호화. 실패/빈 결과 시 fallback 반환. */
  private static String dec(String value, String fallback) {
    try {
      String d = CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(value));
      return CommonUtils.isNullOrEmpty(d) ? fallback : d;
    } catch (Exception e) {
      return fallback;
    }
  }
}
