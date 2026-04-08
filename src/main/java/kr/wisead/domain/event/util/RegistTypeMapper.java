package kr.wisead.domain.event.util;

/** RSVP 영문 코드 ↔ 한국어 registType 단일 변환 헬퍼 (단일 진실 원천). */
public final class RegistTypeMapper {

  public static final String PRE_REGISTERED = "사전등록";
  public static final String ONSITE_REGISTERED = "현장등록";
  public static final String ABSENT = "불참석";

  private RegistTypeMapper() {}

  /** 영문 코드를 한국어 registType으로 변환. */
  public static String toKorean(String englishCode) {
    if (englishCode == null) {
      throw new IllegalArgumentException("response가 null입니다.");
    }
    return switch (englishCode) {
      case "attend" -> PRE_REGISTERED;
      case "absent" -> ABSENT;
      default -> throw new IllegalArgumentException("알 수 없는 응답값: " + englishCode);
    };
  }

  /** 사전등록 또는 현장등록이면 attend로 간주. */
  public static boolean isAttendType(String registType) {
    return PRE_REGISTERED.equals(registType) || ONSITE_REGISTERED.equals(registType);
  }
}
