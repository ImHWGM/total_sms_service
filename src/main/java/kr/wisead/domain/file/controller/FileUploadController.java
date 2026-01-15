package kr.wisead.domain.file.controller;

import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.file.dto.FileUploadResponse;
import kr.wisead.domain.file.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 업로드 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileStorageService fileStorageService;

    /**
     * MMS 이미지 업로드
     */
    @PostMapping("/mms")
    public ApiResponse<FileUploadResponse> uploadMmsFile(
            @RequestParam("file") MultipartFile file) {
        String relativePath = fileStorageService.storeMmsFileRelative(file);
        FileUploadResponse response = FileUploadResponse.builder()
                .success(true)
                .message("MMS 파일 업로드가 완료되었습니다.")
                .relativePath(relativePath)
                .originalFileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .build();
        return ApiResponse.success(response);
    }

    /**
     * 사업자등록증 업로드
     */
    @PostMapping("/bizreg")
    public ApiResponse<FileUploadResponse> uploadBizRegFile(
            @RequestParam("file") MultipartFile file) {
        String filePath = fileStorageService.storeBizRegFile(file);
        FileUploadResponse response = FileUploadResponse.success(filePath);
        response.setOriginalFileName(file.getOriginalFilename());
        response.setFileSize(file.getSize());
        return ApiResponse.success(response);
    }

    /**
     * 설문 문항 이미지 업로드 (Base64)
     * - eventSeq가 있으면 해당 이벤트 폴더에 저장
     * - eventSeq가 없으면 tempId 폴더에 저장 (신규 이벤트 생성용)
     */
    @PostMapping("/survey/question")
    public ApiResponse<FileUploadResponse> uploadSurveyQuestionImg(
            @RequestParam(value = "eventSeq", required = false) Integer eventSeq,
            @RequestParam(value = "tempId", required = false) String tempId,
            @RequestParam("questionSeq") int questionSeq,
            @RequestBody String base64Image) {
        String directoryId = resolveDirectoryId(eventSeq, tempId);
        String filePath = fileStorageService.storeSurveyQuestionImg(base64Image, directoryId, questionSeq);
        return ApiResponse.success(FileUploadResponse.success(filePath));
    }

    /**
     * 설문 항목 이미지 업로드 (Base64)
     * - eventSeq가 있으면 해당 이벤트 폴더에 저장
     * - eventSeq가 없으면 tempId 폴더에 저장 (신규 이벤트 생성용)
     */
    @PostMapping("/survey/item")
    public ApiResponse<FileUploadResponse> uploadSurveyItemImg(
            @RequestParam(value = "eventSeq", required = false) Integer eventSeq,
            @RequestParam(value = "tempId", required = false) String tempId,
            @RequestParam("questionSeq") int questionSeq,
            @RequestParam("order") int order,
            @RequestBody String base64Image) {
        String directoryId = resolveDirectoryId(eventSeq, tempId);
        String filePath = fileStorageService.storeSurveyItemImg(base64Image, directoryId, questionSeq, order);
        return ApiResponse.success(FileUploadResponse.success(filePath));
    }

    /**
     * 설문 설명 이미지 업로드
     * - eventSeq가 있으면 해당 이벤트 폴더에 저장
     * - eventSeq가 없으면 tempId 폴더에 저장 (신규 이벤트 생성용)
     */
    @PostMapping("/survey/desc")
    public ApiResponse<FileUploadResponse> uploadSurveyDescImg(
            @RequestParam(value = "eventSeq", required = false) Integer eventSeq,
            @RequestParam(value = "tempId", required = false) String tempId,
            @RequestParam("file") MultipartFile file) {
        String directoryId = resolveDirectoryId(eventSeq, tempId);
        String filePath = fileStorageService.storeSurveyDescImg(file, directoryId);
        FileUploadResponse response = FileUploadResponse.success(filePath);
        response.setOriginalFileName(file.getOriginalFilename());
        response.setFileSize(file.getSize());
        return ApiResponse.success(response);
    }

    /**
     * 설문 종료 이미지 업로드
     * - eventSeq가 있으면 해당 이벤트 폴더에 저장
     * - eventSeq가 없으면 tempId 폴더에 저장 (신규 이벤트 생성용)
     */
    @PostMapping("/survey/end")
    public ApiResponse<FileUploadResponse> uploadSurveyEndImg(
            @RequestParam(value = "eventSeq", required = false) Integer eventSeq,
            @RequestParam(value = "tempId", required = false) String tempId,
            @RequestParam("file") MultipartFile file) {
        String directoryId = resolveDirectoryId(eventSeq, tempId);
        String filePath = fileStorageService.storeSurveyEndImg(file, directoryId);
        FileUploadResponse response = FileUploadResponse.success(filePath);
        response.setOriginalFileName(file.getOriginalFilename());
        response.setFileSize(file.getSize());
        return ApiResponse.success(response);
    }

    /**
     * 디렉토리 ID 결정
     * - eventSeq가 있으면 eventSeq 사용
     * - eventSeq가 없으면 tempId 사용
     * - 둘 다 없으면 UUID 생성
     */
    private String resolveDirectoryId(Integer eventSeq, String tempId) {
        if (eventSeq != null && eventSeq > 0) {
            return String.valueOf(eventSeq);
        }
        if (tempId != null && !tempId.isBlank()) {
            return "temp_" + tempId;
        }
        return "temp_" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 설문 임시 이미지를 이벤트 디렉토리로 이동
     * - 신규 이벤트 생성 후 호출
     */
    @PostMapping("/survey/move-temp")
    public ApiResponse<Boolean> moveSurveyTempFiles(
            @RequestParam("tempId") String tempId,
            @RequestParam("eventSeq") int eventSeq) {
        boolean success = fileStorageService.moveSurveyTempToEvent(tempId, eventSeq);
        if (success) {
            return ApiResponse.success(true);
        } else {
            return ApiResponse.error("FILE_MOVE_ERROR", "이미지 파일 이동에 실패했습니다.");
        }
    }

    /**
     * 템플릿 이미지 업로드
     */
    @PostMapping("/template")
    public ApiResponse<FileUploadResponse> uploadTemplateImage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("file") MultipartFile file) {
        int userSeq = Integer.parseInt(userDetails.getUsername());
        String relativePath = fileStorageService.storeTemplateImage(file, userSeq);
        FileUploadResponse response = FileUploadResponse.builder()
                .success(true)
                .message("템플릿 이미지 업로드가 완료되었습니다.")
                .relativePath(relativePath)
                .originalFileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .build();
        return ApiResponse.success(response);
    }

    /**
     * 템플릿 이미지 삭제
     */
    @DeleteMapping("/template")
    public ApiResponse<Void> deleteTemplateImage(@RequestParam("path") String imagePath) {
        fileStorageService.deleteTemplateImage(imagePath);
        return ApiResponse.success(null);
    }
}
