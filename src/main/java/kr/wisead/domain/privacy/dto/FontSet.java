package kr.wisead.domain.privacy.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.pdfbox.pdmodel.font.PDFont;

/**
 * PDF 폰트 세트
 */
@Getter
@AllArgsConstructor
public class FontSet {
    private final PDFont regularFont;
    private final PDFont boldFont;
    private final PDFont checkboxFont;

    public FontSet(PDFont regularFont, PDFont boldFont) {
        this.regularFont = regularFont;
        this.boldFont = boldFont;
        this.checkboxFont = boldFont;
    }
}
