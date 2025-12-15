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
     */
    @PostMapping("/survey/question")
    public ApiResponse<FileUploadResponse> uploadSurveyQuestionImg(
            @RequestParam("eventSeq") int eventSeq,
            @RequestParam("questionSeq") int questionSeq,
            @RequestBody String base64Image) {
        String filePath = fileStorageService.storeSurveyQuestionImg(base64Image, eventSeq, questionSeq);
        return ApiResponse.success(FileUploadResponse.success(filePath));
    }

    /**
     * 설문 항목 이미지 업로드 (Base64)
     */
    @PostMapping("/survey/item")
    public ApiResponse<FileUploadResponse> uploadSurveyItemImg(
            @RequestParam("eventSeq") int eventSeq,
            @RequestParam("questionSeq") int questionSeq,
            @RequestParam("order") int order,
            @RequestBody String base64Image) {
        String filePath = fileStorageService.storeSurveyItemImg(base64Image, eventSeq, questionSeq, order);
        return ApiResponse.success(FileUploadResponse.success(filePath));
    }

    /**
     * 설문 설명 이미지 업로드
     */
    @PostMapping("/survey/desc")
    public ApiResponse<FileUploadResponse> uploadSurveyDescImg(
            @RequestParam("eventSeq") int eventSeq,
            @RequestParam("file") MultipartFile file) {
        String filePath = fileStorageService.storeSurveyDescImg(file, eventSeq);
        FileUploadResponse response = FileUploadResponse.success(filePath);
        response.setOriginalFileName(file.getOriginalFilename());
        response.setFileSize(file.getSize());
        return ApiResponse.success(response);
    }

    /**
     * 설문 종료 이미지 업로드
     */
    @PostMapping("/survey/end")
    public ApiResponse<FileUploadResponse> uploadSurveyEndImg(
            @RequestParam("eventSeq") int eventSeq,
            @RequestParam("file") MultipartFile file) {
        String filePath = fileStorageService.storeSurveyEndImg(file, eventSeq);
        FileUploadResponse response = FileUploadResponse.success(filePath);
        response.setOriginalFileName(file.getOriginalFilename());
        response.setFileSize(file.getSize());
        return ApiResponse.success(response);
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
