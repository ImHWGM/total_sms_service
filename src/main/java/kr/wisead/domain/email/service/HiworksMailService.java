package kr.wisead.domain.email.service;

import kr.wisead.domain.email.dto.EmailRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Hiworks 메일 API 서비스
 */
@Slf4j
@Service
public class HiworksMailService {

    private final RestTemplate restTemplate;
    private final String officeToken;
    private final String userId;
    private final String apiUrl;

    public HiworksMailService(
            @Value("${mail.hiworks.id:service}") String userId,
            @Value("${mail.hiworks.office-token:}") String officeToken,
            @Value("${mail.hiworks.url:https://api.hiworks.com}") String apiUrl) {
        this.restTemplate = new RestTemplate();
        this.userId = userId;
        this.officeToken = officeToken;
        this.apiUrl = apiUrl + "/office/v2/webmail/sendMail";
    }

    /**
     * 이메일 발송
     */
    public HiworksResponse send(EmailRequest request) {
        log.info("Hiworks API로 이메일 발송: to={}", request.getTo());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(officeToken);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("to", request.getTo());
        body.add("user_id", userId);
        body.add("subject", request.getSubject());
        body.add("content", request.getContent());
        body.add("save_sent_mail", request.isSaveSentMail() ? "Y" : "N");

        if (request.getCc() != null && !request.getCc().isEmpty()) {
            body.add("cc", request.getCc());
        }
        if (request.getBcc() != null && !request.getBcc().isEmpty()) {
            body.add("bcc", request.getBcc());
        }

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<HiworksResponse> resp = restTemplate.postForEntity(apiUrl, entity, HiworksResponse.class);

            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                log.error("Hiworks API 오류: HTTP {}", resp.getStatusCode());
                throw new RuntimeException("Hiworks API 오류: " + resp.getStatusCode());
            }

            HiworksResponse response = resp.getBody();
            log.info("Hiworks API 응답: code={}, message={}", response.getCode(), response.getMessage());

            if (!"SUC".equals(response.getCode())) {
                log.error("Hiworks 이메일 발송 실패: {}", response.getMessage());
                throw new RuntimeException("이메일 발송 실패: " + response.getMessage());
            }

            log.info("이메일 발송 성공: to={}", request.getTo());
            return response;

        } catch (Exception e) {
            log.error("Hiworks API 호출 실패", e);
            throw new RuntimeException("이메일 발송에 실패했습니다.", e);
        }
    }

    @Data
    public static class HiworksResponse {
        private String code;
        private String message;
        private Result result;

        @Data
        public static class Result {
            private List<String> successList;
            private List<String> dupList;
            private List<String> wrongList;
        }
    }
}
