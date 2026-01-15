package kr.wisead.domain.file.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 파일 저장 서비스
 */
@Slf4j
@Service
public class FileStorageService {

    @Value("${upload.dir.mmsfile:./uploads/mmsfile}")
    private String uploadMmsPath;

    @Value("${upload.dir.businessRegistration:./uploads/bizreg}")
    private String uploadBizRegPath;

    @Value("${survey.img.dir:./uploads/survey}")
    private String surveyImgFilePath;

    @Value("${upload.dir.template.img:./uploads/template}")
    private String templateImgPath;

    // 허용된 이미지 확장자
    private static final List<String> ALLOWED_IMAGE_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp"
    );

    // 허용된 문서 확장자
    private static final List<String> ALLOWED_DOC_EXTENSIONS = Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "hwp"
    );

    /**
     * MMS 파일 저장
     */
    public String storeMmsFile(MultipartFile file) {
        validateFile(file);
        validateImageExtension(file);

        String fileName = generateRandomFileName(file.getOriginalFilename());
        String directoryName = getEncryptedDirectoryName();
        Path targetLocation = Paths.get(uploadMmsPath, directoryName).toAbsolutePath().normalize();

        return saveFile(file, targetLocation, fileName);
    }

    /**
     * MMS 파일 저장 (상대 경로 반환)
     */
    public String storeMmsFileRelative(MultipartFile file) {
        validateFile(file);
        validateImageExtension(file);

        String fileName = generateRandomFileName(file.getOriginalFilename());
        String directoryName = getEncryptedDirectoryName();
        Path targetLocation = Paths.get(uploadMmsPath, directoryName).toAbsolutePath().normalize();

        saveFile(file, targetLocation, fileName);
        return directoryName + "/" + fileName;
    }

    /**
     * MMS 이미지 저장 (광고문자용)
     */
    public String storeMmsImage(MultipartFile file) {
        return storeMmsFileRelative(file);
    }

    /**
     * 사업자등록증 파일 저장
     */
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
     * @param directoryId 이벤트 시퀀스 또는 temp ID (예: "123" 또는 "temp_abc123")
     */
    public String storeSurveyQuestionImg(MultipartFile file, String directoryId, int questionSeq) {
        validateFile(file);
        validateImageExtension(file);

        String extension = getExtension(file.getOriginalFilename());
        Path targetLocation = Paths.get(surveyImgFilePath, directoryId).toAbsolutePath().normalize();

        String fileName = questionSeq + extension;
        return saveFile(file, targetLocation, fileName);
    }

    /**
     * 설문 항목 이미지 저장
     * @param directoryId 이벤트 시퀀스 또는 temp ID (예: "123" 또는 "temp_abc123")
     */
    public String storeSurveyItemImg(MultipartFile file, String directoryId, int questionSeq, int order) {
        validateFile(file);
        validateImageExtension(file);

        String extension = getExtension(file.getOriginalFilename());
        Path targetLocation = Paths.get(surveyImgFilePath, directoryId).toAbsolutePath().normalize();

        String fileName = questionSeq + "_" + order + extension;
        return saveFile(file, targetLocation, fileName);
    }

    /**
     * 설문 설명 이미지 저장
     * @param directoryId 이벤트 시퀀스 또는 temp ID (예: "123" 또는 "temp_abc123")
     */
    public String storeSurveyDescImg(MultipartFile file, String directoryId) {
        validateFile(file);
        validateImageExtension(file);

        String extension = getExtension(file.getOriginalFilename());
        Path targetLocation = Paths.get(surveyImgFilePath, directoryId).toAbsolutePath().normalize();

        String fileName = "Desc" + extension;
        return saveFile(file, targetLocation, fileName);
    }

    /**
     * 설문 종료 이미지 저장
     * @param directoryId 이벤트 시퀀스 또는 temp ID (예: "123" 또는 "temp_abc123")
     */
    public String storeSurveyEndImg(MultipartFile file, String directoryId) {
        validateFile(file);
        validateImageExtension(file);

        String extension = getExtension(file.getOriginalFilename());
        Path targetLocation = Paths.get(surveyImgFilePath, directoryId).toAbsolutePath().normalize();

        String fileName = "End" + extension;
        return saveFile(file, targetLocation, fileName);
    }

    /**
     * 설문 임시 디렉토리를 이벤트 시퀀스 디렉토리로 이동
     * - 신규 이벤트 생성 후 호출하여 temp 파일들을 eventSeq 폴더로 이동
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
        Path eventDir = Paths.get(surveyImgFilePath, String.valueOf(eventSeq)).toAbsolutePath().normalize();

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
                        .forEach(source -> {
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
     * 디렉토리 삭제 (하위 파일 포함)
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.error("파일 삭제 실패: {}", path, e);
                        }
                    });
        }
    }

    /**
     * 템플릿 이미지 저장
     */
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

    /**
     * 템플릿 이미지 삭제
     */
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

    /**
     * 파일 삭제
     */
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
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE,
                    "허용되지 않은 이미지 형식입니다. (허용: " + String.join(", ", ALLOWED_IMAGE_EXTENSIONS) + ")");
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
