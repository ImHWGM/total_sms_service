package kr.wisead.domain.privacy.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.pdfbox.pdmodel.PDPageContentStream;

/**
 * PDF 페이지 상태 (다중 페이지 처리용)
 */
@Getter
@AllArgsConstructor
public class PageState {
    private final PDPageContentStream contentStream;
    private final float yPosition;
}
