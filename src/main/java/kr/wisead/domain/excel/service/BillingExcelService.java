package kr.wisead.domain.excel.service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import kr.wisead.domain.payment.dto.*;
import kr.wisead.domain.payment.entity.Transaction;
import kr.wisead.domain.payment.service.BillingStatisticsService;
import kr.wisead.domain.statistics.dto.StatsSearchRequest;
import kr.wisead.domain.statistics.dto.UserMsgStatsResponse;
import kr.wisead.domain.statistics.dto.UserSurveyStatsResponse;
import kr.wisead.domain.statistics.service.UserStatisticsService;
import kr.wisead.mapper.primary.TransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

/** 과금 통계 Excel 생성 서비스 - 다중 시트 Excel 생성 (레거시 호환) */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingExcelService {

  private final BillingStatisticsService billingStatisticsService;
  private final UserStatisticsService userStatisticsService;
  private final TransactionMapper transactionMapper;
  private final ExcelService excelService;

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter DATETIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private static final String SHEET_USAGE_SUMMARY = "사용내역";
  private static final String SHEET_MSG_DETAIL = "상세-와이즈애드(메시지)";
  private static final String SHEET_SURVEY_DETAIL = "상세-와이즈애드(설문조사)";
  private static final String SHEET_USER_HISTORY = "요금 내역(";

  /** 과금 통계 Excel 생성 */
  public byte[] generateBillingExcel(
      BillingStatsSearchRequest request, String userId, List<String> targetUserIds) {
    try (SXSSFWorkbook workbook = new SXSSFWorkbook()) {
      // 1. 사용내역 시트
      addUsageSummarySheet(workbook, request);

      // 2. 메시지 상세 시트
      addMessageDetailSheet(workbook, request);

      // 3. 설문조사 상세 시트
      addSurveyDetailSheet(workbook, request);

      // 4. 사용자별 요금 내역 시트
      addUserHistorySheets(workbook, request, targetUserIds);

      return toByteArray(workbook);
    } catch (Exception e) {
      log.error("과금 통계 Excel 생성 실패", e);
      throw new RuntimeException("Excel 파일 생성에 실패했습니다.", e);
    }
  }

  /** 1. 사용내역 시트 */
  private void addUsageSummarySheet(SXSSFWorkbook workbook, BillingStatsSearchRequest request) {
    Sheet sheet = workbook.createSheet(SHEET_USAGE_SUMMARY);

    CellStyle headerStyle = createHeaderStyle(workbook);
    CellStyle moneyStyle = createMoneyStyle(workbook);

    // 제목
    Row titleRow = sheet.createRow(0);
    titleRow.createCell(1).setCellValue("사용내역");
    sheet.createRow(1);

    // 기간
    Row periodRow = sheet.createRow(2);
    periodRow
        .createCell(1)
        .setCellValue("조회기간: " + request.getStartDate() + " ~ " + request.getEndDate());

    // 헤더
    String[] headers = {"서비스 타입", "사용 건수", "사용 금액"};
    Row headerRow = sheet.createRow(4);
    for (int i = 0; i < headers.length; i++) {
      Cell cell = headerRow.createCell(i + 1);
      cell.setCellValue(headers[i]);
      cell.setCellStyle(headerStyle);
    }

    // 데이터 (서비스 타입별 통계)
    List<ServiceTypeBillingStatsResponse> stats =
        billingStatisticsService.getBillingStatsByServiceType(request);
    int rowIdx = 5;
    for (ServiceTypeBillingStatsResponse stat : stats) {
      Row row = sheet.createRow(rowIdx++);
      row.createCell(1)
          .setCellValue(stat.getServiceTypeName() != null ? stat.getServiceTypeName() : "");
      row.createCell(2).setCellValue(stat.getTransactionCount());

      Cell amountCell = row.createCell(3);
      amountCell.setCellValue(
          stat.getTotalAmount() != null ? stat.getTotalAmount().doubleValue() : 0);
      amountCell.setCellStyle(moneyStyle);
    }

    // 컬럼 너비
    sheet.setColumnWidth(1, 5000);
    sheet.setColumnWidth(2, 3000);
    sheet.setColumnWidth(3, 5000);
  }

  /** 2. 메시지 상세 시트 */
  private void addMessageDetailSheet(SXSSFWorkbook workbook, BillingStatsSearchRequest request) {
    Sheet sheet = workbook.createSheet(SHEET_MSG_DETAIL);

    CellStyle headerStyle = createHeaderStyle(workbook);
    CellStyle countStyle = createCountStyle(workbook);
    CellStyle moneyStyle = createMoneyStyle(workbook);

    // 제목
    Row titleRow = sheet.createRow(0);
    titleRow.createCell(1).setCellValue("메시지 발송 상세 내역");

    // 기간
    Row periodRow = sheet.createRow(2);
    periodRow
        .createCell(1)
        .setCellValue("조회기간: " + request.getStartDate() + " ~ " + request.getEndDate());

    // 헤더
    String[] headers = {
      "사용자 ID", "사용자명", "SMS 총건수", "SMS 성공", "LMS 총건수", "LMS 성공", "MMS 총건수", "MMS 성공", "합계"
    };
    Row headerRow = sheet.createRow(4);
    for (int i = 0; i < headers.length; i++) {
      Cell cell = headerRow.createCell(i + 1);
      cell.setCellValue(headers[i]);
      cell.setCellStyle(headerStyle);
    }

    // 데이터
    StatsSearchRequest statsRequest =
        StatsSearchRequest.builder()
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .serviceType("M")
            .build();
    List<UserMsgStatsResponse> msgStats = userStatisticsService.findMsgStats(statsRequest);
    int rowIdx = 5;
    for (UserMsgStatsResponse stat : msgStats) {
      Row row = sheet.createRow(rowIdx++);
      row.createCell(1).setCellValue(stat.getUserId() != null ? stat.getUserId() : "");
      row.createCell(2).setCellValue(stat.getUserName() != null ? stat.getUserName() : "");

      Cell smsCountCell = row.createCell(3);
      smsCountCell.setCellValue(stat.getSmsTotal());
      smsCountCell.setCellStyle(countStyle);

      Cell smsSuccCell = row.createCell(4);
      smsSuccCell.setCellValue(stat.getSmsSucc());
      smsSuccCell.setCellStyle(countStyle);

      Cell lmsCountCell = row.createCell(5);
      lmsCountCell.setCellValue(stat.getLmsTotal());
      lmsCountCell.setCellStyle(countStyle);

      Cell lmsSuccCell = row.createCell(6);
      lmsSuccCell.setCellValue(stat.getLmsSucc());
      lmsSuccCell.setCellStyle(countStyle);

      Cell mmsCountCell = row.createCell(7);
      mmsCountCell.setCellValue(stat.getMmsTotal());
      mmsCountCell.setCellStyle(countStyle);

      Cell mmsSuccCell = row.createCell(8);
      mmsSuccCell.setCellValue(stat.getMmsSucc());
      mmsSuccCell.setCellStyle(countStyle);

      Cell totalCell = row.createCell(9);
      totalCell.setCellValue(stat.getTotalCount());
      totalCell.setCellStyle(countStyle);
    }
  }

  /** 3. 설문조사 상세 시트 */
  private void addSurveyDetailSheet(SXSSFWorkbook workbook, BillingStatsSearchRequest request) {
    Sheet sheet = workbook.createSheet(SHEET_SURVEY_DETAIL);

    CellStyle headerStyle = createHeaderStyle(workbook);
    CellStyle countStyle = createCountStyle(workbook);

    // 제목
    Row titleRow = sheet.createRow(0);
    titleRow.createCell(1).setCellValue("설문조사 발송 상세 내역");

    // 기간
    Row periodRow = sheet.createRow(2);
    periodRow
        .createCell(1)
        .setCellValue("조회기간: " + request.getStartDate() + " ~ " + request.getEndDate());

    // 헤더
    String[] headers = {"사용자 ID", "사용자명", "설문 총건수", "설문 성공", "성공률(%)"};
    Row headerRow = sheet.createRow(4);
    for (int i = 0; i < headers.length; i++) {
      Cell cell = headerRow.createCell(i + 1);
      cell.setCellValue(headers[i]);
      cell.setCellStyle(headerStyle);
    }

    // 데이터
    StatsSearchRequest statsRequest =
        StatsSearchRequest.builder()
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .serviceType("S")
            .build();
    List<UserSurveyStatsResponse> surveyStats = userStatisticsService.findSurveyStats(statsRequest);
    int rowIdx = 5;
    for (UserSurveyStatsResponse stat : surveyStats) {
      Row row = sheet.createRow(rowIdx++);
      row.createCell(1).setCellValue(stat.getUserId() != null ? stat.getUserId() : "");
      row.createCell(2).setCellValue(stat.getUserName() != null ? stat.getUserName() : "");

      Cell surveyCountCell = row.createCell(3);
      surveyCountCell.setCellValue(stat.getSurveyTotal());
      surveyCountCell.setCellStyle(countStyle);

      Cell surveySuccCell = row.createCell(4);
      surveySuccCell.setCellValue(stat.getSurveySucc());
      surveySuccCell.setCellStyle(countStyle);

      Cell rateCell = row.createCell(5);
      rateCell.setCellValue(stat.getSuccessRate());
    }
  }

  /** 4. 사용자별 요금 내역 시트 */
  private void addUserHistorySheets(
      SXSSFWorkbook workbook, BillingStatsSearchRequest request, List<String> targetUserIds) {
    if (targetUserIds == null || targetUserIds.isEmpty()) {
      return;
    }

    CellStyle headerStyle = createHeaderStyle(workbook);
    CellStyle moneyStyle = createMoneyStyle(workbook);

    // 날짜 변환
    LocalDateTime startDateTime = LocalDate.parse(request.getStartDate()).atStartOfDay();
    LocalDateTime endDateTime = LocalDate.parse(request.getEndDate()).atTime(23, 59, 59);

    for (String userId : targetUserIds) {
      // userId는 실제로 userSeq임 (JWT subject로 seq 사용)
      Integer userSeq;
      try {
        userSeq = Integer.parseInt(userId);
      } catch (NumberFormatException e) {
        log.warn("잘못된 사용자 식별자로 스킵: userId={}", userId);
        continue;
      }

      // 사용자별 거래 내역 조회 (userSeq 사용)
      List<Transaction> transactions =
          transactionMapper.selectByDateRange(userSeq, startDateTime, endDateTime);

      // 사용 내역이 있는 경우만 시트 생성
      boolean hasUsableData =
          transactions.stream().anyMatch(tx -> !"CHARGE".equals(tx.getTxType()));

      if (!hasUsableData) {
        continue;
      }

      String sheetName = safeSheetName(SHEET_USER_HISTORY + userId + ")");
      Sheet sheet = workbook.createSheet(sheetName);

      // 제목
      Row titleRow = sheet.createRow(0);
      titleRow.createCell(0).setCellValue("요금 내역");

      // 사용자 ID
      Row userRow = sheet.createRow(2);
      userRow.createCell(0).setCellValue(userId);

      // 기간
      Row periodRow = sheet.createRow(3);
      periodRow
          .createCell(0)
          .setCellValue("조회기간: " + request.getStartDate() + " ~ " + request.getEndDate());

      // 헤더
      String[] headers = {"거래일시", "거래유형", "서비스", "금액", "잔액", "메모"};
      Row headerRow = sheet.createRow(5);
      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      // 데이터
      int rowIdx = 6;
      for (Transaction tx : transactions) {
        Row row = sheet.createRow(rowIdx++);

        row.createCell(0)
            .setCellValue(
                tx.getRegDate() != null ? tx.getRegDate().format(DATETIME_FORMATTER) : "");
        row.createCell(1).setCellValue(getTxTypeName(tx.getTxType()));
        row.createCell(2).setCellValue(getServiceTypeName(tx.getServiceId()));

        Cell amountCell = row.createCell(3);
        amountCell.setCellValue(tx.getAmount() != null ? tx.getAmount().doubleValue() : 0);
        amountCell.setCellStyle(moneyStyle);

        Cell balanceCell = row.createCell(4);
        balanceCell.setCellValue(
            tx.getBalanceAfter() != null ? tx.getBalanceAfter().doubleValue() : 0);
        balanceCell.setCellStyle(moneyStyle);

        row.createCell(5).setCellValue(tx.getComment() != null ? tx.getComment() : "");
      }
    }
  }

  // ==================== Helper Methods ====================

  private CellStyle createHeaderStyle(SXSSFWorkbook workbook) {
    CellStyle style = workbook.createCellStyle();
    Font font = workbook.createFont();
    font.setBold(true);
    style.setFont(font);
    style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    style.setBorderBottom(BorderStyle.THIN);
    style.setBorderTop(BorderStyle.THIN);
    style.setBorderLeft(BorderStyle.THIN);
    style.setBorderRight(BorderStyle.THIN);
    return style;
  }

  private CellStyle createCountStyle(SXSSFWorkbook workbook) {
    CellStyle style = workbook.createCellStyle();
    style.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
    return style;
  }

  private CellStyle createMoneyStyle(SXSSFWorkbook workbook) {
    CellStyle style = workbook.createCellStyle();
    style.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
    return style;
  }

  private String safeSheetName(String name) {
    // Excel 시트 이름 제한 (31자, 특수문자 제거)
    String safe = name.replaceAll("[\\[\\]\\*\\?/\\\\:]", "_");
    return safe.length() > 31 ? safe.substring(0, 31) : safe;
  }

  private String getTxTypeName(String txType) {
    if (txType == null) return "";
    return switch (txType) {
      case "CHARGE" -> "충전";
      case "DEDUCT" -> "차감";
      case "REFUND" -> "환불";
      case "GRANT" -> "지급";
      case "EXPIRE" -> "만료";
      default -> txType;
    };
  }

  private String getServiceTypeName(String serviceId) {
    if (serviceId == null) return "";
    return switch (serviceId) {
      case "msg_sms" -> "SMS";
      case "msg_lms" -> "LMS";
      case "msg_mms" -> "MMS";
      case "survey" -> "설문조사";
      case "qr_code" -> "QR코드";
      default -> serviceId;
    };
  }

  private byte[] toByteArray(SXSSFWorkbook workbook) throws Exception {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      workbook.write(out);
      return out.toByteArray();
    }
  }
}
