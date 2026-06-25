package kr.wisead.domain.privacy.service;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.privacy.dto.FontSet;
import kr.wisead.domain.privacy.dto.PageState;
import kr.wisead.domain.survey.entity.SurveyMaster;
import kr.wisead.domain.survey.entity.SurveyUser;
import kr.wisead.mapper.primary.SurveyAnswerMapper;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import kr.wisead.mapper.primary.SurveyQuestionMapper;
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

/** 개인정보제공동의서 PDF 생성 Service */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrivacyConsentPdfService {

  private static final String PDF_TITLE = "개인정보 수집·이용 동의서";

  private static final String CONSENT_METHOD_NOTICE = "※ 동의 방식: 온라인 설문 참여 시 체크박스를 통한 동의";

  // English constants
  private static final String PDF_TITLE_EN =
      "Consent for Collection and Use of Personal Information";

  private static final String CONSENT_METHOD_NOTICE_EN =
      "* Consent method: Consent via checkbox during online survey participation";

  // PDF 설정 상수
  private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
  private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
  private static final float MARGIN = 50;
  private static final float FONT_SIZE = 11;
  private static final float TITLE_FONT_SIZE = 16;
  private static final float SMALL_FONT_SIZE = 9;
  private static final float LINE_HEIGHT = 18;
  private static final float CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2);

  private final SurveyAnswerMapper surveyAnswerMapper;
  private final SurveyMasterMapper surveyMasterMapper;
  private final SurveyQuestionMapper surveyQuestionMapper;
  private final SurveyUserMapper surveyUserMapper;

  private String getTitle(String language) {
    return "en".equals(language) ? PDF_TITLE_EN : PDF_TITLE;
  }

  private String getConsentMethodNotice(String language) {
    return "en".equals(language) ? CONSENT_METHOD_NOTICE_EN : CONSENT_METHOD_NOTICE;
  }

  private String getConsentDatePrefix(String language) {
    return "en".equals(language) ? "Date of Consent :   " : "동의일시 :   ";
  }

  private String getConsenterPrefix(String language) {
    return "en".equals(language) ? "Consenter   :   " : "동의자   :   ";
  }

  private String getSignatureSuffix(String language) {
    return "en".equals(language) ? "      (Signature)" : "      (서명)";
  }

  private String getSignatureNotice(String language) {
    return "en".equals(language)
        ? "* This name is digitally processed information, not an actual signature."
        : "※ 본 성명은 전산 처리된 정보이며 실제 서명이 아닙니다.";
  }

  private String getPreviewNotice(String language) {
    return "en".equals(language)
        ? "* This document is a preview and does not contain an actual signature."
        : "※ 본 문서는 미리보기용으로 실제 서명이 아닙니다.";
  }

  /** 단건 개인정보제공동의서 PDF 생성 */
  @Transactional(readOnly = true)
  public byte[] generatePrivacyConsentPdf(
      int userSeq, int eventSeq, boolean includeSignature, String language) throws Exception {
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventSeq(eventSeq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

    if (!"Y".equals(event.getPrivacyPolicyYn())) {
      throw new IllegalArgumentException("개인정보제공동의를 사용하지 않는 이벤트입니다.");
    }

    SurveyUser user =
        surveyUserMapper
            .selectBySeq(userSeq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자 정보를 찾을 수 없습니다."));

    if (user.getSubmissionDate() == null) {
      throw new IllegalArgumentException("설문 제출이 완료되지 않아 개인정보제공동의서를 생성할 수 없습니다.");
    }

    // 수집한 개인정보 타입 조회 (발송 시 사용된 전화번호 포함)
    List<String> privacyTypes = getPrivacyTypesWithSendPhone(eventSeq, user);
    if (privacyTypes.isEmpty()) {
      throw new IllegalArgumentException("개인정보를 수집하지 않는 설문입니다. 개인정보제공동의서를 생성할 수 없습니다.");
    }

    // 수집한 개인정보 항목 중 파기 여부 확인
    PrivacyDestroyedResult destroyedResult = checkPrivacyDestroyed(user, privacyTypes);
    if (destroyedResult.isDestroyed()) {
      throw new IllegalArgumentException("개인정보가 파기된 데이터입니다. 개인정보제공동의서를 생성할 수 없습니다.");
    }

    // 동의서에 표시할 이름 결정 (이름을 수집한 경우 이름, 아니면 전화번호 뒷자리)
    String displayName = destroyedResult.getDisplayName();

    return createPdf(event, user, displayName, includeSignature, language);
  }

  /** 다건 개인정보제공동의서 PDF.zip 생성 */
  @Transactional(readOnly = true)
  public byte[] generatePrivacyConsentPdfZip(
      int eventSeq, boolean includeSignature, String language) throws Exception {
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventSeq(eventSeq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

    if (!"Y".equals(event.getPrivacyPolicyYn())) {
      throw new IllegalArgumentException("개인정보제공동의를 사용하지 않는 이벤트입니다.");
    }

    List<SurveyUser> users = surveyUserMapper.selectCompletedByEventSeq(eventSeq);
    if (users == null || users.isEmpty()) {
      throw new IllegalArgumentException("참여자 정보를 찾을 수 없습니다.");
    }

    // 기본 개인정보 타입 조회 (survey_question 기반)
    List<String> basePrivacyTypes = surveyQuestionMapper.selectPrivacyTypesByEventSeq(eventSeq);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    int totalUsers = users.size();
    int destroyedDataCount = 0;
    int generatedPdfCount = 0;
    int noPrivacyDataCount = 0;

    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      for (SurveyUser user : users) {
        // 수집한 개인정보 타입 조회 (발송 시 사용된 전화번호 포함)
        List<String> privacyTypes = getPrivacyTypesWithSendPhone(basePrivacyTypes, user);
        if (privacyTypes.isEmpty()) {
          noPrivacyDataCount++;
          continue;
        }

        // 수집한 개인정보 항목 중 파기 여부 확인
        PrivacyDestroyedResult destroyedResult = checkPrivacyDestroyed(user, privacyTypes);
        if (destroyedResult.isDestroyed()) {
          destroyedDataCount++;
          continue;
        }

        String displayName = destroyedResult.getDisplayName();

        try {
          byte[] pdfContent = createPdf(event, user, displayName, includeSignature, language);
          String fileName = String.format("개인정보제공동의서_%s.pdf", displayName);

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
        String infoContent;
        if ("en".equals(language)) {
          infoContent =
              String.format(
                  "Privacy Consent Download Information\n\n"
                      + "Total participants: %d\n"
                      + "Generated consent forms: %d\n"
                      + "Destroyed personal data: %d\n\n"
                      + "* Consent forms cannot be generated for data whose retention period has"
                      + " expired.",
                  totalUsers, generatedPdfCount, destroyedDataCount);
        } else {
          infoContent =
              String.format(
                  "개인정보제공동의서 다운로드 안내\n\n"
                      + "전체 참여자 수: %d명\n"
                      + "생성된 동의서: %d개\n"
                      + "개인정보 파기 데이터: %d개\n\n"
                      + "※ 개인정보 보관기간이 지나 파기된 데이터는 동의서를 생성할 수 없습니다.",
                  totalUsers, generatedPdfCount, destroyedDataCount);
        }

        ZipEntry infoEntry = new ZipEntry("en".equals(language) ? "Notice.txt" : "안내사항.txt");
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

  /** 파일명용 사용자 이름 조회 */
  @Transactional(readOnly = true)
  public String getUserNameForFilename(int userSeq) throws Exception {
    SurveyUser user =
        surveyUserMapper
            .selectBySeq(userSeq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자 정보를 찾을 수 없습니다."));

    // 수집한 개인정보 타입 조회 (발송 시 사용된 전화번호 포함)
    List<String> privacyTypes = getPrivacyTypesWithSendPhone(user.getEventSeq(), user);
    if (privacyTypes.isEmpty()) {
      throw new IllegalArgumentException("개인정보를 수집하지 않는 설문입니다.");
    }

    // 수집한 개인정보 항목 중 파기 여부 확인
    PrivacyDestroyedResult destroyedResult = checkPrivacyDestroyed(user, privacyTypes);
    if (destroyedResult.isDestroyed()) {
      throw new IllegalArgumentException("개인정보가 파기된 데이터입니다.");
    }

    return destroyedResult.getDisplayName();
  }

  /** 미리보기용 개인정보제공동의서 PDF 생성 (제3자 제공 동의 포함) */
  public byte[] generatePreviewPdf(
      String title,
      String content,
      String language,
      String thirdPartyYn,
      String thirdPartyTtl,
      String thirdPartyContent)
      throws Exception {
    boolean hasThirdParty =
        "Y".equals(thirdPartyYn) && thirdPartyContent != null && !thirdPartyContent.isEmpty();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    try (PDDocument document = new PDDocument()) {
      FontSet fonts = loadFonts(document);

      PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
      document.addPage(page);
      PDPageContentStream contentStream = new PDPageContentStream(document, page);

      float yPosition = PAGE_HEIGHT - MARGIN;

      try {
        // 제목
        String pdfTitle = getTitle(language);
        float titleWidth = getStringWidth(pdfTitle, fonts.getBoldFont(), TITLE_FONT_SIZE);
        float titleX = (PAGE_WIDTH - titleWidth) / 2;
        contentStream.beginText();
        contentStream.setFont(fonts.getBoldFont(), TITLE_FONT_SIZE);
        contentStream.newLineAtOffset(titleX, yPosition);
        contentStream.showText(ensureSafeText(pdfTitle, fonts.getBoldFont()));
        contentStream.endText();
        yPosition -= LINE_HEIGHT * 3;

        // 섹션 제목
        String previewSectionTitle = title != null && !title.trim().isEmpty() ? title : "[제목]";
        writeSectionTitle(contentStream, fonts, previewSectionTitle, yPosition);
        yPosition -= LINE_HEIGHT * 2;

        // 내용 (bold 태그 지원)
        if (content != null && !content.isEmpty()) {
          PageState pageState =
              writeTextWithBoldAndPaging(
                  document,
                  contentStream,
                  fonts,
                  FONT_SIZE,
                  MARGIN,
                  yPosition,
                  LINE_HEIGHT,
                  content,
                  CONTENT_WIDTH,
                  PAGE_HEIGHT,
                  PAGE_WIDTH);
          contentStream = pageState.getContentStream();
          yPosition = pageState.getYPosition();
        }
        yPosition -= LINE_HEIGHT;

        // 동의 확인 문구
        writeAgreeCheckboxText(
            contentStream, fonts, FONT_SIZE, PAGE_WIDTH, yPosition, language, false);
        yPosition -= LINE_HEIGHT;

        // 동의 방식 안내 문구
        writeConsentMethodNotice(contentStream, fonts, yPosition, language);
        yPosition -= LINE_HEIGHT;

        // 제3자 제공 동의 섹션 (같은 페이지 흐름으로 이어서 출력)
        if (hasThirdParty) {
          PageState sepState = drawSeparatorOrNewPage(document, contentStream, yPosition);
          contentStream = sepState.getContentStream();
          yPosition = sepState.getYPosition();

          // 제3자 제공 동의 제목
          String tpPreviewTitle =
              thirdPartyTtl != null && !thirdPartyTtl.trim().isEmpty() ? thirdPartyTtl : "[제목]";
          writeSectionTitle(contentStream, fonts, tpPreviewTitle, yPosition);
          yPosition -= LINE_HEIGHT * 2;

          // 제3자 제공 동의 내용 (bold 태그 지원)
          PageState tpPageState =
              writeTextWithBoldAndPaging(
                  document,
                  contentStream,
                  fonts,
                  FONT_SIZE,
                  MARGIN,
                  yPosition,
                  LINE_HEIGHT,
                  thirdPartyContent,
                  CONTENT_WIDTH,
                  PAGE_HEIGHT,
                  PAGE_WIDTH);
          contentStream = tpPageState.getContentStream();
          yPosition = tpPageState.getYPosition();
          yPosition -= LINE_HEIGHT;

          // 페이지 하단 체크
          if (yPosition < MARGIN + LINE_HEIGHT) {
            contentStream.close();
            PDPage newPage = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
            document.addPage(newPage);
            contentStream = new PDPageContentStream(document, newPage);
            yPosition = PAGE_HEIGHT - MARGIN;
          }

          // 제3자 제공 동의 체크박스
          writeAgreeCheckboxText(
              contentStream, fonts, FONT_SIZE, PAGE_WIDTH, yPosition, language, true);
          yPosition -= LINE_HEIGHT;

          // 동의 방식 안내 문구
          writeConsentMethodNotice(contentStream, fonts, yPosition, language);
          yPosition -= LINE_HEIGHT;
        }

        // 페이지 하단 체크: 여백(2) + 날짜(1) + 이름(1) = 최소 5줄 필요
        if (yPosition < MARGIN + LINE_HEIGHT * 5) {
          contentStream.close();
          PDPage newPage = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
          document.addPage(newPage);
          contentStream = new PDPageContentStream(document, newPage);
          yPosition = PAGE_HEIGHT - MARGIN;
        }
        yPosition -= LINE_HEIGHT * 3;

        // 날짜 및 이름
        SimpleDateFormat sdf =
            new SimpleDateFormat(getConsentDatePrefix(language) + "yyyy. MM. dd.      HH:mm:ss");
        String consentDate = sdf.format(new Date());
        float dateWidth = getStringWidth(consentDate, fonts.getRegularFont(), FONT_SIZE);
        float dateX = PAGE_WIDTH - MARGIN - dateWidth;
        contentStream.beginText();
        contentStream.setFont(fonts.getRegularFont(), FONT_SIZE);
        contentStream.newLineAtOffset(dateX, yPosition);
        contentStream.showText(ensureSafeText(consentDate, fonts.getRegularFont()));
        contentStream.endText();
        yPosition -= LINE_HEIGHT;

        String nameText = getConsenterPrefix(language) + "OOO";
        float nameWidth = getStringWidth(nameText, fonts.getRegularFont(), FONT_SIZE);
        float nameX = PAGE_WIDTH - MARGIN - nameWidth;
        contentStream.beginText();
        contentStream.setFont(fonts.getRegularFont(), FONT_SIZE);
        contentStream.newLineAtOffset(nameX, yPosition);
        contentStream.showText(ensureSafeText(nameText, fonts.getRegularFont()));
        contentStream.endText();
        yPosition -= LINE_HEIGHT;

        // 미리보기 안내 문구
        String previewNotice = getPreviewNotice(language);
        float previewNoticeWidth =
            getStringWidth(previewNotice, fonts.getRegularFont(), SMALL_FONT_SIZE);
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

  private byte[] createPdf(
      SurveyMaster event,
      SurveyUser user,
      String decryptedName,
      boolean includeSignature,
      String language)
      throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    try (PDDocument document = new PDDocument()) {
      FontSet fonts = loadFonts(document);

      PDPage page = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
      document.addPage(page);
      PDPageContentStream contentStream = new PDPageContentStream(document, page);

      float yPosition = PAGE_HEIGHT - MARGIN;

      try {
        // 제목 (중앙 정렬)
        String pdfTitle = getTitle(language);
        float titleWidth = getStringWidth(pdfTitle, fonts.getBoldFont(), TITLE_FONT_SIZE);
        float titleX = (PAGE_WIDTH - titleWidth) / 2;
        contentStream.beginText();
        contentStream.setFont(fonts.getBoldFont(), TITLE_FONT_SIZE);
        contentStream.newLineAtOffset(titleX, yPosition);
        contentStream.showText(ensureSafeText(pdfTitle, fonts.getBoldFont()));
        contentStream.endText();
        yPosition -= LINE_HEIGHT * 3;

        // 섹션 제목
        String sectionTitle = event.getPrivacyPolicyTtl();
        writeSectionTitle(contentStream, fonts, sectionTitle, yPosition);
        yPosition -= LINE_HEIGHT * 2;

        // 개인정보 처리방침 내용 (bold 태그 지원)
        String policyDesc = event.getPrivacyPolicyDesc();
        if (policyDesc != null && !policyDesc.isEmpty()) {
          PageState pageState =
              writeTextWithBoldAndPaging(
                  document,
                  contentStream,
                  fonts,
                  FONT_SIZE,
                  MARGIN,
                  yPosition,
                  LINE_HEIGHT,
                  policyDesc,
                  CONTENT_WIDTH,
                  PAGE_HEIGHT,
                  PAGE_WIDTH);
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
        writeAgreeCheckboxText(
            contentStream, fonts, FONT_SIZE, PAGE_WIDTH, yPosition, language, false);
        yPosition -= LINE_HEIGHT;

        // 동의 방식 안내 문구
        writeConsentMethodNotice(contentStream, fonts, yPosition, language);
        yPosition -= LINE_HEIGHT;

        // 제3자 제공 동의 섹션 (같은 페이지 흐름으로 이어서 출력)
        if ("Y".equals(event.getThirdPartyYn())) {
          PageState sepState = drawSeparatorOrNewPage(document, contentStream, yPosition);
          contentStream = sepState.getContentStream();
          yPosition = sepState.getYPosition();

          // 제3자 제공 동의 제목
          String tpSectionTitle = event.getThirdPartyTtl();
          writeSectionTitle(contentStream, fonts, tpSectionTitle, yPosition);
          yPosition -= LINE_HEIGHT * 2;

          // 제3자 제공 동의 내용 (bold 태그 지원)
          String thirdPartyDesc = event.getThirdPartyDesc();
          if (thirdPartyDesc != null && !thirdPartyDesc.isEmpty()) {
            PageState tpPageState =
                writeTextWithBoldAndPaging(
                    document,
                    contentStream,
                    fonts,
                    FONT_SIZE,
                    MARGIN,
                    yPosition,
                    LINE_HEIGHT,
                    thirdPartyDesc,
                    CONTENT_WIDTH,
                    PAGE_HEIGHT,
                    PAGE_WIDTH);
            contentStream = tpPageState.getContentStream();
            yPosition = tpPageState.getYPosition();
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

          // 제3자 제공 동의 체크박스
          writeAgreeCheckboxText(
              contentStream, fonts, FONT_SIZE, PAGE_WIDTH, yPosition, language, true);
          yPosition -= LINE_HEIGHT;

          // 동의 방식 안내 문구
          writeConsentMethodNotice(contentStream, fonts, yPosition, language);
          yPosition -= LINE_HEIGHT;
        }

        // 서명란
        SimpleDateFormat sdf =
            new SimpleDateFormat(getConsentDatePrefix(language) + "yyyy. MM. dd.      HH:mm:ss");
        String consentDate;
        if (user.getSurveyStartTime() != null) {
          consentDate = sdf.format(java.sql.Timestamp.valueOf(user.getSurveyStartTime()));
        } else if (user.getSubmissionDate() != null) {
          consentDate = sdf.format(java.sql.Timestamp.valueOf(user.getSubmissionDate()));
        } else {
          consentDate = sdf.format(new Date());
        }

        // 페이지 하단 체크: 여백(2) + 날짜(1) + 이름(1) = 최소 5줄 필요
        if (yPosition < MARGIN + LINE_HEIGHT * 5) {
          contentStream.close();
          PDPage newPage = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
          document.addPage(newPage);
          contentStream = new PDPageContentStream(document, newPage);
          yPosition = PAGE_HEIGHT - MARGIN;
        }
        yPosition -= LINE_HEIGHT * 2;

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
        String nameSignatureText =
            includeSignature
                ? getConsenterPrefix(language) + decryptedName + getSignatureSuffix(language)
                : getConsenterPrefix(language) + decryptedName;
        float nameSignatureWidth =
            getStringWidth(nameSignatureText, fonts.getRegularFont(), FONT_SIZE);
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
            PDImageXObject pdSignature =
                PDImageXObject.createFromByteArray(
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
          String signatureNotice = getSignatureNotice(language);
          float signatureNoticeWidth =
              getStringWidth(signatureNotice, fonts.getRegularFont(), SMALL_FONT_SIZE);
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

  /** 구분선을 그리거나, 공간 부족 시 새 페이지로 이동. 제3자 제공 동의 섹션 앞에 사용. */
  private PageState drawSeparatorOrNewPage(
      PDDocument document, PDPageContentStream contentStream, float yPosition) throws IOException {
    yPosition -= LINE_HEIGHT / 2;
    // 제목(1) + 이벤트명(1) + 여백(1) + 내용 최소(1) + 체크박스(1) = 5줄 필요
    if (yPosition < MARGIN + LINE_HEIGHT * 5) {
      contentStream.close();
      PDPage newPage = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
      document.addPage(newPage);
      contentStream = new PDPageContentStream(document, newPage);
      yPosition = PAGE_HEIGHT - MARGIN;
    }
    yPosition -= LINE_HEIGHT;
    return new PageState(contentStream, yPosition);
  }

  private FontSet loadFonts(PDDocument document) throws Exception {
    try {
      PDFont font =
          PDType0Font.load(
              document, new ClassPathResource("font/NanumBarunGothic.ttf").getInputStream());
      PDFont boldFont =
          PDType0Font.load(
              document, new ClassPathResource("font/NanumBarunGothicBold.ttf").getInputStream());
      return new FontSet(font, boldFont);
    } catch (Exception e) {
      log.debug("나눔바른고딕 폰트 로드 실패, 기본 폰트 사용: {}", e.getMessage());
      return new FontSet(
          new PDType1Font(Standard14Fonts.FontName.HELVETICA),
          new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD));
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

  /** 섹션 번호 + 제목을 왼쪽 정렬 bold로 출력 */
  private void writeSectionTitle(
      PDPageContentStream contentStream, FontSet fonts, String title, float yPosition)
      throws IOException {
    contentStream.beginText();
    contentStream.setFont(fonts.getBoldFont(), FONT_SIZE);
    contentStream.newLineAtOffset(MARGIN, yPosition);
    contentStream.showText(ensureSafeText(title, fonts.getBoldFont()));
    contentStream.endText();
  }

  /** 동의 방식 안내 문구를 중앙 정렬 소폰트로 출력 */
  private void writeConsentMethodNotice(
      PDPageContentStream contentStream, FontSet fonts, float yPosition, String language)
      throws IOException {
    String notice = getConsentMethodNotice(language);
    float noticeWidth = getStringWidth(notice, fonts.getRegularFont(), SMALL_FONT_SIZE);
    float noticeX = (PAGE_WIDTH - noticeWidth) / 2;
    contentStream.beginText();
    contentStream.setFont(fonts.getRegularFont(), SMALL_FONT_SIZE);
    contentStream.newLineAtOffset(noticeX, yPosition);
    contentStream.showText(ensureSafeText(notice, fonts.getRegularFont()));
    contentStream.endText();
  }

  private void writeAgreeCheckboxText(
      PDPageContentStream contentStream,
      FontSet fonts,
      float fontSize,
      float pageWidth,
      float yPosition,
      String language,
      boolean isThirdParty)
      throws IOException {
    String textBeforeCheckbox;
    if (isThirdParty) {
      textBeforeCheckbox =
          "en".equals(language)
              ? "I have read and agree to the above third-party provision.       Agreed "
              : "개인정보 제3자 제공에 동의합니다.        동의함 ";
    } else {
      textBeforeCheckbox =
          "en".equals(language)
              ? "I have read and agree to the above.       Agreed "
              : "개인정보 수집 이용에 동의합니다.        동의함 ";
    }
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

  /**
   * HTML &lt;b&gt; 태그를 파싱하여 bold/regular 세그먼트로 분리
   *
   * @param text HTML bold 태그가 포함될 수 있는 텍스트
   * @return TextSegment 리스트 (각 세그먼트에 bold 여부 포함)
   */
  private List<TextSegment> parseBoldSegments(String text) {
    List<TextSegment> segments = new ArrayList<>();
    if (text == null || text.isEmpty()) return segments;

    int pos = 0;
    while (pos < text.length()) {
      int boldStart = text.indexOf("<b>", pos);
      if (boldStart == -1) {
        // 남은 텍스트는 일반 텍스트
        if (pos < text.length()) {
          segments.add(new TextSegment(text.substring(pos), false));
        }
        break;
      }

      // bold 시작 전 일반 텍스트
      if (boldStart > pos) {
        segments.add(new TextSegment(text.substring(pos, boldStart), false));
      }

      int boldEnd = text.indexOf("</b>", boldStart + 3);
      if (boldEnd == -1) {
        // 닫는 태그 없으면 나머지를 bold로 처리
        segments.add(new TextSegment(text.substring(boldStart + 3), true));
        break;
      }

      segments.add(new TextSegment(text.substring(boldStart + 3, boldEnd), true));
      pos = boldEnd + 4;
    }

    return segments;
  }

  /** bold 태그를 지원하는 텍스트 줄바꿈 및 페이징 처리 */
  private PageState writeTextWithBoldAndPaging(
      PDDocument document,
      PDPageContentStream contentStream,
      FontSet fonts,
      float fontSize,
      float margin,
      float yPosition,
      float lineHeight,
      String text,
      float contentWidth,
      float pageHeight,
      float pageWidth)
      throws IOException {
    if (text == null || text.isEmpty()) {
      return new PageState(contentStream, yPosition);
    }

    // bold 태그가 없으면 기존 방식으로 처리
    if (!text.contains("<b>")) {
      return writeTextWithWrappingAndPaging(
          document,
          contentStream,
          fonts.getRegularFont(),
          fontSize,
          margin,
          yPosition,
          lineHeight,
          text,
          contentWidth,
          pageHeight,
          pageWidth);
    }

    // 줄 단위로 분리 후 각 줄에서 bold 세그먼트 처리
    String[] paragraphs = text.split("\n");
    for (String paragraph : paragraphs) {
      if (paragraph.trim().isEmpty()) {
        yPosition -= lineHeight;
        if (yPosition < margin + lineHeight) {
          contentStream.close();
          PDPage newPage = new PDPage(new PDRectangle(pageWidth, pageHeight));
          document.addPage(newPage);
          contentStream = new PDPageContentStream(document, newPage);
          yPosition = pageHeight - margin;
        }
        continue;
      }

      // 세그먼트 파싱
      List<TextSegment> segments = parseBoldSegments(paragraph);

      // 줄바꿈 처리: 세그먼트를 순회하며 한 줄의 너비를 계산
      List<List<TextSegment>> wrappedLines =
          wrapSegmentedText(segments, fonts, fontSize, contentWidth);

      for (List<TextSegment> lineSegments : wrappedLines) {
        if (yPosition < margin + lineHeight) {
          contentStream.close();
          PDPage newPage = new PDPage(new PDRectangle(pageWidth, pageHeight));
          document.addPage(newPage);
          contentStream = new PDPageContentStream(document, newPage);
          yPosition = pageHeight - margin;
        }

        float xPosition = margin;
        for (TextSegment seg : lineSegments) {
          PDFont font = seg.isBold() ? fonts.getBoldFont() : fonts.getRegularFont();
          String safeText = ensureSafeText(seg.text(), font);
          contentStream.beginText();
          contentStream.setFont(font, fontSize);
          contentStream.newLineAtOffset(xPosition, yPosition);
          contentStream.showText(safeText);
          contentStream.endText();
          xPosition += getStringWidth(safeText, font, fontSize);
        }

        yPosition -= lineHeight;
      }
    }

    return new PageState(contentStream, yPosition);
  }

  /** 세그먼트 텍스트를 줄바꿈 처리 */
  private List<List<TextSegment>> wrapSegmentedText(
      List<TextSegment> segments, FontSet fonts, float fontSize, float maxWidth)
      throws IOException {
    List<List<TextSegment>> result = new ArrayList<>();
    List<TextSegment> currentLine = new ArrayList<>();
    float currentWidth = 0;

    for (TextSegment segment : segments) {
      PDFont font = segment.isBold() ? fonts.getBoldFont() : fonts.getRegularFont();
      String text = segment.text();
      StringBuilder pending = new StringBuilder();

      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        String testStr = pending.toString() + c;
        float testWidth = getStringWidth(testStr, font, fontSize);
        float charWidth = testWidth - getStringWidth(pending.toString(), font, fontSize);

        if (currentWidth + charWidth > maxWidth && !currentLine.isEmpty()) {
          // 현재 pending 텍스트를 현재 줄에 flush
          if (!pending.isEmpty()) {
            appendToLine(currentLine, pending.toString(), segment.isBold());
            pending.setLength(0);
          }
          result.add(currentLine);
          currentLine = new ArrayList<>();
          currentWidth = 0;
        }

        pending.append(c);
        currentWidth += charWidth;
      }

      // flush remaining pending text
      if (!pending.isEmpty()) {
        appendToLine(currentLine, pending.toString(), segment.isBold());
      }
    }

    if (!currentLine.isEmpty()) {
      result.add(currentLine);
    }

    return result;
  }

  /** 현재 줄의 마지막 세그먼트와 같은 bold 속성이면 텍스트를 이어붙이고, 아니면 새 세그먼트 추가 */
  private void appendToLine(List<TextSegment> line, String text, boolean bold) {
    if (!line.isEmpty() && line.getLast().isBold() == bold) {
      TextSegment last = line.getLast();
      line.set(line.size() - 1, new TextSegment(last.text() + text, bold));
    } else {
      line.add(new TextSegment(text, bold));
    }
  }

  /** 텍스트 세그먼트 (bold 여부 포함) */
  private record TextSegment(String text, boolean bold) {
    boolean isBold() {
      return bold;
    }
  }

  private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth)
      throws IOException {
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

  private PageState writeTextWithWrappingAndPaging(
      PDDocument document,
      PDPageContentStream contentStream,
      PDFont font,
      float fontSize,
      float margin,
      float yPosition,
      float lineHeight,
      String text,
      float contentWidth,
      float pageHeight,
      float pageWidth)
      throws IOException {
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
    tempG2d.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    Font signatureFont;
    boolean isKorean = name.matches(".*[ㄱ-ㅎㅏ-ㅣ가-힣]+.*");
    float baseFontSize = 50f;
    float scaleFactor = 1.0f;

    if (isKorean) {
      try {
        signatureFont =
            Font.createFont(
                Font.TRUETYPE_FONT,
                new ClassPathResource("font/NanumBrushScript-Regular.ttf").getInputStream());
        signatureFont = signatureFont.deriveFont(baseFontSize);
      } catch (Exception e) {
        signatureFont = new Font("Dialog", Font.BOLD, (int) baseFontSize);
      }
    } else {
      try {
        signatureFont =
            Font.createFont(
                Font.TRUETYPE_FONT,
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
    g2d.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

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

  /**
   * 개인정보 타입 목록 조회 (발송 시 사용된 전화번호 포함)
   *
   * @param eventSeq 이벤트 시퀀스
   * @param user 설문 참여자
   * @return 개인정보 타입 목록
   */
  private List<String> getPrivacyTypesWithSendPhone(int eventSeq, SurveyUser user) {
    List<String> baseTypes = surveyQuestionMapper.selectPrivacyTypesByEventSeq(eventSeq);
    return getPrivacyTypesWithSendPhone(baseTypes, user);
  }

  /**
   * 개인정보 타입 목록에 발송 전화번호 포함 (다건 처리용 오버로드)
   *
   * @param basePrivacyTypes 기본 개인정보 타입 목록 (survey_question 기반)
   * @param user 설문 참여자
   * @return 개인정보 타입 목록 (발송 전화번호 포함)
   */
  private List<String> getPrivacyTypesWithSendPhone(
      List<String> basePrivacyTypes, SurveyUser user) {
    List<String> privacyTypes = new ArrayList<>();
    if (basePrivacyTypes != null) {
      privacyTypes.addAll(basePrivacyTypes);
    }

    // 발송 시 사용된 전화번호가 있고, CU 타입이 아직 없으면 추가
    if (user.getUserPhone() != null && !user.getUserPhone().isEmpty()) {
      if (!privacyTypes.contains("CU")) {
        privacyTypes.add("CU");
      }
    }

    return privacyTypes;
  }

  /**
   * 수집한 개인정보 항목 중 파기 여부 확인
   *
   * @param user 설문 참여자
   * @param privacyTypes 수집한 개인정보 타입 목록 (NE, CU, SO, EM, AD)
   * @return 파기 여부 및 표시용 이름
   */
  private PrivacyDestroyedResult checkPrivacyDestroyed(SurveyUser user, List<String> privacyTypes) {
    String displayName = null;
    boolean hasAnyValidData = false;

    for (String type : privacyTypes) {
      String decryptedValue = null;
      switch (type) {
        case "NE": // 이름
          decryptedValue = CryptoUtils.decryptName(user.getUserName());
          // survey_user에 이름이 없으면 survey_answer에서 NE 타입 답변 조회 (QR 설문 등)
          if (!isValidPrivacyData(decryptedValue)) {
            String answerName =
                surveyAnswerMapper.selectAnswerByTypeDetail(
                    user.getEventSeq(), user.getSeq(), "NE");
            if (answerName != null && !answerName.trim().isEmpty()) {
              decryptedValue =
                  kr.wisead.domain.survey.service.OtherTextCrypto.decryptForDisplay(
                          "NE", answerName)
                      .trim();
            }
          }
          if (isValidPrivacyData(decryptedValue)) {
            hasAnyValidData = true;
            displayName = decryptedValue; // 이름을 표시용으로 우선 사용
          }
          break;
        case "CU": // 전화번호
          decryptedValue = CryptoUtils.getDecryptedAES256Data(user.getUserPhone());
          if (isValidPrivacyData(decryptedValue)) {
            hasAnyValidData = true;
            if (displayName == null) {
              // 이름이 없으면 전화번호 뒷 4자리 사용
              displayName = maskPhoneForDisplay(decryptedValue);
            }
          }
          break;
        case "SO": // 주민번호
          decryptedValue = CryptoUtils.getDecryptedAES256Data(user.getJuminNum());
          if (isValidPrivacyData(decryptedValue)) {
            hasAnyValidData = true;
          }
          break;
        case "EM": // 이메일
          decryptedValue = CryptoUtils.getDecryptedAES256Data(user.getUserEmail());
          if (isValidPrivacyData(decryptedValue)) {
            hasAnyValidData = true;
          }
          break;
        case "AD": // 주소
          decryptedValue = CryptoUtils.getDecryptedAES256Data(user.getAddress());
          if (isValidPrivacyData(decryptedValue)) {
            hasAnyValidData = true;
          }
          break;
      }
    }

    // 수집한 개인정보 중 하나라도 유효한 데이터가 없으면 파기된 것으로 판단
    boolean isDestroyed = !hasAnyValidData;

    // 표시용 이름이 없으면 "참여자" 사용
    if (displayName == null) {
      displayName = "참여자_" + user.getSeq();
    }

    return new PrivacyDestroyedResult(isDestroyed, displayName);
  }

  /** 개인정보 데이터가 유효한지 확인 (파기되지 않았는지) */
  private boolean isValidPrivacyData(String decryptedValue) {
    if (decryptedValue == null) {
      return false;
    }
    String trimmed = decryptedValue.trim();
    if (trimmed.isEmpty() || "-".equals(trimmed)) {
      return false;
    }
    // 마스킹된 데이터 확인 (예: *******7911)
    if (trimmed.matches("^\\*+.*$")) {
      return false;
    }
    return true;
  }

  /** 전화번호를 표시용으로 마스킹 (뒷 4자리만 표시) */
  private String maskPhoneForDisplay(String phone) {
    if (phone == null || phone.length() < 4) {
      return phone;
    }
    return "참여자_" + phone.substring(phone.length() - 4);
  }

  /** 개인정보 파기 확인 결과 */
  private static class PrivacyDestroyedResult {
    private final boolean destroyed;
    private final String displayName;

    public PrivacyDestroyedResult(boolean destroyed, String displayName) {
      this.destroyed = destroyed;
      this.displayName = displayName;
    }

    public boolean isDestroyed() {
      return destroyed;
    }

    public String getDisplayName() {
      return displayName;
    }
  }
}
