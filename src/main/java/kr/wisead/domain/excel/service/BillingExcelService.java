package kr.wisead.domain.excel.service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.payment.dto.BillingStatsSearchRequest;
import kr.wisead.domain.payment.entity.Transaction;
import kr.wisead.domain.payment.service.StandardRateService;
import kr.wisead.domain.statistics.dto.*;
import kr.wisead.domain.statistics.service.StatisticsService;
import kr.wisead.domain.statistics.service.UserStatisticsService;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.TransactionMapper;
import kr.wisead.mapper.primary.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

/** 과금 통계 Excel 생성 서비스 - 레거시 호환 형식 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingExcelService {

  private final StatisticsService statisticsService;
  private final UserStatisticsService userStatisticsService;
  private final StandardRateService standardRateService;
  private final TransactionMapper transactionMapper;
  private final UserMapper userMapper;
  private final UserIdResolver userIdResolver;

  private static final DateTimeFormatter DATETIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  // Comment 파싱 패턴: "SMS: 500건 성공: 480건" 또는 "SMS: 500건"
  private static final Pattern COMMENT_COUNT_PATTERN =
      Pattern.compile("^(.+?):\\s*(\\d+)건(?:\\s+(.+))?$");

  /** 과금 통계 Excel 생성 */
  public byte[] generateBillingExcel(
      BillingStatsSearchRequest request, String userId, List<String> targetUserIds) {
    try (SXSSFWorkbook workbook = new SXSSFWorkbook()) {
      addUsageSummarySheet(workbook, request);
      addMessageDetailSheet(workbook, request);
      addSurveyDetailSheet(workbook, request);
      addUserHistorySheets(workbook, request, targetUserIds);
      return toByteArray(workbook);
    } catch (Exception e) {
      log.error("과금 통계 Excel 생성 실패", e);
      throw new RuntimeException("Excel 파일 생성에 실패했습니다.", e);
    }
  }

  // ==================== Sheet 1: 사용내역 ====================

  private void addUsageSummarySheet(SXSSFWorkbook workbook, BillingStatsSearchRequest request) {
    Sheet sheet = workbook.createSheet("사용내역");

    CellStyle titleStyle = titleStyle(workbook);
    CellStyle headerStyle = headerStyle(workbook);
    CellStyle borderStyle = borderStyle(workbook);
    CellStyle numBorderStyle = numBorderStyle(workbook);

    // Row 0: 제목 "WiseAd 사용내역" (병합 0~5열, 17pt)
    Row r0 = sheet.createRow(0);
    Cell titleCell = r0.createCell(0);
    titleCell.setCellValue("WiseAd 사용내역");
    titleCell.setCellStyle(titleStyle);
    sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

    // Row 1: "(단위: 원, VAT포함)" 우측 정렬
    CellStyle rightStyle = workbook.createCellStyle();
    rightStyle.setAlignment(HorizontalAlignment.RIGHT);
    Row r1 = sheet.createRow(1);
    Cell unitCell = r1.createCell(4);
    unitCell.setCellValue("(단위: 원, VAT포함)");
    unitCell.setCellStyle(rightStyle);
    sheet.addMergedRegion(new CellRangeAddress(1, 1, 4, 5));

    // Row 2: 기간
    Row r2 = sheet.createRow(2);
    r2.createCell(0).setCellValue("조회기간: " + request.getStartDate() + " ~ " + request.getEndDate());

    // Row 4: 헤더
    String[] headers = {"서비스 종류", "구분", "건수", "단가", "사용료", "비고"};
    Row hr = sheet.createRow(4);
    for (int i = 0; i < headers.length; i++) {
      Cell c = hr.createCell(i);
      c.setCellValue(headers[i]);
      c.setCellStyle(headerStyle);
    }

    // 데이터 - UsageSummaryResponse 사용
    StatsSearchRequest statsReq =
        StatsSearchRequest.builder()
            .userId(request.getUserId())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .build();
    List<UsageSummaryResponse> summaries = statisticsService.getUsageSummary(statsReq);

    int rowIdx = 5;
    for (UsageSummaryResponse s : summaries) {
      Row row = sheet.createRow(rowIdx++);
      setCellWithStyle(row, 0, nvl(s.getServiceTypeName()), borderStyle);
      setCellWithStyle(row, 1, nvl(s.getCategory()), borderStyle);
      makeNumberCell(row, 2, s.getCount(), numBorderStyle);
      makeNumberCell(
          row, 3, s.getUnitPrice() != null ? s.getUnitPrice().longValue() : 0, numBorderStyle);
      BigDecimal amt = s.getAmount() != null ? s.getAmount() : s.calculateAmount();
      makeNumberCell(row, 4, amt.longValue(), numBorderStyle);
      setCellWithStyle(row, 5, nvl(s.getRemarks()), borderStyle);
    }

    // 컬럼 너비
    sheet.setColumnWidth(0, 5000);
    sheet.setColumnWidth(1, 3000);
    sheet.setColumnWidth(2, 3500);
    sheet.setColumnWidth(3, 3500);
    sheet.setColumnWidth(4, 5000);
    sheet.setColumnWidth(5, 5000);
  }

  // ==================== Sheet 2: 상세-와이즈애드(메시지) ====================

  private void addMessageDetailSheet(SXSSFWorkbook workbook, BillingStatsSearchRequest request) {
    Sheet sheet = workbook.createSheet("상세-와이즈애드(메시지)");

    CellStyle titleStyle = titleStyle(workbook);
    CellStyle headerStyle = headerStyle(workbook);
    CellStyle borderStyle = borderStyle(workbook);
    CellStyle numBorderStyle = numBorderStyle(workbook);
    CellStyle pinkStyle = pinkBorderStyle(workbook);
    CellStyle pinkNumStyle = pinkNumBorderStyle(workbook);

    // 제목 (0~1행, 0~8열 병합)
    Row r0 = sheet.createRow(0);
    Cell titleCell = r0.createCell(0);
    titleCell.setCellValue("와이즈애드 메시지");
    titleCell.setCellStyle(titleStyle);
    sheet.createRow(1);
    sheet.addMergedRegion(new CellRangeAddress(0, 1, 0, 8));

    // 2단 병합 헤더 (행 2-3)
    Row hr1 = sheet.createRow(2);
    Row hr2 = sheet.createRow(3);

    // 회사명 (2-3행 병합)
    setHeaderCell(hr1, 0, "회사명", headerStyle);
    setHeaderCell(hr2, 0, "", headerStyle);
    sheet.addMergedRegion(new CellRangeAddress(2, 3, 0, 0));

    // 아이디 (2-3행 병합)
    setHeaderCell(hr1, 1, "아이디", headerStyle);
    setHeaderCell(hr2, 1, "", headerStyle);
    sheet.addMergedRegion(new CellRangeAddress(2, 3, 1, 1));

    // SMS (2행 2-3열 병합)
    setHeaderCell(hr1, 2, "SMS", headerStyle);
    setHeaderCell(hr1, 3, "", headerStyle);
    sheet.addMergedRegion(new CellRangeAddress(2, 2, 2, 3));
    setHeaderCell(hr2, 2, "전송", headerStyle);
    setHeaderCell(hr2, 3, "성공", headerStyle);

    // LMS (2행 4-5열 병합)
    setHeaderCell(hr1, 4, "LMS", headerStyle);
    setHeaderCell(hr1, 5, "", headerStyle);
    sheet.addMergedRegion(new CellRangeAddress(2, 2, 4, 5));
    setHeaderCell(hr2, 4, "전송", headerStyle);
    setHeaderCell(hr2, 5, "성공", headerStyle);

    // MMS (2행 6-7열 병합)
    setHeaderCell(hr1, 6, "MMS", headerStyle);
    setHeaderCell(hr1, 7, "", headerStyle);
    sheet.addMergedRegion(new CellRangeAddress(2, 2, 6, 7));
    setHeaderCell(hr2, 6, "전송", headerStyle);
    setHeaderCell(hr2, 7, "성공", headerStyle);

    // 사용금액 (2-3행 병합)
    setHeaderCell(hr1, 8, "사용금액", headerStyle);
    setHeaderCell(hr2, 8, "", headerStyle);
    sheet.addMergedRegion(new CellRangeAddress(2, 3, 8, 8));

    // 단가 조회
    BigDecimal smsRate = standardRateService.getStandardRateWithVat("msg_sms");
    BigDecimal lmsRate = standardRateService.getStandardRateWithVat("msg_lms");
    BigDecimal mmsRate = standardRateService.getStandardRateWithVat("msg_mms");

    // 데이터
    StatsSearchRequest statsReq =
        StatsSearchRequest.builder()
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .serviceType("M")
            .build();
    List<UserMsgStatsResponse> msgStats = userStatisticsService.findMsgStats(statsReq);

    int rowIdx = 4;
    long sumSmsTotal = 0, sumSmsSucc = 0, sumLmsTotal = 0, sumLmsSucc = 0;
    long sumMmsTotal = 0, sumMmsSucc = 0;
    BigDecimal sumAmount = BigDecimal.ZERO;

    for (UserMsgStatsResponse stat : msgStats) {
      Row row = sheet.createRow(rowIdx++);

      // 회사명 조회
      String corpName = findCorpName(stat.getUserId());
      boolean isPink = corpName != null && corpName.contains("모바일이앤엠애드");
      CellStyle ts = isPink ? pinkStyle : borderStyle;
      CellStyle ns = isPink ? pinkNumStyle : numBorderStyle;

      setCellWithStyle(row, 0, nvl(corpName), ts);
      setCellWithStyle(row, 1, nvl(stat.getUserId()), ts);
      makeNumberCell(row, 2, stat.getSmsTotal(), ns);
      makeNumberCell(row, 3, stat.getSmsSucc(), ns);
      makeNumberCell(row, 4, stat.getLmsTotal(), ns);
      makeNumberCell(row, 5, stat.getLmsSucc(), ns);
      makeNumberCell(row, 6, stat.getMmsTotal(), ns);
      makeNumberCell(row, 7, stat.getMmsSucc(), ns);

      // 사용금액 = 성공건수 × 단가
      BigDecimal amount = calcMsgAmount(stat, smsRate, lmsRate, mmsRate);
      makeMoneyCell(row, 8, amount, ns);

      sumSmsTotal += stat.getSmsTotal();
      sumSmsSucc += stat.getSmsSucc();
      sumLmsTotal += stat.getLmsTotal();
      sumLmsSucc += stat.getLmsSucc();
      sumMmsTotal += stat.getMmsTotal();
      sumMmsSucc += stat.getMmsSucc();
      sumAmount = sumAmount.add(amount);
    }

    // 합계 행
    CellStyle sumStyle = sumStyle(workbook);
    CellStyle sumNumStyle = sumNumStyle(workbook);
    Row sumRow = sheet.createRow(rowIdx);
    setCellWithStyle(sumRow, 0, "합계", sumStyle);
    setCellWithStyle(sumRow, 1, "", sumStyle);
    makeNumberCell(sumRow, 2, sumSmsTotal, sumNumStyle);
    makeNumberCell(sumRow, 3, sumSmsSucc, sumNumStyle);
    makeNumberCell(sumRow, 4, sumLmsTotal, sumNumStyle);
    makeNumberCell(sumRow, 5, sumLmsSucc, sumNumStyle);
    makeNumberCell(sumRow, 6, sumMmsTotal, sumNumStyle);
    makeNumberCell(sumRow, 7, sumMmsSucc, sumNumStyle);
    makeMoneyCell(sumRow, 8, sumAmount, sumNumStyle);

    // 컬럼 너비
    sheet.setColumnWidth(0, 5000);
    sheet.setColumnWidth(1, 4000);
    sheet.setColumnWidth(2, 3000);
    sheet.setColumnWidth(3, 3000);
    sheet.setColumnWidth(4, 3000);
    sheet.setColumnWidth(5, 3000);
    sheet.setColumnWidth(6, 3000);
    sheet.setColumnWidth(7, 3000);
    sheet.setColumnWidth(8, 5000);
  }

  // ==================== Sheet 3: 상세-와이즈애드(설문조사) ====================

  private void addSurveyDetailSheet(SXSSFWorkbook workbook, BillingStatsSearchRequest request) {
    Sheet sheet = workbook.createSheet("상세-와이즈애드(설문조사)");

    CellStyle titleStyle = titleStyle(workbook);
    CellStyle headerStyle = headerStyle(workbook);
    CellStyle borderStyle = borderStyle(workbook);
    CellStyle numBorderStyle = numBorderStyle(workbook);

    // 제목 (0~1행, 0~6열 병합)
    Row r0 = sheet.createRow(0);
    Cell titleCell = r0.createCell(0);
    titleCell.setCellValue("와이즈애드 설문조사");
    titleCell.setCellStyle(titleStyle);
    sheet.createRow(1);
    sheet.addMergedRegion(new CellRangeAddress(0, 1, 0, 6));

    // 2단 병합 헤더 (행 2-3)
    Row hr1 = sheet.createRow(2);
    Row hr2 = sheet.createRow(3);

    // 회사명 (2-3행 병합)
    setHeaderCell(hr1, 0, "회사명", headerStyle);
    setHeaderCell(hr2, 0, "", headerStyle);
    sheet.addMergedRegion(new CellRangeAddress(2, 3, 0, 0));

    // 아이디 (2-3행 병합)
    setHeaderCell(hr1, 1, "아이디", headerStyle);
    setHeaderCell(hr2, 1, "", headerStyle);
    sheet.addMergedRegion(new CellRangeAddress(2, 3, 1, 1));

    // 설문조사 (2행 2-3열 병합)
    setHeaderCell(hr1, 2, "설문조사", headerStyle);
    setHeaderCell(hr1, 3, "", headerStyle);
    sheet.addMergedRegion(new CellRangeAddress(2, 2, 2, 3));
    setHeaderCell(hr2, 2, "전송", headerStyle);
    setHeaderCell(hr2, 3, "성공", headerStyle);

    // QR코드 (2행 4-5열 병합)
    setHeaderCell(hr1, 4, "QR코드", headerStyle);
    setHeaderCell(hr1, 5, "", headerStyle);
    sheet.addMergedRegion(new CellRangeAddress(2, 2, 4, 5));
    setHeaderCell(hr2, 4, "이벤트수", headerStyle);
    setHeaderCell(hr2, 5, "방문횟수", headerStyle);

    // 사용금액 (2-3행 병합)
    setHeaderCell(hr1, 6, "사용금액", headerStyle);
    setHeaderCell(hr2, 6, "", headerStyle);
    sheet.addMergedRegion(new CellRangeAddress(2, 3, 6, 6));

    // 단가 조회
    BigDecimal surveyRate = standardRateService.getStandardRateWithVat("survey");
    BigDecimal qrRate = standardRateService.getStandardRateWithVat("qr_code");

    // 설문 통계
    StatsSearchRequest surveyReq =
        StatsSearchRequest.builder()
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .serviceType("S")
            .build();
    List<UserSurveyStatsResponse> surveyStats = userStatisticsService.findSurveyStats(surveyReq);

    // QR 통계
    StatsSearchRequest qrReq =
        StatsSearchRequest.builder()
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .serviceType("Q")
            .build();
    List<UserQrStatsResponse> qrStats = userStatisticsService.findQrStats(qrReq);

    // QR 통계를 userId별 Map으로
    Map<String, UserQrStatsResponse> qrMap = new HashMap<>();
    for (UserQrStatsResponse qr : qrStats) {
      qrMap.merge(
          qr.getUserId(),
          qr,
          (a, b) ->
              UserQrStatsResponse.builder()
                  .userId(a.getUserId())
                  .userName(a.getUserName())
                  .eventCount(a.getEventCount() + b.getEventCount())
                  .visitCount(a.getVisitCount() + b.getVisitCount())
                  .build());
    }

    // 모든 userId 수집 (설문 + QR, 순서 유지)
    Set<String> allUserIds = new LinkedHashSet<>();
    for (UserSurveyStatsResponse s : surveyStats) allUserIds.add(s.getUserId());
    for (UserQrStatsResponse q : qrStats) allUserIds.add(q.getUserId());

    // 설문 통계를 userId별 Map으로
    Map<String, UserSurveyStatsResponse> surveyMap = new HashMap<>();
    for (UserSurveyStatsResponse s : surveyStats) {
      surveyMap.put(s.getUserId(), s);
    }

    int rowIdx = 4;
    long sumSurveyTotal = 0, sumSurveySucc = 0, sumEventCount = 0, sumVisitCount = 0;
    BigDecimal sumAmount = BigDecimal.ZERO;

    for (String uid : allUserIds) {
      Row row = sheet.createRow(rowIdx++);
      String corpName = findCorpName(uid);

      UserSurveyStatsResponse sv = surveyMap.get(uid);
      UserQrStatsResponse qr = qrMap.get(uid);

      int surveyTotal = sv != null ? sv.getSurveyTotal() : 0;
      int surveySucc = sv != null ? sv.getSurveySucc() : 0;
      int eventCount = qr != null ? qr.getEventCount() : 0;
      int visitCount = qr != null ? qr.getVisitCount() : 0;

      setCellWithStyle(row, 0, nvl(corpName), borderStyle);
      setCellWithStyle(row, 1, nvl(uid), borderStyle);
      makeNumberCell(row, 2, surveyTotal, numBorderStyle);
      makeNumberCell(row, 3, surveySucc, numBorderStyle);
      makeNumberCell(row, 4, eventCount, numBorderStyle);
      makeNumberCell(row, 5, visitCount, numBorderStyle);

      // 사용금액 = 설문 성공 × 설문단가 + QR 방문횟수 × QR단가
      BigDecimal amount =
          surveyRate
              .multiply(BigDecimal.valueOf(surveySucc))
              .add(qrRate.multiply(BigDecimal.valueOf(visitCount)));
      makeMoneyCell(row, 6, amount, numBorderStyle);

      sumSurveyTotal += surveyTotal;
      sumSurveySucc += surveySucc;
      sumEventCount += eventCount;
      sumVisitCount += visitCount;
      sumAmount = sumAmount.add(amount);
    }

    // 합계 행
    CellStyle sumStyle = sumStyle(workbook);
    CellStyle sumNumStyle = sumNumStyle(workbook);
    Row sumRow = sheet.createRow(rowIdx);
    setCellWithStyle(sumRow, 0, "합계", sumStyle);
    setCellWithStyle(sumRow, 1, "", sumStyle);
    makeNumberCell(sumRow, 2, sumSurveyTotal, sumNumStyle);
    makeNumberCell(sumRow, 3, sumSurveySucc, sumNumStyle);
    makeNumberCell(sumRow, 4, sumEventCount, sumNumStyle);
    makeNumberCell(sumRow, 5, sumVisitCount, sumNumStyle);
    makeMoneyCell(sumRow, 6, sumAmount, sumNumStyle);

    // 컬럼 너비
    sheet.setColumnWidth(0, 5000);
    sheet.setColumnWidth(1, 4000);
    sheet.setColumnWidth(2, 3000);
    sheet.setColumnWidth(3, 3000);
    sheet.setColumnWidth(4, 3500);
    sheet.setColumnWidth(5, 3500);
    sheet.setColumnWidth(6, 5000);
  }

  // ==================== Sheet 4: 사용자별 요금 내역 ====================

  private void addUserHistorySheets(
      SXSSFWorkbook workbook, BillingStatsSearchRequest request, List<String> targetUserIds) {
    if (targetUserIds == null || targetUserIds.isEmpty()) return;

    CellStyle headerStyle = headerStyle(workbook);
    CellStyle borderStyle = borderStyle(workbook);
    CellStyle numBorderStyle = numBorderStyle(workbook);

    LocalDateTime startDt = LocalDate.parse(request.getStartDate()).atStartOfDay();
    LocalDateTime endDt = LocalDate.parse(request.getEndDate()).atTime(23, 59, 59);

    for (String uid : targetUserIds) {
      Integer userSeq = resolveUserSeq(uid);
      if (userSeq == null) {
        log.warn("사용자 시퀀스를 찾을 수 없어 스킵: {}", uid);
        continue;
      }

      List<Transaction> transactions = transactionMapper.selectByDateRange(userSeq, startDt, endDt);

      // 사용 내역이 있는 경우만 시트 생성
      if (transactions.stream()
          .noneMatch(tx -> !Transaction.TX_TYPE_CHARGE.equals(tx.getTxType()))) {
        continue;
      }

      // 로그인 ID로 시트명 표시
      String loginId = resolveLoginId(uid, userSeq);
      String sheetName = safeSheetName("요금 내역(" + loginId + ")");
      Sheet sheet = workbook.createSheet(sheetName);

      // 제목
      Row r0 = sheet.createRow(0);
      r0.createCell(0).setCellValue("요금 내역");

      // 사용자 ID
      Row r2 = sheet.createRow(2);
      r2.createCell(0).setCellValue(loginId);

      // 기간
      Row r3 = sheet.createRow(3);
      r3.createCell(0)
          .setCellValue("조회기간: " + request.getStartDate() + " ~ " + request.getEndDate());

      // 헤더 (행 5)
      String[] headers = {"No", "내용", "상세내역", "건수", "충전금액", "사용금액", "잔액", "발생일시"};
      Row hr = sheet.createRow(5);
      for (int i = 0; i < headers.length; i++) {
        Cell c = hr.createCell(i);
        c.setCellValue(headers[i]);
        c.setCellStyle(headerStyle);
      }

      // 데이터 (행 6~)
      int rowIdx = 6;
      int no = 1;
      for (Transaction tx : transactions) {
        Row row = sheet.createRow(rowIdx++);

        // No
        makeNumberCell(row, 0, no++, borderStyle);

        // Comment 파싱 → [내용, 상세내역, 건수]
        String[] parsed = parseComment(tx.getComment());
        setCellWithStyle(row, 1, parsed[0], borderStyle);
        setCellWithStyle(row, 2, parsed[1], borderStyle);

        // 건수
        if (!parsed[2].isEmpty()) {
          try {
            makeNumberCell(row, 3, Long.parseLong(parsed[2]), numBorderStyle);
          } catch (NumberFormatException e) {
            setCellWithStyle(row, 3, parsed[2], borderStyle);
          }
        } else {
          setCellWithStyle(row, 3, "", borderStyle);
        }

        // 충전금액 / 사용금액 분리
        BigDecimal amount = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
        boolean isCharge =
            Transaction.TX_TYPE_CHARGE.equals(tx.getTxType())
                || Transaction.TX_TYPE_REFUND.equals(tx.getTxType())
                || "GRANT".equals(tx.getTxType());

        if (isCharge) {
          makeMoneyCell(row, 4, amount, numBorderStyle);
          setCellWithStyle(row, 5, "", borderStyle);
        } else {
          setCellWithStyle(row, 4, "", borderStyle);
          makeMoneyCell(row, 5, amount, numBorderStyle);
        }

        // 잔액
        BigDecimal balance = tx.getBalanceAfter() != null ? tx.getBalanceAfter() : BigDecimal.ZERO;
        makeMoneyCell(row, 6, balance, numBorderStyle);

        // 발생일시
        String dateStr = tx.getRegDate() != null ? tx.getRegDate().format(DATETIME_FORMATTER) : "";
        setCellWithStyle(row, 7, dateStr, borderStyle);
      }

      // 컬럼 너비
      sheet.setColumnWidth(0, 1500);
      sheet.setColumnWidth(1, 4000);
      sheet.setColumnWidth(2, 5000);
      sheet.setColumnWidth(3, 3000);
      sheet.setColumnWidth(4, 4000);
      sheet.setColumnWidth(5, 4000);
      sheet.setColumnWidth(6, 4000);
      sheet.setColumnWidth(7, 5500);
    }
  }

  // ==================== Comment 파싱 ====================

  /**
   * Comment 파싱: "SMS: 500건 성공: 480건" → ["SMS", "성공: 480건", "500"]
   *
   * @return [내용, 상세내역, 건수]
   */
  private String[] parseComment(String comment) {
    if (comment == null || comment.isBlank()) {
      return new String[] {"", "", ""};
    }
    Matcher m = COMMENT_COUNT_PATTERN.matcher(comment.trim());
    if (m.matches()) {
      String content = m.group(1).trim();
      String count = m.group(2);
      String detail = m.group(3) != null ? m.group(3).trim() : "";
      return new String[] {content, detail, count};
    }
    // 패턴 불일치 시 전체를 내용으로
    return new String[] {comment, "", ""};
  }

  // ==================== 사용자 조회 헬퍼 ====================

  private String findCorpName(String userId) {
    if (userId == null) return null;
    try {
      return userMapper.findByUserId(userId).map(User::getCorpName).orElse(null);
    } catch (Exception e) {
      log.warn("회사명 조회 실패: userId={}", userId, e);
      return null;
    }
  }

  /** ID를 userSeq로 변환 (숫자면 그대로, 아니면 userId로 조회) */
  private Integer resolveUserSeq(String id) {
    try {
      return Integer.parseInt(id);
    } catch (NumberFormatException e) {
      return userIdResolver.toUserSeq(id);
    }
  }

  /** 로그인 ID 확인 (숫자면 userSeq → userId 변환) */
  private String resolveLoginId(String id, Integer userSeq) {
    try {
      Integer.parseInt(id);
      String loginId = userIdResolver.toUserId(userSeq);
      return loginId != null ? loginId : id;
    } catch (NumberFormatException e) {
      return id;
    }
  }

  // ==================== 스타일 헬퍼 ====================

  private CellStyle titleStyle(SXSSFWorkbook wb) {
    CellStyle style = wb.createCellStyle();
    Font font = wb.createFont();
    font.setBold(true);
    font.setFontHeightInPoints((short) 17);
    style.setFont(font);
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    return style;
  }

  private CellStyle headerStyle(SXSSFWorkbook wb) {
    CellStyle style = wb.createCellStyle();
    Font font = wb.createFont();
    font.setBold(true);
    style.setFont(font);
    style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    style.setAlignment(HorizontalAlignment.CENTER);
    style.setVerticalAlignment(VerticalAlignment.CENTER);
    applyThinBorder(style);
    return style;
  }

  private CellStyle borderStyle(SXSSFWorkbook wb) {
    CellStyle style = wb.createCellStyle();
    applyThinBorder(style);
    return style;
  }

  private CellStyle numBorderStyle(SXSSFWorkbook wb) {
    CellStyle style = wb.createCellStyle();
    style.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
    applyThinBorder(style);
    return style;
  }

  private CellStyle pinkBorderStyle(SXSSFWorkbook wb) {
    CellStyle style = wb.createCellStyle();
    style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    applyThinBorder(style);
    return style;
  }

  private CellStyle pinkNumBorderStyle(SXSSFWorkbook wb) {
    CellStyle style = wb.createCellStyle();
    style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
    style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
    style.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
    applyThinBorder(style);
    return style;
  }

  private CellStyle sumStyle(SXSSFWorkbook wb) {
    CellStyle style = wb.createCellStyle();
    Font font = wb.createFont();
    font.setBold(true);
    style.setFont(font);
    applyThinBorder(style);
    return style;
  }

  private CellStyle sumNumStyle(SXSSFWorkbook wb) {
    CellStyle style = wb.createCellStyle();
    Font font = wb.createFont();
    font.setBold(true);
    style.setFont(font);
    style.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
    applyThinBorder(style);
    return style;
  }

  private void applyThinBorder(CellStyle style) {
    style.setBorderTop(BorderStyle.THIN);
    style.setBorderBottom(BorderStyle.THIN);
    style.setBorderLeft(BorderStyle.THIN);
    style.setBorderRight(BorderStyle.THIN);
  }

  // ==================== 셀 헬퍼 ====================

  private void setHeaderCell(Row row, int col, String value, CellStyle style) {
    Cell cell = row.createCell(col);
    cell.setCellValue(value);
    cell.setCellStyle(style);
  }

  private void setCellWithStyle(Row row, int col, String value, CellStyle style) {
    Cell cell = row.createCell(col);
    cell.setCellValue(value);
    cell.setCellStyle(style);
  }

  private void makeNumberCell(Row row, int col, long value, CellStyle style) {
    Cell cell = row.createCell(col);
    cell.setCellValue(value);
    cell.setCellStyle(style);
  }

  private void makeMoneyCell(Row row, int col, BigDecimal value, CellStyle style) {
    Cell cell = row.createCell(col);
    cell.setCellValue(value != null ? value.doubleValue() : 0);
    cell.setCellStyle(style);
  }

  private BigDecimal calcMsgAmount(
      UserMsgStatsResponse stat, BigDecimal smsRate, BigDecimal lmsRate, BigDecimal mmsRate) {
    return smsRate
        .multiply(BigDecimal.valueOf(stat.getSmsSucc()))
        .add(lmsRate.multiply(BigDecimal.valueOf(stat.getLmsSucc())))
        .add(mmsRate.multiply(BigDecimal.valueOf(stat.getMmsSucc())));
  }

  private String nvl(String s) {
    return s != null ? s : "";
  }

  private String safeSheetName(String name) {
    String safe = name.replaceAll("[\\[\\]\\*\\?/\\\\:]", "_");
    return safe.length() > 31 ? safe.substring(0, 31) : safe;
  }

  private byte[] toByteArray(SXSSFWorkbook workbook) throws Exception {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      workbook.write(out);
      return out.toByteArray();
    }
  }
}
