package kr.wisead.domain.file.controller;

import jakarta.annotation.PostConstruct;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 파일 다운로드/서빙 Controller */
@Slf4j
@RestController
public class FileDownloadController {

  private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
  private static final String DEFAULT_IMAGE_TYPE = "image/png";

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

  /** MMS 파일 다운로드/서빙 */
  @GetMapping({"/files/mmsfile/{folder}/{fileName:.+}", "/mmsfile/{folder}/{fileName:.+}"})
  public ResponseEntity<Resource> serveMmsFile(
      @PathVariable String folder, @PathVariable String fileName) throws Exception {
    return serveFile(mmsFileStorageLocation, folder, fileName);
  }

  /** 설문 이미지 다운로드/서빙 - /survey/** : DB에 저장된 경로로 직접 접근 (비로그인 허용) - /files/survey/** : 기존 호환성 유지 */
  @GetMapping({"/survey/{folder}/{fileName:.+}", "/files/survey/{folder}/{fileName:.+}"})
  public ResponseEntity<Resource> serveSurveyFile(
      @PathVariable String folder, @PathVariable String fileName) throws Exception {
    return serveFile(surveyFileStorageLocation, folder, fileName);
  }

  /** 템플릿 이미지 다운로드/서빙 */
  @GetMapping({"/files/template/{folder}/{fileName:.+}", "/template/{folder}/{fileName:.+}"})
  public ResponseEntity<Resource> serveTemplateFile(
      @PathVariable String folder, @PathVariable String fileName) throws Exception {
    return serveFile(templateFileStorageLocation, folder, fileName);
  }

  /** 사업자등록증 다운로드/서빙 */
  @GetMapping({"/files/bizreg/{fileName:.+}", "/bizreg/{fileName:.+}"})
  public ResponseEntity<Resource> serveBizRegFile(@PathVariable String fileName) throws Exception {
    Path filePath = validateAndResolvePath(bizRegFileStorageLocation, fileName);
    return buildFileResponse(filePath, fileName, "attachment", DEFAULT_CONTENT_TYPE);
  }

  /**
   * QR 코드 이미지 서빙 - /qrcode/** : 직접 접근 - /files/qrcode/** : 기존 호환성 유지 - /survey/qrcode/** : 레거시 호환성
   */
  @GetMapping({
    "/qrcode/{fileName:.+}",
    "/files/qrcode/{fileName:.+}",
    "/survey/qrcode/{fileName:.+}"
  })
  public ResponseEntity<Resource> serveQrCodeFile(@PathVariable String fileName) throws Exception {
    Path filePath = validateAndResolvePath(qrFileStorageLocation, "qrcode", fileName);
    return buildFileResponse(filePath, fileName, "inline", DEFAULT_IMAGE_TYPE);
  }

  /** 공통 파일 서빙 메서드 (폴더 포함) */
  private ResponseEntity<Resource> serveFile(Path storageLocation, String folder, String fileName)
      throws Exception {
    Path filePath = validateAndResolvePath(storageLocation, folder, fileName);
    String contentType = getContentType(filePath, DEFAULT_CONTENT_TYPE);
    String disposition = contentType.startsWith("image/") ? "inline" : "attachment";
    return buildFileResponse(filePath, fileName, disposition, contentType);
  }

  /**
   * 경로가 허용된 디렉토리 내에 있는지 검증 (Path Traversal 공격 방지)
   *
   * @param baseLocation 허용된 기본 디렉토리
   * @param pathSegments 경로 세그먼트들 (folder, fileName 등)
   * @return 검증된 절대 경로
   * @throws SecurityException 경로가 기본 디렉토리를 벗어나는 경우
   */
  private Path validateAndResolvePath(Path baseLocation, String... pathSegments) {
    Path resolvedPath = baseLocation.resolve(Paths.get("", pathSegments)).normalize();

    if (!resolvedPath.startsWith(baseLocation)) {
      log.warn("Path Traversal 시도 감지: baseLocation={}, segments={}", baseLocation, pathSegments);
      throw new SecurityException("잘못된 파일 경로: 허용되지 않은 접근");
    }

    return resolvedPath;
  }

  /** 파일 응답 빌드 공통 메서드 */
  private ResponseEntity<Resource> buildFileResponse(
      Path filePath, String fileName, String disposition, String defaultContentType)
      throws Exception {
    Resource resource = new UrlResource(filePath.toUri());

    if (!resource.exists()) {
      throw new RuntimeException("파일을 찾을 수 없습니다: " + fileName);
    }

    String contentType = getContentType(filePath, defaultContentType);
    String encodedFileName = encodeFileName(resource.getFilename(), fileName);

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .header(
            HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + encodedFileName + "\"")
        .body(resource);
  }

  /** Content-Type 결정 */
  private String getContentType(Path filePath, String defaultType) throws Exception {
    String contentType = Files.probeContentType(filePath);
    return contentType != null ? contentType : defaultType;
  }

  /** 파일명 URL 인코딩 */
  private String encodeFileName(String resourceFileName, String fallbackName) {
    String nameToEncode = resourceFileName != null ? resourceFileName : fallbackName;
    return URLEncoder.encode(nameToEncode, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
