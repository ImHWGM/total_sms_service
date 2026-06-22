package kr.wisead.domain.survey.service;

import kr.wisead.common.util.CommonUtils;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.survey.entity.OtherType;

/**
 * 설문 "기타" 답변(OTHER_TEXT) PII 유형별 저장/표시 암복호화.
 *
 * <p>NE/AD/CU는 전체 AES256+Base64({@value #ENC_PREFIX} 접두사), EM은 로컬파트(아이디)만 암호화하고 "@도메인"은 평문 유지,
 * SA는 평문, SO는 {@link SurveyService}가 RSA 키패드 흐름으로 별도 암호화(여기서는 무접두 하위호환 복호화만).
 *
 * <p>마이그레이션 안전: 접두사 없는 기존 평문 데이터는 복호화하지 않고 raw 그대로 반환한다.
 */
public final class OtherTextCrypto {

  /** 신규 암호화 OTHER_TEXT 식별용 접두사. 기존 평문 데이터와 구분해 안전한 복호화를 보장한다. */
  static final String ENC_PREFIX = "PII:";

  private OtherTextCrypto() {}

  /** 평문 → 저장값. SO는 caller가 별도 암호화하므로 여기서는 NE/AD/CU/EM만 처리한다. */
  public static String encryptForStorage(OtherType type, String plain) {
    if (CommonUtils.isNullOrEmpty(plain) || type == null) {
      return plain;
    }
    switch (type) {
      case NE:
      case AD:
      case CU:
        return ENC_PREFIX + enc(plain);
      case EM:
        int at = plain.lastIndexOf('@');
        return at <= 0
            ? ENC_PREFIX + enc(plain)
            : ENC_PREFIX + enc(plain.substring(0, at)) + plain.substring(at);
      default:
        return plain; // SA(평문), SO(별도 처리)
    }
  }

  /** 저장값 → 표시용 평문. 접두사 없는 기존 평문이거나 복호화 실패 시 raw 유지. */
  public static String decryptForDisplay(OtherType type, String stored) {
    if (CommonUtils.isNullOrEmpty(stored) || type == null) {
      return stored;
    }
    switch (type) {
      case SO:
        return dec(stored, stored); // 기존 무접두 더블 Base64 (하위호환)
      case NE:
      case AD:
      case CU:
        return stored.startsWith(ENC_PREFIX) ? dec(strip(stored), stored) : stored;
      case EM:
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
