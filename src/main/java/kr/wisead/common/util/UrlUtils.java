package kr.wisead.common.util;

/** URL 관련 유틸리티 */
public final class UrlUtils {

  private UrlUtils() {}

  /**
   * 상대 경로를 절대 URL로 변환
   *
   * @param path 이미지 경로 (상대 또는 절대)
   * @param baseUrl base URL (예: https://api.example.com)
   * @return 절대 URL 또는 원본 (null/empty/이미 절대 URL인 경우)
   */
  private static final String[] KNOWN_PATH_SEGMENTS = {
    "/survey/", "/qrcode/", "/mmsfile/", "/template/", "/bizreg/"
  };

  public static String toAbsoluteUrl(String path, String baseUrl) {
    if (path == null || path.isEmpty()) {
      return path;
    }
    if (path.startsWith("http://") || path.startsWith("https://")) {
      return path;
    }
    // 레거시 절대 파일경로 처리 (예: C:/project/monkeys/upload/survey/564/Desc.jpg)
    if (path.length() > 2 && Character.isLetter(path.charAt(0)) && (path.charAt(1) == ':')) {
      path = extractRelativePath(path);
    }
    return baseUrl + path;
  }

  /**
   * 절대 파일경로에서 상대 URL 경로 추출
   *
   * @param absolutePath 절대 경로 (예: C:/project/monkeys/upload/survey/564/Desc.jpg)
   * @return 상대 경로 (예: /survey/564/Desc.jpg)
   */
  private static String extractRelativePath(String absolutePath) {
    String normalized = absolutePath.replace('\\', '/');
    for (String segment : KNOWN_PATH_SEGMENTS) {
      int idx = normalized.indexOf(segment);
      if (idx >= 0) {
        return normalized.substring(idx);
      }
    }
    return absolutePath;
  }
}
