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
  public static String toAbsoluteUrl(String path, String baseUrl) {
    if (path == null || path.isEmpty()) {
      return path;
    }
    if (path.startsWith("http://") || path.startsWith("https://")) {
      return path;
    }
    return baseUrl + path;
  }
}
