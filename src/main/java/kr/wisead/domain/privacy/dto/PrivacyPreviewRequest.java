package kr.wisead.domain.privacy.dto;

import lombok.Data;

/**
 * 개인정보제공동의서 미리보기 요청 DTO
 */
@Data
public class PrivacyPreviewRequest {
    private String title;
    private String content;
}
