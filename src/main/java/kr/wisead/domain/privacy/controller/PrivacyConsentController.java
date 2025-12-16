package kr.wisead.domain.privacy.controller;

import kr.wisead.domain.privacy.dto.PrivacyPreviewRequest;
import kr.wisead.domain.privacy.service.PrivacyConsentPdfService;
import kr.wisead.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

/**
 * 개인정보제공동의서 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/privacy-consent")
@RequiredArgsConstructor
public class PrivacyConsentController {

    private final PrivacyConsentPdfService privacyConsentPdfService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 단건 개인정보제공동의서 PDF 다운로드
     * GET /api/privacy-consent/download/user/{userSeq}/event/{eventSeq}
     */
    @GetMapping("/download/user/{userSeq}/event/{eventSeq}")
    public ResponseEntity<byte[]> downloadUserPrivacyConsent(
            @PathVariable int userSeq,
            @PathVariable int eventSeq,
            @RequestParam(value = "includeSignature", defaultValue = "true") boolean includeSignature,
            @RequestHeader("Authorization") String token) {

        String accessToken = token.replace("Bearer ", "");
        String requestUserId = jwtTokenProvider.getUserId(accessToken);

        try {
            log.info("개인정보제공동의서 PDF 다운로드 시작 - 요청자: {}, eventSeq: {}, userSeq: {}, 서명포함: {}",
                    requestUserId, eventSeq, userSeq, includeSignature);

            byte[] pdfContent = privacyConsentPdfService.generatePrivacyConsentPdf(userSeq, eventSeq, includeSignature);

            log.info("개인정보제공동의서 PDF 다운로드 성공 - 요청자: {}, eventSeq: {}, userSeq: {}, PDF 크기: {} bytes",
                    requestUserId, eventSeq, userSeq, pdfContent.length);

            String filename;
            try {
                String userName = privacyConsentPdfService.getUserNameForFilename(userSeq);
                filename = String.format("privacy_consent_%s.pdf", userName);
            } catch (Exception e) {
                filename = String.format("privacy_consent_%d.pdf", userSeq);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment",
                    URLEncoder.encode(filename, StandardCharsets.UTF_8));

            return new ResponseEntity<>(pdfContent, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            log.warn("개인정보제공동의서 다운로드 실패 - 요청자: {}, eventSeq: {}, userSeq: {}, 실패사유: {}",
                    requestUserId, eventSeq, userSeq, e.getMessage());

            String encodedMessage = Base64.getEncoder()
                    .encodeToString(e.getMessage().getBytes(StandardCharsets.UTF_8));

            if (e.getMessage().contains("개인정보가 파기된 데이터")) {
                return ResponseEntity.status(HttpStatus.GONE)
                        .header("X-Error-Message-Encoded", encodedMessage)
                        .build();
            }
            return ResponseEntity.badRequest()
                    .header("X-Error-Message-Encoded", encodedMessage)
                    .build();
        } catch (Exception e) {
            log.error("개인정보제공동의서 다운로드 오류 - 요청자: {}, eventSeq: {}, userSeq: {}",
                    requestUserId, eventSeq, userSeq, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 이벤트별 전체 개인정보제공동의서 PDF.zip 다운로드
     * GET /api/privacy-consent/download/event/{eventSeq}
     */
    @GetMapping("/download/event/{eventSeq}")
    public ResponseEntity<byte[]> downloadEventPrivacyConsentZip(
            @PathVariable int eventSeq,
            @RequestParam(value = "includeSignature", defaultValue = "true") boolean includeSignature,
            @RequestHeader("Authorization") String token) {

        String accessToken = token.replace("Bearer ", "");
        String requestUserId = jwtTokenProvider.getUserId(accessToken);

        try {
            log.info("개인정보제공동의서 ZIP 다운로드 시작 - 요청자: {}, eventSeq: {}, 서명포함: {}",
                    requestUserId, eventSeq, includeSignature);

            byte[] zipContent = privacyConsentPdfService.generatePrivacyConsentPdfZip(eventSeq, includeSignature);

            log.info("개인정보제공동의서 ZIP 다운로드 성공 - 요청자: {}, eventSeq: {}, ZIP 크기: {} bytes",
                    requestUserId, eventSeq, zipContent.length);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            String filename = String.format("privacy_consent_event_%d_%s.zip",
                    eventSeq, new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
            headers.setContentDispositionFormData("attachment",
                    URLEncoder.encode(filename, StandardCharsets.UTF_8));

            return new ResponseEntity<>(zipContent, headers, HttpStatus.OK);

        } catch (IllegalArgumentException e) {
            log.warn("개인정보제공동의서 ZIP 다운로드 실패 - 요청자: {}, eventSeq: {}, 실패사유: {}",
                    requestUserId, eventSeq, e.getMessage());
            String encodedMessage = Base64.getEncoder()
                    .encodeToString(e.getMessage().getBytes(StandardCharsets.UTF_8));
            return ResponseEntity.badRequest()
                    .header("X-Error-Message-Encoded", encodedMessage)
                    .build();
        } catch (Exception e) {
            log.error("개인정보제공동의서 ZIP 다운로드 오류 - 요청자: {}, eventSeq: {}",
                    requestUserId, eventSeq, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 개인정보취합 안내문 미리보기 PDF 생성
     * POST /api/privacy-consent/preview
     */
    @PostMapping("/preview")
    public ResponseEntity<byte[]> previewPrivacyConsent(
            @RequestBody PrivacyPreviewRequest request,
            @RequestHeader("Authorization") String token) {

        String accessToken = token.replace("Bearer ", "");
        String requestUserId = jwtTokenProvider.getUserId(accessToken);
        String previewTitle = request.getTitle() != null ? request.getTitle() : "Unknown";

        try {
            log.info("개인정보제공동의서 미리보기 생성 시작 - 요청자: {}, 제목: {}",
                    requestUserId, previewTitle);

            byte[] pdfContent = privacyConsentPdfService.generatePreviewPdf(
                    request.getTitle(),
                    request.getContent()
            );

            log.info("개인정보제공동의서 미리보기 생성 성공 - 요청자: {}, 제목: {}, PDF 크기: {} bytes",
                    requestUserId, previewTitle, pdfContent.length);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);

            String filename = String.format("privacy_consent_preview_%s.pdf",
                    new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
            headers.setContentDispositionFormData("inline",
                    URLEncoder.encode(filename, StandardCharsets.UTF_8));

            return new ResponseEntity<>(pdfContent, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("개인정보제공동의서 미리보기 생성 오류 - 요청자: {}, 제목: {}",
                    requestUserId, previewTitle, e);
            String encodedMessage = Base64.getEncoder()
                    .encodeToString("미리보기 생성 중 오류가 발생했습니다.".getBytes(StandardCharsets.UTF_8));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message-Encoded", encodedMessage)
                    .build();
        }
    }
}
