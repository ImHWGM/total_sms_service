package kr.wisead.common.controller;

import java.util.Map;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.util.ShortUrlUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** URL 단축 Controller */
@Slf4j
@RestController
@RequestMapping("/api/url")
public class ShortUrlController {

  /**
   * URL 단축 POST /api/url/shorten
   *
   * @param request { "url": "https://..." }
   * @return { "originalUrl": "...", "shortenedUrl": "..." }
   */
  @PostMapping("/shorten")
  public ApiResponse<Map<String, String>> shortenUrl(@RequestBody Map<String, String> request) {
    String url = request.get("url");

    if (url == null || url.isBlank()) {
      return ApiResponse.error("INVALID_INPUT", "URL은 필수입니다.");
    }

    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      return ApiResponse.error("INVALID_INPUT", "올바른 URL 형식이 아닙니다.");
    }

    String shortenedUrl = ShortUrlUtils.shortenUrl(url);
    log.info("URL 단축 API 호출 - original: {}, shortened: {}", url, shortenedUrl);

    return ApiResponse.success(Map.of("originalUrl", url, "shortenedUrl", shortenedUrl));
  }
}
