package kr.wisead.domain.file.controller;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 파일 다운로드/서빙 Controller
 */
@Slf4j
@RestController
@RequestMapping("/files")
public class FileDownloadController {

    @Value("${upload.dir.mmsfile:./uploads/mmsfile}")
    private String mmsFilePath;

    @Value("${survey.img.dir:./uploads/survey}")
    private String surveyImgFilePath;

    @Value("${upload.dir.template.img:./uploads/template}")
    private String templateImgPath;

    @Value("${upload.dir.businessRegistration:./uploads/bizreg}")
    private String bizRegPath;

    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    private Path mmsFileStorageLocation;
    private Path surveyFileStorageLocation;
    private Path templateFileStorageLocation;
    private Path bizRegFileStorageLocation;
    private Path qrFileStorageLocation;

    @PostConstruct
    public void init() {
        try {
            mmsFileStorageLocation = Paths.get(mmsFilePath).toAbsolutePath().normalize();
            surveyFileStorageLocation = Paths.get(surveyImgFilePath).toAbsolutePath().normalize();
            templateFileStorageLocation = Paths.get(templateImgPath).toAbsolutePath().normalize();
            bizRegFileStorageLocation = Paths.get(bizRegPath).toAbsolutePath().normalize();
            qrFileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();

            createDirectoryIfNotExists(mmsFileStorageLocation);
            createDirectoryIfNotExists(surveyFileStorageLocation);
            createDirectoryIfNotExists(templateFileStorageLocation);
            createDirectoryIfNotExists(bizRegFileStorageLocation);
            createDirectoryIfNotExists(qrFileStorageLocation.resolve("qrcode"));
        } catch (Exception ex) {
            throw new RuntimeException("업로드 디렉토리를 생성할 수 없습니다.", ex);
        }
    }

    private void createDirectoryIfNotExists(Path path) throws Exception {
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            log.info("디렉토리 생성: {}", path);
        }
    }

    /**
     * MMS 파일 다운로드/서빙
     */
    @GetMapping("/mmsfile/{folder}/{fileName:.+}")
    public ResponseEntity<Resource> serveMmsFile(
            @PathVariable String folder,
            @PathVariable String fileName) throws Exception {
        return serveFile(mmsFileStorageLocation, folder, fileName);
    }

    /**
     * 설문 이미지 다운로드/서빙
     */
    @GetMapping("/survey/{folder}/{fileName:.+}")
    public ResponseEntity<Resource> serveSurveyFile(
            @PathVariable String folder,
            @PathVariable String fileName) throws Exception {
        return serveFile(surveyFileStorageLocation, folder, fileName);
    }

    /**
     * 템플릿 이미지 다운로드/서빙
     */
    @GetMapping("/template/{folder}/{fileName:.+}")
    public ResponseEntity<Resource> serveTemplateFile(
            @PathVariable String folder,
            @PathVariable String fileName) throws Exception {
        return serveFile(templateFileStorageLocation, folder, fileName);
    }

    /**
     * 사업자등록증 다운로드/서빙
     */
    @GetMapping("/bizreg/{fileName:.+}")
    public ResponseEntity<Resource> serveBizRegFile(
            @PathVariable String fileName) throws Exception {
        Path filePath = bizRegFileStorageLocation.resolve(fileName).normalize();
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists()) {
            throw new RuntimeException("파일을 찾을 수 없습니다: " + fileName);
        }

        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        String encodedFileName = URLEncoder.encode(resource.getFilename(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedFileName + "\"")
                .body(resource);
    }

    /**
     * QR 코드 이미지 서빙
     * 설문조사 QR 코드 이미지 제공
     */
    @GetMapping("/qrcode/{fileName:.+}")
    public ResponseEntity<Resource> serveQrCodeFile(
            @PathVariable String fileName) throws Exception {
        Path filePath = qrFileStorageLocation.resolve(Paths.get("qrcode", fileName)).normalize();
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists()) {
            throw new RuntimeException("파일을 찾을 수 없습니다: " + fileName);
        }

        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            contentType = "image/png";
        }

        String encodedFileName = URLEncoder.encode(resource.getFilename(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + encodedFileName + "\"")
                .body(resource);
    }

    /**
     * 공통 파일 서빙 메서드
     */
    private ResponseEntity<Resource> serveFile(Path storageLocation, String folder, String fileName) throws Exception {
        Path filePath = storageLocation.resolve(Paths.get(folder, fileName)).normalize();
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists()) {
            throw new RuntimeException("파일을 찾을 수 없습니다: " + fileName);
        }

        String contentType = Files.probeContentType(filePath);
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        // 이미지 파일인 경우 inline으로 표시
        String disposition = "attachment";
        if (contentType.startsWith("image/")) {
            disposition = "inline";
        }

        String encodedFileName = URLEncoder.encode(resource.getFilename(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        disposition + "; filename=\"" + encodedFileName + "\"")
                .body(resource);
    }
}
