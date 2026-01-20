package kr.wisead.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 단축 URL 유틸리티
 */
@Slf4j
public class ShortUrlUtils {

    private static final String SHORTEN_URL = "https://shorten.epopkon.com/api/shorten";

    /**
     * URL 단축
     *
     * @param longUrl 원본 URL
     * @return 단축된 URL (실패 시 원본 URL 반환)
     */
    public static String urlShortener(String longUrl) {
        log.debug("URL 단축 요청 - Long URL: {}", longUrl);

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String requestJson = "{\"originalUrl\": \"" + longUrl + "\"}";
            HttpEntity<String> entity = new HttpEntity<>(requestJson, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    UriComponentsBuilder.fromHttpUrl(SHORTEN_URL).toUriString(),
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode rootNode = mapper.readTree(response.getBody());
                String shortUrl = rootNode.path("shortenedUrl").asText();
                log.debug("URL 단축 성공 - Short URL: {}", shortUrl);
                return shortUrl;
            }
        } catch (Exception e) {
            log.warn("URL 단축 실패, 원본 URL 사용 - error: {}", e.getMessage());
            return longUrl;
        }

        return longUrl;
    }

    /**
     * 텍스트 내 설문 URL을 모두 단축 URL로 변환
     *
     * @param text 원본 텍스트
     * @param baseUrl 설문 서비스 기본 URL (예: https://twisead.epopkon.com) - 사용되지 않음, 제네릭 패턴 사용
     * @return URL이 단축된 텍스트
     */
    public static String shortenUrlsInText(String text, String baseUrl) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        try {
            // 제네릭 URL 패턴: https://로 시작하고 /auth/ 경로가 포함된 URL
            // 예: https://twisead.epopkon.com/auth/qrcode/이벤트코드/유저키
            //     https://wisead.kr/auth/이벤트코드/유저키
            String regex = "(https?://[^\\s]+/auth/(?:qrcode/)?[A-Za-z0-9]+(?:/[A-Za-z0-9]+)?)(?![A-Za-z0-9/])";
            Pattern urlPattern = Pattern.compile(regex);
            Matcher matcher = urlPattern.matcher(text);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String longUrl = matcher.group(1);
                String shortUrl = urlShortener(longUrl);
                log.debug("URL 단축: {} -> {}", longUrl, shortUrl);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(shortUrl));
            }
            matcher.appendTail(sb);

            return sb.toString();
        } catch (Exception e) {
            log.warn("텍스트 내 URL 단축 실패 - error: {}", e.getMessage());
            return text;
        }
    }

    /**
     * 텍스트 내 설문 URL을 모두 단축 URL로 변환 (baseUrl 없는 버전)
     *
     * @param text 원본 텍스트
     * @return URL이 단축된 텍스트
     */
    public static String shortenUrlsInText(String text) {
        return shortenUrlsInText(text, null);
    }

    /**
     * URL 단축 (urlShortener의 alias)
     *
     * @param longUrl 원본 URL
     * @return 단축된 URL (실패 시 원본 URL 반환)
     */
    public static String shortenUrl(String longUrl) {
        return urlShortener(longUrl);
    }

    /**
     * 텍스트 내 QR코드 URL을 모두 단축 URL로 변환
     * /qrcode/ 패턴 포함 URL을 단축
     *
     * @param text 원본 텍스트
     * @return URL이 단축된 텍스트
     */
    public static String shortenQrUrlsInText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        try {
            // QR코드 URL 패턴: https://로 시작하고 /qrcode/ 경로가 포함된 URL
            String regex = "(https?://[^\\s]+/qrcode/[A-Za-z0-9]+(?:/[A-Za-z0-9]+)?)(?![A-Za-z0-9/])";
            Pattern urlPattern = Pattern.compile(regex);
            Matcher matcher = urlPattern.matcher(text);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String longUrl = matcher.group(1);
                String shortUrl = urlShortener(longUrl);
                log.debug("QR URL 단축: {} -> {}", longUrl, shortUrl);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(shortUrl));
            }
            matcher.appendTail(sb);

            return sb.toString();
        } catch (Exception e) {
            log.warn("텍스트 내 QR URL 단축 실패 - error: {}", e.getMessage());
            return text;
        }
    }
}