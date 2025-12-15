package kr.wisead.domain.file.dto;

import lombok.*;

/**
 * 파일 업로드 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileUploadResponse {

    private boolean success;            // 성공 여부
    private String message;             // 응답 메시지
    private String filePath;            // 파일 경로 (절대 경로)
    private String relativePath;        // 상대 경로
    private String fileName;            // 파일명
    private String originalFileName;    // 원본 파일명
    private long fileSize;              // 파일 크기

    public static FileUploadResponse success(String filePath) {
        return FileUploadResponse.builder()
                .success(true)
                .message("파일 업로드가 완료되었습니다.")
                .filePath(filePath)
                .build();
    }

    public static FileUploadResponse success(String filePath, String relativePath) {
        return FileUploadResponse.builder()
                .success(true)
                .message("파일 업로드가 완료되었습니다.")
                .filePath(filePath)
                .relativePath(relativePath)
                .build();
    }

    public static FileUploadResponse fail(String message) {
        return FileUploadResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}
