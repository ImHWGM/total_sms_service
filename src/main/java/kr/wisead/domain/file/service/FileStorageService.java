package kr.wisead.domain.file.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/** 파일 저장 서비스 */
@Slf4j
@Service
public class FileStorageService {

  @Value("${upload.dir.mmsfile:./uploads/mmsfile}")
  private String uploadMmsPath;

  @Value("${api.base.url:}")
  private String apiBaseUrl;

  @Value("${upload.dir.businessRegistration:./uploads/bizreg}")
  private String uploadBizRegPath;

  @Value("${survey.img.dir:./uploads/survey}")
  private String surveyImgFilePath;

  @Value("${survey.img.url:http://localhost:8100/files/survey}")
  private String surveyImgUrlPrefix;

  @Value("${upload.dir.template.img:./uploads/template}")
  private String templateImgPath;

  // 허용된 이미지 확장자
  private static final List<String> ALLOWED_IMAGE_EXTENSIONS =
      Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp");

  // 허용된 문서 확장자
  private static final List<String> ALLOWED_DOC_EXTENSIONS =
      Arrays.asList("pdf", "doc", "docx", "xls", "xlsx", "hwp");

  // 설문 응답 파일 허용 확장자 (이미지 + 문서 + 압축)
  private static final List<String> ALLOWED_SURVEY_ANSWER_EXTENSIONS =
      Arrays.asList(
          "jpg", "jpeg", "png", "gif", "bmp", "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
          "hwp", "zip", "alz", "7z");

  /** MMS 파일 저장 */
  public String storeMmsFile(MultipartFile file) {
    validateFile(file);
    validateImageExtension(file);

    String fileName = generateRandomFileName(file.getOriginalFilename());
    String directoryName = getEncryptedDirectoryName();
    Path targetLocation = Paths.get(uploadMmsPath, directoryName).toAbsolutePath().normalize();

    return saveFile(file, targetLocation, fileName);
  }

  /** MMS 파일 저장 (상대 경로 반환) */
  public String storeMmsFileRelative(MultipartFile file) {
    validateFile(file);
    validateImageExtension(file);

    String fileName = generateRandomFileName(file.getOriginalFilename());
    String directoryName = getEncryptedDirectoryName();
    Path targetLocation = Paths.get(uploadMmsPath, directoryName).toAbsolutePath().normalize();

    saveFile(file, targetLocation, fileName);
    return directoryName + "/" + fileName;
  }

  /** MMS 이미지 저장 (광고문자용) */
  public String storeMmsImage(MultipartFile file) {
    return storeMmsFileRelative(file);
  }

  /** 사업자등록증 파일 저장 */
  public String storeBizRegFile(MultipartFile file) {
    validateFile(file);
    validateFileNameLength(file, 126);

    String originalFileName = file.getOriginalFilename();
    String fileName = UUID.randomUUID() + "_" + originalFileName;
    Path targetLocation = Paths.get(uploadBizRegPath).toAbsolutePath().normalize();

    return saveFile(file, targetLocation, fileName);
  }

  /**
   * 설문 문항 이미지 저장
   *
   * @param directoryId 이벤트 시퀀스 또는 temp ID (예: "123" 또는 "temp_abc123")
   * @return 웹 접근 가능한 URL (예: https://api.example.com/files/survey/181/1.png)
   */
  public String storeSurveyQuestionImg(MultipartFile file, String directoryId, int questionSeq) {
    validateFile(file);
    validateImageExtension(file);

    String extension = getExtension(file.getOriginalFilename());
    Path targetLocation = Paths.get(surveyImgFilePath, directoryId).toAbsolutePath().normalize();

    String fileName = questionSeq + extension;
    saveFile(file, targetLocation, fileName);
    return buildSurveyImageUrl(directoryId, fileName);
  }

  /**
   * 설문 항목 이미지 저장
   *
   * @param directoryId 이벤트 시퀀스 또는 temp ID (예: "123" 또는 "temp_abc123")
   * @return 웹 접근 가능한 URL (예: https://api.example.com/files/survey/181/1_1.png)
   */
  public String storeSurveyItemImg(
      MultipartFile file, String directoryId, int questionSeq, int order) {
    validateFile(file);
    validateImageExtension(file);

    String extension = getExtension(file.getOriginalFilename());
    Path targetLocation = Paths.get(surveyImgFilePath, directoryId).toAbsolutePath().normalize();

    String fileName = questionSeq + "_" + order + extension;
    saveFile(file, targetLocation, fileName);
    return buildSurveyImageUrl(directoryId, fileName);
  }

  /**
   * 설문 설명 이미지 저장
   *
   * @param directoryId 이벤트 시퀀스 또는 temp ID (예: "123" 또는 "temp_abc123")
   * @return 웹 접근 가능한 URL (예: https://api.example.com/files/survey/181/Desc.png)
   */
  public String storeSurveyDescImg(MultipartFile file, String directoryId) {
    validateFile(file);
    validateImageExtension(file);

    String extension = getExtension(file.getOriginalFilename());
    Path targetLocation = Paths.get(surveyImgFilePath, directoryId).toAbsolutePath().normalize();

    String fileName = "Desc" + extension;
    saveFile(file, targetLocation, fileName);
    return buildSurveyImageUrl(directoryId, fileName);
  }

  /**
   * 설문 종료 이미지 저장
   *
   * @param directoryId 이벤트 시퀀스 또는 temp ID (예: "123" 또는 "temp_abc123")
   * @return 웹 접근 가능한 URL (예: https://api.example.com/files/survey/181/End.png)
   */
  public String storeSurveyEndImg(MultipartFile file, String directoryId) {
    validateFile(file);
    validateImageExtension(file);

    String extension = getExtension(file.getOriginalFilename());
    Path targetLocation = Paths.get(surveyImgFilePath, directoryId).toAbsolutePath().normalize();

    String fileName = "End" + extension;
    saveFile(file, targetLocation, fileName);
    return buildSurveyImageUrl(directoryId, fileName);
  }

  /**
   * 설문 응답 파일 저장 (이미지 + 문서 + 압축 파일 허용)
   *
   * @param file 업로드 파일
   * @param eventSeq 설문 이벤트 시퀀스
   * @param questionSeq 문항 시퀀스
   * @return 웹 접근 가능한 URL (예: /survey/181/answers/3/20260210_143052_a1b2c3.pdf)
   */
  public String storeSurveyAnswerFile(MultipartFile file, int eventSeq, int questionSeq) {
    validateFile(file);
    validateSurveyAnswerExtension(file);

    String extension = getExtension(file.getOriginalFilename());
    String timestamp =
        java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    String uuid = UUID.randomUUID().toString().substring(0, 6);
    String fileName = timestamp + "_" + uuid + extension;

    Path targetLocation =
        Paths.get(
                surveyImgFilePath, String.valueOf(eventSeq), "answers", String.valueOf(questionSeq))
            .toAbsolutePath()
            .normalize();

    saveFile(file, targetLocation, fileName);
    return "/survey/" + eventSeq + "/answers/" + questionSeq + "/" + fileName;
  }

  /**
   * 설문 이미지 웹 URL 생성
   *
   * @param directoryId 이벤트 시퀀스 또는 temp ID
   * @param fileName 파일명
   * @return 웹 접근 가능한 상대 경로 (예: /survey/181/Desc.png)
   */
  private String buildSurveyImageUrl(String directoryId, String fileName) {
    return "/survey/" + directoryId + "/" + fileName;
  }

  /**
   * 설문 임시 디렉토리를 이벤트 시퀀스 디렉토리로 이동 - 신규 이벤트 생성 후 호출하여 temp 파일들을 eventSeq 폴더로 이동
   *
   * @param tempId 임시 ID (temp_ 접두사 제외)
   * @param eventSeq 생성된 이벤트 시퀀스
   * @return 이동 성공 여부
   */
  public boolean moveSurveyTempToEvent(String tempId, int eventSeq) {
    if (tempId == null || tempId.isBlank()) {
      return false;
    }

    String tempDirName = "temp_" + tempId;
    Path tempDir = Paths.get(surveyImgFilePath, tempDirName).toAbsolutePath().normalize();
    Path eventDir =
        Paths.get(surveyImgFilePath, String.valueOf(eventSeq)).toAbsolutePath().normalize();

    if (!Files.exists(tempDir)) {
      log.warn("임시 디렉토리가 존재하지 않음: {}", tempDir);
      return false;
    }

    try {
      // 이벤트 디렉토리가 이미 존재하면 파일들을 복사
      if (Files.exists(eventDir)) {
        // 기존 디렉토리가 있으면 파일만 복사
        Files.walk(tempDir)
            .filter(Files::isRegularFile)
            .forEach(
                source -> {
                  try {
                    Path dest = eventDir.resolve(tempDir.relativize(source));
                    Files.createDirectories(dest.getParent());
                    Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                  } catch (IOException e) {
                    log.error("파일 복사 실패: {} -> {}", source, eventDir, e);
                  }
                });
        // temp 디렉토리 삭제
        deleteDirectory(tempDir);
      } else {
        // 디렉토리 이름 변경 (이동)
        Files.move(tempDir, eventDir, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }

      log.info("설문 이미지 디렉토리 이동 완료: {} -> {}", tempDirName, eventSeq);
      return true;
    } catch (IOException e) {
      log.error("설문 이미지 디렉토리 이동 실패: {} -> {}", tempDirName, eventSeq, e);
      return false;
    }
  }

  /**
   * 단일 설문 이미지 파일을 다른 이벤트 폴더로 복사 (불러오기 시 사용)
   *
   * @param sourceEventSeq 원본 이벤트 번호
   * @param fileName 복사할 파일명
   * @param targetEventSeq 대상 이벤트 번호
   * @return 복사 성공 여부
   */
  public boolean copySurveyImageFile(int sourceEventSeq, String fileName, int targetEventSeq) {
    if (fileName == null || fileName.isBlank()) {
      return false;
    }
    // 경로 탈출 차단: fileName은 단일 파일명이어야 함
    if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
      log.warn("유효하지 않은 파일명(경로 구분자 포함): {}", fileName);
      return false;
    }
    Path baseDir = Paths.get(surveyImgFilePath).toAbsolutePath().normalize();
    Path source =
        Paths.get(surveyImgFilePath, String.valueOf(sourceEventSeq), fileName)
            .toAbsolutePath()
            .normalize();
    Path target =
        Paths.get(surveyImgFilePath, String.valueOf(targetEventSeq), fileName)
            .toAbsolutePath()
            .normalize();
    if (!source.startsWith(baseDir) || !target.startsWith(baseDir)) {
      log.warn("base 디렉토리를 벗어난 경로 차단 - source: {}, target: {}", source, target);
      return false;
    }

    if (!Files.exists(source)) {
      log.warn("원본 설문 이미지 파일이 존재하지 않음: {}", source);
      return false;
    }
    try {
      Files.createDirectories(target.getParent());
      Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
      log.info("설문 이미지 파일 복사 완료: {} -> {}", source, target);
      return true;
    } catch (IOException e) {
      log.error("설문 이미지 파일 복사 실패: {} -> {}", source, target, e);
      return false;
    }
  }

  /** 디렉토리 삭제 (하위 파일 포함) */
  private void deleteDirectory(Path directory) throws IOException {
    if (Files.exists(directory)) {
      Files.walk(directory)
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.delete(path);
                } catch (IOException e) {
                  log.error("파일 삭제 실패: {}", path, e);
                }
              });
    }
  }

  /** 템플릿 이미지 저장 */
  public String storeTemplateImage(MultipartFile file, int userSeq) {
    validateFile(file);
    validateImageExtension(file);

    String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
    String directoryName = String.valueOf(userSeq);
    Path targetLocation = Paths.get(templateImgPath, directoryName).toAbsolutePath().normalize();

    String fileName = UUID.randomUUID() + "." + extension;
    saveFile(file, targetLocation, fileName);

    return directoryName + "/" + fileName;
  }

  /** 템플릿 이미지 삭제 */
  public void deleteTemplateImage(String imagePath) {
    if (imagePath == null || imagePath.isEmpty()) {
      return;
    }

    try {
      Path filePath = Paths.get(templateImgPath, imagePath).toAbsolutePath().normalize();
      if (Files.exists(filePath)) {
        Files.delete(filePath);
        log.info("템플릿 이미지 삭제 완료: {}", filePath);
      }
    } catch (IOException e) {
      log.error("템플릿 이미지 삭제 실패: {}", imagePath, e);
    }
  }

  /** 파일 삭제 */
  public void deleteFile(String filePath) {
    if (filePath == null || filePath.isEmpty()) {
      return;
    }

    try {
      Path path = Paths.get(filePath).toAbsolutePath().normalize();
      if (Files.exists(path)) {
        Files.delete(path);
        log.info("파일 삭제 완료: {}", path);
      }
    } catch (IOException e) {
      log.error("파일 삭제 실패: {}", filePath, e);
    }
  }

  /**
   * MMS 파일 URL 정규화
   *
   * <p>상대경로나 잘못된 URL을 올바른 전체 URL로 변환합니다.
   *
   * <p>입력 예시:
   *
   * <ul>
   *   <li>"MjAyNjAx/xxx.png" → "https://api.base.url/mmsfile/MjAyNjAx/xxx.png"
   *   <li>"/mmsfile/MjAyNjAx/xxx.png" → "https://api.base.url/mmsfile/MjAyNjAx/xxx.png"
   *   <li>"https://api.base.url/MjAyNjAx/xxx.png" → "https://api.base.url/mmsfile/MjAyNjAx/xxx.png"
   *   <li>"https://api.base.url/mmsfile/MjAyNjAx/xxx.png" → 그대로 반환
   * </ul>
   *
   * @param fileLoc 파일 경로 또는 URL
   * @return 정규화된 전체 URL (입력이 null 또는 빈 문자열이면 null 반환)
   */
  public String normalizeMmsFileUrl(String fileLoc) {
    if (fileLoc == null || fileLoc.isBlank()) {
      return null;
    }

    String trimmed = fileLoc.trim();

    // 이미 올바른 URL인 경우 (baseUrl/mmsfile/ 포함)
    if (trimmed.contains("/mmsfile/")) {
      // 전체 URL이면 그대로 반환
      if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed;
      }
      // 상대 경로 (/mmsfile/xxx)면 baseUrl 추가
      return apiBaseUrl + trimmed;
    }

    // baseUrl이 포함되어 있지만 /mmsfile/이 빠진 경우
    // 예: https://twisead-api.epopkon.com/MjAyNjAx/xxx.png
    if (trimmed.startsWith(apiBaseUrl)) {
      String pathPart = trimmed.substring(apiBaseUrl.length());
      if (!pathPart.startsWith("/")) {
        pathPart = "/" + pathPart;
      }
      return apiBaseUrl + "/mmsfile" + pathPart;
    }

    // 상대 경로만 있는 경우
    // 예: MjAyNjAx/xxx.png 또는 /MjAyNjAx/xxx.png
    String relativePath = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    return apiBaseUrl + "/mmsfile" + relativePath;
  }

  // ==================== Private Methods ====================

  private String saveFile(MultipartFile file, Path targetLocation, String fileName) {
    try {
      if (!Files.exists(targetLocation)) {
        Files.createDirectories(targetLocation);
      }

      Path destinationFile = targetLocation.resolve(fileName);
      Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

      log.info("파일 저장 완료: {}", destinationFile);
      return destinationFile.toString();
    } catch (IOException e) {
      log.error("파일 저장 실패", e);
      throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "파일 저장에 실패했습니다.");
    }
  }

  private String saveBytes(byte[] bytes, Path targetLocation, String fileName) {
    try {
      if (!Files.exists(targetLocation)) {
        Files.createDirectories(targetLocation);
      }

      Path destinationFile = targetLocation.resolve(fileName);
      Files.write(destinationFile, bytes);

      log.info("파일 저장 완료: {}", destinationFile);
      return destinationFile.toString();
    } catch (IOException e) {
      log.error("파일 저장 실패", e);
      throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "파일 저장에 실패했습니다.");
    }
  }

  private void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "파일이 비어있습니다.");
    }
  }

  private void validateFileNameLength(MultipartFile file, int maxLength) {
    String originalFileName = file.getOriginalFilename();
    if (originalFileName != null && originalFileName.length() > maxLength) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "파일명이 너무 깁니다.");
    }
  }

  private void validateImageExtension(MultipartFile file) {
    String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
    if (extension == null || !ALLOWED_IMAGE_EXTENSIONS.contains(extension.toLowerCase())) {
      throw new BusinessException(
          ErrorCode.INVALID_FILE_TYPE,
          "허용되지 않은 이미지 형식입니다. (허용: " + String.join(", ", ALLOWED_IMAGE_EXTENSIONS) + ")");
    }
  }

  private void validateSurveyAnswerExtension(MultipartFile file) {
    String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
    if (extension == null || !ALLOWED_SURVEY_ANSWER_EXTENSIONS.contains(extension.toLowerCase())) {
      throw new BusinessException(
          ErrorCode.INVALID_FILE_TYPE,
          "허용되지 않은 파일 형식입니다. (허용: " + String.join(", ", ALLOWED_SURVEY_ANSWER_EXTENSIONS) + ")");
    }
  }

  private String generateRandomFileName(String originalFileName) {
    String extension = StringUtils.getFilenameExtension(originalFileName);
    String randomFileName = UUID.randomUUID().toString();
    return extension != null ? randomFileName + "." + extension : randomFileName;
  }

  private String getEncryptedDirectoryName() {
    LocalDate now = LocalDate.now();
    String yearMonth = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
    byte[] encodedBytes = Base64.getUrlEncoder().encode(yearMonth.getBytes());
    return new String(encodedBytes);
  }

  private String getExtension(String fileName) {
    if (fileName == null) return "";
    int dotIndex = fileName.lastIndexOf('.');
    return dotIndex >= 0 ? fileName.substring(dotIndex) : "";
  }

  private byte[] decodeBase64Image(String base64Image) {
    if (base64Image == null || !base64Image.contains("base64,")) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 Base64 이미지 형식입니다.");
    }

    String[] parts = base64Image.split(",");
    if (parts.length != 2) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "예상치 못한 Base64 이미지 형식입니다.");
    }

    String base64Data = parts[1].trim();
    try {
      return Base64.getMimeDecoder().decode(base64Data);
    } catch (IllegalArgumentException e) {
      log.error("Base64 이미지 디코딩 실패", e);
      throw new BusinessException(ErrorCode.INVALID_INPUT, "Base64 이미지 디코딩에 실패했습니다.");
    }
  }

  private String getExtensionFromBase64(String base64Image) {
    String[] parts = base64Image.split(",");
    String metadata = parts[0];

    int imageIndex = metadata.indexOf("image/");
    int semicolonIndex = metadata.indexOf(";", imageIndex);

    if (imageIndex != -1 && semicolonIndex != -1) {
      String mimeType = metadata.substring(imageIndex + "image/".length(), semicolonIndex);
      if (mimeType.equalsIgnoreCase("jpeg")) {
        return ".jpg";
      }
      return "." + mimeType;
    }
    throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "지원하지 않는 이미지 MIME 타입입니다.");
  }
}
