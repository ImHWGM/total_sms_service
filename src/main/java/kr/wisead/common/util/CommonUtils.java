package kr.wisead.common.util;

import java.io.File;
import java.io.FileReader;
import java.security.SecureRandom;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 공통 유틸리티 클래스 */
public class CommonUtils {

  private CommonUtils() {
    // 유틸리티 클래스 인스턴스화 방지
  }

  /** Null 여부 반환 (Object) */
  public static boolean isNullOrEmpty(Object s) {
    return s == null;
  }

  /** Null 또는 빈 문자열 여부 반환 (String) */
  public static boolean isNullOrEmpty(String s) {
    return s == null || s.trim().isEmpty();
  }

  /** Null일 경우 기본값 반환 (int) */
  public static int replaceNull(Object origin, int iDefault) {
    return origin == null ? iDefault : replaceNull(origin.toString(), iDefault);
  }

  /** Null일 경우 기본값 반환 (String from Object) */
  public static String replaceNull(Object origin, String strDefault) {
    return origin == null ? strDefault : replaceNull(origin.toString(), strDefault);
  }

  /** Null 또는 빈 문자열일 경우 기본값 반환 */
  public static String replaceNull(String strOrg, String strDefault) {
    return isNullOrEmpty(strOrg) ? strDefault : strOrg;
  }

  /** Null일 경우 기본값 반환 (int from String) */
  public static int replaceNull(String strOrg, int iDefault) {
    return !isNullOrEmpty(strOrg) && isNumeric(strOrg) ? Integer.parseInt(strOrg) : iDefault;
  }

  /** Null일 경우 기본값 반환 (Long from String) */
  public static Long replaceNull(String strOrg, Long lDefault) {
    return !isNullOrEmpty(strOrg) && isNumeric(strOrg) ? Long.parseLong(strOrg) : lDefault;
  }

  /** 숫자 여부 체크 */
  public static boolean isNumeric(String s) {
    if (isNullOrEmpty(s)) return false;
    char[] chars = s.toCharArray();
    for (char c : chars) {
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  /** 영문(대,소문자) 체크 */
  public static boolean isAlphabet(String s) {
    if (isNullOrEmpty(s)) return false;
    char[] chars = s.toCharArray();
    for (char c : chars) {
      if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) {
        return false;
      }
    }
    return true;
  }

  /** 파일 내용을 읽어서 String으로 반환 */
  public static String readTextFile(String path) {
    StringBuilder sb = new StringBuilder();
    if (isExistsFile(path)) {
      try (FileReader reader = new FileReader(path)) {
        int temp;
        while ((temp = reader.read()) != -1) {
          sb.append((char) temp);
        }
      } catch (Exception e) {
        // ignore
      }
    }
    return sb.toString();
  }

  /** 파일 존재 여부 확인 */
  public static boolean isExistsFile(String path) {
    if (isNullOrEmpty(path)) {
      return false;
    }
    File file = new File(path);
    return file.exists();
  }

  /** 태그 변환 (XSS, SQL Injection 방지) */
  public static String stripTags(String strOrg) {
    String str = replaceNull(strOrg, "");

    str = eregiReplace(" union", " u nion", str);
    str = eregiReplace(" select", " s elect", str);
    str = eregiReplace(" update", " u pdate", str);
    str = eregiReplace(" delete", " d elete", str);
    str = eregiReplace(" insert", " i nsert", str);
    str = eregiReplace(" drop", " d rop", str);

    str = eregiReplace("<(/?)([^<>]*)?>", "&lt;$1$2&gt;", str);
    str = eregiReplace("(javascript|vbscript)+", "_$1_", str);
    str = eregiReplace("(<(/?)(script|style)([^<>]*)>)+", "_$1_", str);
    str = eregiReplace("'", "`", str);
    str = eregiReplace("=", "&#61", str);

    str =
        eregiReplace(
            "(onreset|onmove|onstop|onpaste|onstart|onresize|onrowexit|onselect|onmousewheel|"
                + "ondataavailable|onafterprint|onafterupdate|onmousedown|onbeforeactivate|onbeforecopy|"
                + "ondatasetchanged|onbeforedeactivate|onbeforeeditfocus|onbeforepaste|onbeforeprint|"
                + "onbeforeunload|onbeforeupdate|onpropertychange|ondatasetcomplete|oncellchange|"
                + "onlayoutcomplete|onmousemove|oncontextmenu|oncontrolselect|onreadystatechange|"
                + "onselectionchange|onrowsinserted|onactivae|oncopy|oncut|onclick|onchange|onbeforecut|"
                + "ondblclick|ondeactivate|ondrag|ondragend|ondragenter|ondragleave|ondragover|ondragstart|"
                + "ondrop|onerror|onerrorupdate|onfilterchange|onfinish|onfocus|onresizestart|onunload|"
                + "onselectstart|onfocusin|onfocusout|onhelp|onkeydown|onkeypress|onkeyup|onrowsdelete|"
                + "onload|onlosecapture|onbounce|onmouseenter|onmouseleave|onbefore|onmouseout|onmouseover|"
                + "onmouseup|onresizeend|onabort|onmoveend|onmovestart|onrowenter|onsubmit|onblur)+",
            " ",
            str);
    return str;
  }

  /** 정규식을 이용해 치환된 문자열 반환 (대소문자 무시) */
  public static String eregiReplace(String pattern, String strReplace, String strOrg) {
    String str = replaceNull(strOrg, "");
    Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
    Matcher m = p.matcher(str);
    return m.replaceAll(strReplace);
  }

  /** 전화번호 마스킹 */
  public static String maskingPhone(String strOrg) {
    String str = replaceNull(strOrg, "");
    if (!isNullOrEmpty(str)) {
      if (!str.contains("-")) {
        if (strOrg.length() == 11) {
          str = str.substring(0, 3) + "-****-" + str.substring(7, 11);
        } else {
          str = str.substring(0, 3) + "-***-" + str.substring(6, 10);
        }
      } else {
        String[] phoneArray = str.split("-");
        str = phoneArray[0] + "-****-" + phoneArray[2];
      }
    }
    return str;
  }

  /** 이름 마스킹 */
  public static String maskingName(String strOrg) {
    String str = replaceNull(strOrg, "");
    if (!isNullOrEmpty(str)) {
      if (str.length() == 2) {
        str = str.substring(0, 1) + "*";
      } else {
        StringBuilder sb = new StringBuilder(str.substring(0, 1));
        sb.append("*".repeat(str.length() - 2));
        sb.append(str.substring(str.length() - 1));
        str = sb.toString();
      }
    }
    return str;
  }

  /** 이메일 마스킹 */
  public static String maskingEmail(String strOrg) {
    String str = replaceNull(strOrg, "");
    if (!isNullOrEmpty(str) && str.contains("@")) {
      String[] emailArray = str.split("@");
      if (emailArray[0].length() > 4) {
        str = emailArray[0].substring(0, emailArray[0].length() - 4) + "****@";
      } else {
        str = emailArray[0].substring(0, 1) + "****@";
      }
      str += emailArray[1];
    }
    return str;
  }

  /** 이메일 마스킹 (앞 3자 유지 + *** + @도메인). 로그/2FA 응답용 short 포맷. */
  public static String maskingEmailShort(String email) {
    if (email == null || !email.contains("@")) {
      return "***";
    }
    int atIndex = email.indexOf("@");
    if (atIndex <= 3) {
      return email.charAt(0) + "***" + email.substring(atIndex);
    }
    return email.substring(0, 3) + "***" + email.substring(atIndex);
  }

  /** 랜덤 문자열 생성 (대문자 + 숫자) */
  public static String randomString(int length) {
    StringBuilder sb = new StringBuilder();
    Random r = new SecureRandom();
    String randomChars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    for (int i = 0; i < length; i++) {
      sb.append(randomChars.charAt(r.nextInt(randomChars.length())));
    }
    return sb.toString();
  }

  /** 랜덤 코드 생성 (대소문자 + 숫자) */
  public static String randomCode(int length) {
    StringBuilder sb = new StringBuilder();
    Random r = new SecureRandom();
    String randomChars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    for (int i = 0; i < length; i++) {
      sb.append(randomChars.charAt(r.nextInt(randomChars.length())));
    }
    return sb.toString();
  }

  /** 랜덤 숫자 생성 (10자리 이하일 경우 중복 없음) */
  public static String numberGenerator(int length) {
    Random random = new SecureRandom();
    StringBuilder result = new StringBuilder();

    for (int i = 0; i < length; i++) {
      String randomNumber = Integer.toString(random.nextInt(10));
      if (length > 10) {
        result.append(randomNumber);
      } else {
        if (!result.toString().contains(randomNumber)) {
          result.append(randomNumber);
        } else {
          i--;
        }
      }
    }
    return result.toString();
  }

  /** 외국인 ID 마스킹 (Passport No./TIN) */
  public static String maskingForeignId(String strOrg) {
    String str = replaceNull(strOrg, "");
    if (isNullOrEmpty(str)) return str;
    if (str.length() <= 4) {
      return str.substring(0, 1) + "***";
    }
    return str.substring(0, 2) + "*".repeat(str.length() - 4) + str.substring(str.length() - 2);
  }
}
