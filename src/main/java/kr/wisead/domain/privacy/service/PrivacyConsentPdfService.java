package kr.wisead.domain.privacy.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.privacy.dto.FontSet;
import kr.wisead.domain.privacy.dto.PageState;
import kr.wisead.domain.survey.entity.SurveyMaster;
import kr.wisead.domain.survey.entity.SurveyUser;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import kr.wisead.mapper.primary.SurveyUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 개인정보제공동의서 PDF 생성 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivacyConsentPdfService {

    private static final String PDF_TITLE = "개인정보 수집·이용 동의서";
    private static final String CONSENT_METHOD_NOTICE = "※ 동의 방식: 온라인 설문 참여 시 체크박스를 통한 동의";
    private static final String AGREE_CHECKBOX_TEXT = "위 개인정보 수집ㆍ이용 안내를 확인하였습니다.       확인함 ☑";

    // PDF 설정 상수
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 50;
    private static final float FONT_SIZE = 11;
    private static final float TITLE_FONT_SIZE = 16;
    private static final float SMALL_FONT_SIZE = 9;
    private static final float LINE_HEIGHT = 18;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2);

    private final SurveyMasterMapper surveyMasterMapper;
    private final SurveyUserMapper surveyUserMapper;

    /**
     * 단건 개인정보제공동의서 PDF 생성
     */
    @Transactional(readOnly = true)
    public byte[] generatePrivacyConsentPdf(int userSeq, int eventSeq, boolean includeSignature) throws Exception {
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        if (!"Y".equals(event.getPrivacyPolicyYn())) {
            throw new IllegalArgumentException("개인정보제공동의를 사용하지 않는 이벤트입니다.");
        }

        SurveyUser user = surveyUserMapper.selectBySeq(userSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자 정보를 찾을 수 없습니다."));

        String decryptedName = CryptoUtils.decryptAES256(user.getUserName());
        if (decryptedName == null || decryptedName.trim().isEmpty() || "-".equals(decryptedName.trim())) {
            throw new IllegalArgumentException("개인정보가 파기된 데이터입니다. 개인정보제공동의서를 생성할 수 없습니다.");
        }

        if (user.getSubmissionDate() == null) {
            throw new IllegalArgumentException("설문 제출이 완료되지 않아 개인정보제공동의서를 생성할 수 없습니다.");
        }

        return createPdf(event, user, decryptedName, includeSignature);
    }

    /**
     * 다건 개인정보제공동의서 PDF.zip 생성
     */
    @Transactional(readOnly = true)
    public byte[] generatePrivacyConsentPdfZip(int eventSeq, boolean includeSignature) throws Exception {
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        if (!"Y".equals(event.getPrivacyPolicyYn())) {
            throw new IllegalArgumentException("개인정보제공동의를 사용하지 않는 이벤트입니다.");
        }

        List<SurveyUser> users = surveyUserMapper.selectCompletedByEventSeq(eventSeq);
        if (users == null || users.isEmpty()) {
            throw new IllegalArgumentException("참여자 정보를 찾을 수 없습니다.");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int totalUsers = users.size();
        int destroyedDataCount = 0;
        int generatedPdfCount = 0;

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (SurveyUser user : users) {
                String decryptedName = CryptoUtils.decryptAES256(user.getUserName());
                if (decryptedName == null || decryptedName.trim().isEmpty() || "-".equals(decryptedName.trim())) {
                    destroyedDataCount++;
                    continue;
                }

                try {
                    byte[] pdfContent = createPdf(event, user, decryptedName, includeSignature);
                    String fileName = String.format("개인정보제공동의서_%s.pdf", decryptedName);

                    ZipEntry entry = new ZipEntry(fileName);
                    zos.putNextEntry(entry);
                    zos.write(pdfContent);
                    zos.closeEntry();
                    generatedPdfCount++;
                } catch (Exception e) {
                    log.warn("PDF 생성 실패 - 사용자: {}, 오류: {}", user.getSeq(), e.getMessage());
                }
            }

            if (destroyedDataCount > 0) {
                String infoContent = String.format(
                        "개인정보제공동의서 다운로드 안내\n\n" +
                                "전체 참여자 수: %d명\n" +
                                "생성된 동의서: %d개\n" +
                                "개인정보 파기 데이터: %d개\n\n" +
                                "※ 개인정보 보관기간이 지나 파기된 데이터는 동의서를 생성할 수 없습니다.",
                        totalUsers, generatedPdfCount, destroyedDataCount
                );

                ZipEntry infoEntry = new ZipEntry("안내사항.txt");
                zos.putNextEntry(infoEntry);
                zos.write(infoContent.getBytes("UTF-8"));
                zos.closeEntry();
            }
        }

        if (generatedPdfCount == 0) {
            throw new IllegalArgumentException("해당 이벤트 참여자들의 개인정보가 파기되어 다운로드할 수 없습니다.");
        }

        return baos.toByteArray();
    }

    /**
     * 파일명용 사용자 이름 조회
     */
    @Transactional(readOnly = true)
    public String getUserNameForFilename(int userSeq) throws Exception {
        SurveyUser user = surveyUserMapper.selectBySeq(userSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자 정보를 찾을 수 없습니다."));

        String decryptedName = CryptoUtils.decryptAES256(user.getUserName());
        if (decryptedName == null || decryptedName.trim().isEmpty() || "-".equals(decryptedName.trim())) {
            throw new IllegalArgumentException("개인정보가 파기된 데이터입니다.");
        }

        return decryptedName;
    }

    /**
     * 미리보기용 개인정보제공동의서 PDF 생성
     */
    public byte[] generatePreviewPdf(String title, String content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PDDocument document = new PDDocument()) {
            FontSet fonts = loadFonts(document);

            PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
            document.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            float yPosition = PAGE_HEIGHT - MARGIN;

            try {
                // 제목
                float titleWidth = getStringWidth(PDF_TITLE, fonts.getBoldFont(), TITLE_FONT_SIZE);
                float titleX = (PAGE_WIDTH - titleWidth) / 2;
                contentStream.beginText();
                contentStream.setFont(fonts.getBoldFont(), TITLE_FONT_SIZE);
                contentStream.newLineAtOffset(titleX, yPosition);
                contentStream.showText(ensureSafeText(PDF_TITLE, fonts.getBoldFont()));
                contentStream.endText();
                yPosition -= LINE_HEIGHT;

                // 이벤트명
                String eventNameText = "(" + (title != null && !title.trim().isEmpty() ? title : "[이벤트명]") + ")";
                float eventNameWidth = getStringWidth(eventNameText, fonts.getRegularFont(), FONT_SIZE);
                float eventNameX = (PAGE_WIDTH - eventNameWidth) / 2;
                contentStream.beginText();
                contentStream.setFont(fonts.getRegularFont(), FONT_SIZE);
                contentStream.newLineAtOffset(eventNameX, yPosition);
                contentStream.showText(ensureSafeText(eventNameText, fonts.getRegularFont()));
                contentStream.endText();
                yPosition -= LINE_HEIGHT * 2;

                // 내용
                if (content != null && !content.isEmpty()) {
                    PageState pageState = writeTextWithWrappingAndPaging(document, contentStream,
                            fonts.getRegularFont(), FONT_SIZE, MARGIN, yPosition, LINE_HEIGHT, content,
                            CONTENT_WIDTH, PAGE_HEIGHT, PAGE_WIDTH);
                    contentStream = pageState.getContentStream();
                    yPosition = pageState.getYPosition();
                }
                yPosition -= LINE_HEIGHT;

                // 동의 확인 문구
                writeAgreeCheckboxText(contentStream, fonts, FONT_SIZE, PAGE_WIDTH, yPosition);
                yPosition -= LINE_HEIGHT;

                // 동의 방식 안내 문구
                float noticeWidth = getStringWidth(CONSENT_METHOD_NOTICE, fonts.getRegularFont(), SMALL_FONT_SIZE);
                float noticeX = (PAGE_WIDTH - noticeWidth) / 2;
                contentStream.beginText();
                contentStream.setFont(fonts.getRegularFont(), SMALL_FONT_SIZE);
                contentStream.newLineAtOffset(noticeX, yPosition);
                contentStream.showText(ensureSafeText(CONSENT_METHOD_NOTICE, fonts.getRegularFont()));
                contentStream.endText();
                yPosition -= LINE_HEIGHT * 2;

                // 날짜 및 이름
                SimpleDateFormat sdf = new SimpleDateFormat("동의일시 :   yyyy. MM. dd.      HH:mm:ss");
                String consentDate = sdf.format(new Date());
                float dateWidth = getStringWidth(consentDate, fonts.getRegularFont(), FONT_SIZE);
                float dateX = PAGE_WIDTH - MARGIN - dateWidth;
                contentStream.beginText();
                contentStream.setFont(fonts.getRegularFont(), FONT_SIZE);
                contentStream.newLineAtOffset(dateX, yPosition);
                contentStream.showText(ensureSafeText(consentDate, fonts.getRegularFont()));
                contentStream.endText();
                yPosition -= LINE_HEIGHT;

                String nameText = "동의자   :   OOO";
                float nameWidth = getStringWidth(nameText, fonts.getRegularFont(), FONT_SIZE);
                float nameX = PAGE_WIDTH - MARGIN - nameWidth;
                contentStream.beginText();
                contentStream.setFont(fonts.getRegularFont(), FONT_SIZE);
                contentStream.newLineAtOffset(nameX, yPosition);
                contentStream.showText(ensureSafeText(nameText, fonts.getRegularFont()));
                contentStream.endText();
                yPosition -= LINE_HEIGHT;

                // 미리보기 안내 문구
                String previewNotice = "※ 본 문서는 미리보기용으로 실제 서명이 아닙니다.";
                float previewNoticeWidth = getStringWidth(previewNotice, fonts.getRegularFont(), SMALL_FONT_SIZE);
                float previewNoticeX = PAGE_WIDTH - MARGIN - previewNoticeWidth;
                contentStream.beginText();
                contentStream.setFont(fonts.getRegularFont(), SMALL_FONT_SIZE);
                contentStream.setNonStrokingColor(Color.GRAY);
                contentStream.newLineAtOffset(previewNoticeX, yPosition);
                contentStream.showText(ensureSafeText(previewNotice, fonts.getRegularFont()));
                contentStream.endText();

            } finally {
                contentStream.close();
            }

            document.save(baos);
        }

        return baos.toByteArray();
    }

    // ==================== Private Methods ====================

    private byte[] createPdf(SurveyMaster event, SurveyUser user, String decryptedName, boolean includeSignature) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PDDocument document = new PDDocument()) {
            FontSet fonts = loadFonts(document);

            PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
            document.addPage(page);
            PDPageContentStream contentStream = new PDPageContentStream(document, page);

            float yPosition = PAGE_HEIGHT - MARGIN;

            try {
                // 제목 (중앙 정렬)
                float titleWidth = getStringWidth(PDF_TITLE, fonts.getBoldFont(), TITLE_FONT_SIZE);
                float titleX = (PAGE_WIDTH - titleWidth) / 2;
                contentStream.beginText();
                contentStream.setFont(fonts.getBoldFont(), TITLE_FONT_SIZE);
                contentStream.newLineAtOffset(titleX, yPosition);
                contentStream.showText(ensureSafeText(PDF_TITLE, fonts.getBoldFont()));
                contentStream.endText();
                yPosition -= LINE_HEIGHT;

                // 이벤트 정보
                String eventNameText = "(" + event.getEventName() + ")";
                float eventNameWidth = getStringWidth(eventNameText, fonts.getRegularFont(), FONT_SIZE);
                float eventNameX = (PAGE_WIDTH - eventNameWidth) / 2;
                contentStream.beginText();
                contentStream.setFont(fonts.getRegularFont(), FONT_SIZE);
                contentStream.newLineAtOffset(eventNameX, yPosition);
                contentStream.showText(ensureSafeText(eventNameText, fonts.getRegularFont()));
                contentStream.endText();
                yPosition -= LINE_HEIGHT * 2;

                // 개인정보 처리방침 내용
                String policyDesc = event.getPrivacyPolicyDesc();
                if (policyDesc != null && !policyDesc.isEmpty()) {
                    PageState pageState = writeTextWithWrappingAndPaging(document, contentStream,
                            fonts.getRegularFont(), FONT_SIZE, MARGIN, yPosition, LINE_HEIGHT, policyDesc,
                            CONTENT_WIDTH, PAGE_HEIGHT, PAGE_WIDTH);
                    contentStream = pageState.getContentStream();
                    yPosition = pageState.getYPosition();
                }
                yPosition -= LINE_HEIGHT;

                // 페이지 하단 체크
                if (yPosition < MARGIN + LINE_HEIGHT) {
                    contentStream.close();
                    PDPage newPage = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
                    document.addPage(newPage);
                    contentStream = new PDPageContentStream(document, newPage);
                    yPosition = PAGE_HEIGHT - MARGIN;
                }

                // 동의 체크박스 출력
                writeAgreeCheckboxText(contentStream, fonts, FONT_SIZE, PAGE_WIDTH, yPosition);
                yPosition -= LINE_HEIGHT;

                // 서명 없는 버전일 때 동의 방식 안내 문구
                if (!includeSignature) {
                    float noticeWidth = getStringWidth(CONSENT_METHOD_NOTICE, fonts.getRegularFont(), SMALL_FONT_SIZE);
                    float noticeX = (PAGE_WIDTH - noticeWidth) / 2;
                    contentStream.beginText();
                    contentStream.setFont(fonts.getRegularFont(), SMALL_FONT_SIZE);
                    contentStream.newLineAtOffset(noticeX, yPosition);
                    contentStream.showText(ensureSafeText(CONSENT_METHOD_NOTICE, fonts.getRegularFont()));
                    contentStream.endText();
                    yPosition -= LINE_HEIGHT * 2;
                }

                // 서명란
                SimpleDateFormat sdf = new SimpleDateFormat("동의일시 :   yyyy. MM. dd.      HH:mm:ss");
                String consentDate;
                if (user.getSurveyStartTime() != null) {
                    consentDate = sdf.format(java.sql.Timestamp.valueOf(user.getSurveyStartTime()));
                } else if (user.getSubmissionDate() != null) {
                    consentDate = sdf.format(java.sql.Timestamp.valueOf(user.getSubmissionDate()));
                } else {
                    consentDate = sdf.format(new Date());
                }

                // 페이지 하단 체크
                if (yPosition < MARGIN + LINE_HEIGHT * 3) {
                    contentStream.close();
                    PDPage newPage = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
                    document.addPage(newPage);
                    contentStream = new PDPageContentStream(document, newPage);
                    yPosition = PAGE_HEIGHT - MARGIN;
                }

                // 날짜 (오른쪽 정렬)
                float dateWidth = getStringWidth(consentDate, fonts.getRegularFont(), FONT_SIZE);
                float dateX = PAGE_WIDTH - MARGIN - dateWidth;
                contentStream.beginText();
                contentStream.setFont(fonts.getRegularFont(), FONT_SIZE);
                contentStream.newLineAtOffset(dateX, yPosition);
                contentStream.showText(ensureSafeText(consentDate, fonts.getRegularFont()));
                contentStream.endText();
                yPosition -= LINE_HEIGHT;

                // 이름
                String nameSignatureText = includeSignature ?
                        "동의자   :   " + decryptedName + "      (서명)" :
                        "동의자   :   " + decryptedName;
                float nameSignatureWidth = getStringWidth(nameSignatureText, fonts.getRegularFont(), FONT_SIZE);
                float nameSignatureX = PAGE_WIDTH - MARGIN - nameSignatureWidth;
                contentStream.beginText();
                contentStream.setFont(fonts.getRegularFont(), FONT_SIZE);
                contentStream.newLineAtOffset(nameSignatureX, yPosition);
                contentStream.showText(ensureSafeText(nameSignatureText, fonts.getRegularFont()));
                contentStream.endText();

                // 서명 이미지 오버레이 (서명 포함 옵션일 때만)
                if (includeSignature) {
                    try {
                        BufferedImage signatureImage = createSignatureImage(decryptedName);
                        ByteArrayOutputStream signatureBytes = new ByteArrayOutputStream();
                        ImageIO.write(signatureImage, "png", signatureBytes);
                        PDImageXObject pdSignature = PDImageXObject.createFromByteArray(
                                document, signatureBytes.toByteArray(), "signature");

                        boolean isKoreanName = decryptedName.matches(".*[ㄱ-ㅎㅏ-ㅣ가-힣]+.*");
                        float scaleFactor = isKoreanName ? 2.5f : 1.0f;
                        float sigHeight = FONT_SIZE * 1.0f * scaleFactor;
                        float sigWidth = (signatureImage.getWidth() * sigHeight) / signatureImage.getHeight();
                        float betweenSignChars = PAGE_WIDTH * 0.89f;
                        float sigX = betweenSignChars - (sigWidth / 2);
                        if (sigX + sigWidth > PAGE_WIDTH) sigX = PAGE_WIDTH - sigWidth;
                        if (sigX < 0) sigX = 0;
                        float sigY = yPosition - (sigHeight - FONT_SIZE) / 2;

                        contentStream.drawImage(pdSignature, sigX, sigY, sigWidth, sigHeight);
                    } catch (Exception e) {
                        log.error("서명 이미지 생성 실패", e);
                    }

                    // 서명 안내 문구
                    yPosition -= LINE_HEIGHT;
                    String signatureNotice = "※ 본 성명은 전산 처리된 정보이며 실제 서명이 아닙니다.";
                    float signatureNoticeWidth = getStringWidth(signatureNotice, fonts.getRegularFont(), SMALL_FONT_SIZE);
                    float signatureNoticeX = PAGE_WIDTH - MARGIN - signatureNoticeWidth;
                    contentStream.beginText();
                    contentStream.setFont(fonts.getRegularFont(), SMALL_FONT_SIZE);
                    contentStream.newLineAtOffset(signatureNoticeX, yPosition);
                    contentStream.showText(ensureSafeText(signatureNotice, fonts.getRegularFont()));
                    contentStream.endText();
                }

            } finally {
                contentStream.close();
            }

            document.save(baos);
        }

        return baos.toByteArray();
    }

    private FontSet loadFonts(PDDocument document) throws Exception {
        try {
            PDFont font = PDType0Font.load(document,
                    new ClassPathResource("font/NanumBarunGothic.ttf").getInputStream());
            PDFont boldFont = PDType0Font.load(document,
                    new ClassPathResource("font/NanumBarunGothicBold.ttf").getInputStream());
            return new FontSet(font, boldFont);
        } catch (Exception e) {
            log.debug("나눔바른고딕 폰트 로드 실패, 기본 폰트 사용: {}", e.getMessage());
            return new FontSet(
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                    new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
            );
        }
    }

    private float getStringWidth(String text, PDFont font, float fontSize) throws IOException {
        try {
            return font.getStringWidth(text) / 1000 * fontSize;
        } catch (Exception e) {
            String safeText = sanitizeTextForFont(text, font);
            return font.getStringWidth(safeText) / 1000 * fontSize;
        }
    }

    private String ensureSafeText(String text, PDFont font) {
        if (text == null || text.isEmpty()) return text;

        try {
            for (char c : text.toCharArray()) {
                font.encode(String.valueOf(c));
            }
            return text;
        } catch (Exception e) {
            return sanitizeTextForFont(text, font);
        }
    }

    private String sanitizeTextForFont(String text, PDFont font) {
        if (text == null || text.isEmpty()) return text;

        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            try {
                font.encode(String.valueOf(c));
                result.append(c);
            } catch (Exception e) {
                result.append(getReplacementChar(c));
            }
        }
        return result.toString();
    }

    private String getReplacementChar(char c) {
        int codePoint = (int) c;
        if (codePoint == 0x2713 || codePoint == 0x2714 || codePoint == 0x2611) return "V";
        if (codePoint == 0x25A0 || codePoint == 0x25A1 || codePoint == 0x2610) return "[ ]";
        if (codePoint == 0x203B) return "*";
        if (codePoint == 0x00B7 || codePoint == 0x2022) return "-";
        if (codePoint < 128) return String.valueOf(c);
        return "?";
    }

    private void writeAgreeCheckboxText(PDPageContentStream contentStream, FontSet fonts,
                                        float fontSize, float pageWidth, float yPosition) throws IOException {
        String textBeforeCheckbox = "위 개인정보 수집ㆍ이용 안내를 확인하였습니다.       확인함 ";
        String checkboxChar = "V";

        String safeTextBefore = ensureSafeText(textBeforeCheckbox, fonts.getBoldFont());
        float beforeWidth = getStringWidth(safeTextBefore, fonts.getBoldFont(), fontSize);
        float checkboxWidth = getStringWidth(checkboxChar, fonts.getBoldFont(), fontSize);

        float totalWidth = beforeWidth + checkboxWidth;
        float startX = (pageWidth - totalWidth) / 2;

        contentStream.beginText();
        contentStream.setFont(fonts.getBoldFont(), fontSize);
        contentStream.newLineAtOffset(startX, yPosition);
        contentStream.showText(safeTextBefore);
        contentStream.endText();

        contentStream.beginText();
        contentStream.setFont(fonts.getBoldFont(), fontSize);
        contentStream.newLineAtOffset(startX + beforeWidth, yPosition);
        contentStream.showText(checkboxChar);
        contentStream.endText();
    }

    private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) {
                lines.add("");
                continue;
            }

            StringBuilder line = new StringBuilder();
            for (int i = 0; i < paragraph.length(); i++) {
                char c = paragraph.charAt(i);
                String testLine = line.toString() + c;
                if (getStringWidth(testLine, font, fontSize) > maxWidth && line.length() > 0) {
                    lines.add(line.toString());
                    line = new StringBuilder(String.valueOf(c));
                } else {
                    line.append(c);
                }
            }
            if (line.length() > 0) {
                lines.add(line.toString());
            }
        }
        return lines;
    }

    private PageState writeTextWithWrappingAndPaging(PDDocument document, PDPageContentStream contentStream,
                                                      PDFont font, float fontSize, float margin, float yPosition,
                                                      float lineHeight, String text, float contentWidth,
                                                      float pageHeight, float pageWidth) throws IOException {
        if (text == null || text.isEmpty()) {
            return new PageState(contentStream, yPosition);
        }

        List<String> wrappedLines = wrapText(text, font, fontSize, contentWidth);

        for (String line : wrappedLines) {
            if (yPosition < margin + lineHeight) {
                contentStream.close();
                PDPage newPage = new PDPage(new PDRectangle(pageWidth, pageHeight));
                document.addPage(newPage);
                contentStream = new PDPageContentStream(document, newPage);
                yPosition = pageHeight - margin;
            }

            String safeLine = ensureSafeText(line, font);
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.newLineAtOffset(margin, yPosition);
            contentStream.showText(safeLine);
            contentStream.endText();

            yPosition -= lineHeight;
        }

        return new PageState(contentStream, yPosition);
    }

    private BufferedImage createSignatureImage(String name) throws Exception {
        BufferedImage tempImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tempG2d = tempImage.createGraphics();
        tempG2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        tempG2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Font signatureFont;
        boolean isKorean = name.matches(".*[ㄱ-ㅎㅏ-ㅣ가-힣]+.*");
        float baseFontSize = 50f;
        float scaleFactor = 1.0f;

        if (isKorean) {
            try {
                signatureFont = Font.createFont(Font.TRUETYPE_FONT,
                        new ClassPathResource("font/NanumBrushScript-Regular.ttf").getInputStream());
                signatureFont = signatureFont.deriveFont(baseFontSize);
            } catch (Exception e) {
                signatureFont = new Font("Dialog", Font.BOLD, (int) baseFontSize);
            }
        } else {
            try {
                signatureFont = Font.createFont(Font.TRUETYPE_FONT,
                        new ClassPathResource("font/DancingScript-SemiBold.ttf").getInputStream());
                signatureFont = signatureFont.deriveFont(baseFontSize);
            } catch (Exception e) {
                signatureFont = new Font("Serif", Font.ITALIC, (int) baseFontSize);
            }
        }

        AffineTransform shear = AffineTransform.getShearInstance(-0.1, 0);
        Font italicFont = signatureFont.deriveFont(shear);

        FontRenderContext frc = tempG2d.getFontRenderContext();
        TextLayout textLayout = new TextLayout(name, italicFont, frc);
        Rectangle bounds = textLayout.getBounds().getBounds();
        tempG2d.dispose();

        int width = Math.max((int) (bounds.width * scaleFactor), 50);
        int height = Math.max((int) (bounds.height * scaleFactor), 30);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        try {
            g2d.scale(scaleFactor, scaleFactor);
            int textX = -bounds.x;
            int textY = -bounds.y;
            g2d.setColor(new Color(0, 0, 0, 230));
            frc = g2d.getFontRenderContext();
            textLayout = new TextLayout(name, italicFont, frc);
            textLayout.draw(g2d, textX, textY);
        } finally {
            g2d.dispose();
        }

        return image;
    }
}
