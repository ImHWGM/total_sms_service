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
import kr.wisead.domain.payment.service.UserServiceRateService;
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
  private final UserServiceRateService userServiceRateService;
  private final TransactionMapper transactionMapper;
  private final UserMapper userMapper;
  private final UserIdResolver userIdResolver;

  private static final DateTimeFormatter DATETIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  // Comment 파싱 패턴: "SMS: 500건 성공: 480건" 또는 "SMS: 500건"
  private static final Pattern COMMENT_COUNT_PATTERN =
      Pattern.compile("^(.+?):\\s*(\\d+)건(?:\\s+(.+))?$");

  /** 핑크 배경색 적용 대상 회사명 */
  private static final String PINK_HIGHLIGHT_CORP = "모바일이앤엠애드";

  /** QR코드 추가과금 단위 (방문횟수 기준) */
  private static final int QR_VISITS_PER_BLOCK = 3000;

  private static final String[] ALL_SERVICE_IDS =
      {"msg_sms", "msg_lms", "msg_mms", "survey", "qr_code", "qr_code_extra"};

  /** 과금 통계 Excel 생성 */
  public byte[] generateBillingExcel(
      BillingStatsSearchRequest request, String userId, List<String> targetUserIds) {
    try (SXSSFWorkbook workbook = new SXSSFWorkbook()) {
      // 공유 데이터 한번만 조회
      StatsSearchRequest msgReq =
          StatsSearchRequest.builder()
              .startDate(request.getStartDate())
              .endDate(request.getEndDate())
              .serviceType("M")
              .build();
      List<UserMsgStatsResponse> msgStats = userStatisticsService.findMsgStats(msgReq);

      StatsSearchRequest surveyReq =
          StatsSearchRequest.builder()
              .startDate(request.getStartDate())
              .endDate(request.getEndDate())
              .serviceType("S")
              .build();
      List<UserSurveyStatsResponse> surveyStats = userStatisticsService.findSurveyStats(surveyReq);

      StatsSearchRequest qrReq =
          StatsSearchRequest.builder()
              .startDate(request.getStartDate())
              .endDate(request.getEndDate())
              .serviceType("Q")
              .build();
      List<UserQrStatsResponse> qrStats = userStatisticsService.findQrStats(qrReq);

      // 유저별 요금 캐시 구축 (N+1 방지)
      Map<String, Map<String, BigDecimal>> rateCache =
          buildUserRateCache(msgStats, surveyStats, qrStats);

      addUsageSummarySheet(workbook, request, msgStats, surveyStats, qrStats, rateCache);
      addMessageDetailSheet(workbook, request, msgStats, rateCache);
      addSurveyDetailSheet(workbook, request, surveyStats, qrStats, rateCache);
      addUserHistorySheets(workbook, request, targetUserIds);
      return toByteArray(workbook);
    } catch (Exception e) {
      log.error("과금 통계 Excel 생성 실패", e);
      throw new RuntimeException("Excel 파일 생성에 실패했습니다.", e);
    }
  }

  /** 모든 유저의 서비스별 요금을 한번에 캐시 */
  private Map<String, Map<String, BigDecimal>> buildUserRateCache(
      List<UserMsgStatsResponse> msgStats,
      List<UserSurveyStatsResponse> surveyStats,
      List<UserQrStatsResponse> qrStats) {
    Set<String> allUserIds = new LinkedHashSet<>();
    for (UserMsgStatsResponse s : msgStats) allUserIds.add(s.getUserId());
    for (UserSurveyStatsResponse s : surveyStats) allUserIds.add(s.getUserId());
    for (UserQrStatsResponse s : qrStats) allUserIds.add(s.getUserId());

    Map<String, Map<String, BigDecimal>> cache = new HashMap<>();
    for (String uid : allUserIds) {
      Integer userSeq = resolveUserSeq(uid);
      Map<String, BigDecimal> rates = new HashMap<>();
      for (String sid : ALL_SERVICE_IDS) {
        rates.put(
            sid,
            userSeq != null
                ? userServiceRateService.getEffectiveRate(userSeq, sid)
                : standardRateService.getStandardRateWithVat(sid));
      }
      cache.put(uid, rates);
    }
    return cache;
  }

  /** 캐시에서 유저별 요금 조회 */
  private BigDecimal getCachedRate(
      Map<String, Map<String, BigDecimal>> rateCache, String userId, String serviceId) {
    Map<String, BigDecimal> userRates = rateCache.get(userId);
    if (userRates != null) {
      BigDecimal rate = userRates.get(serviceId);
      if (rate != null) return rate;
    }
    return getEffectiveRate(userId, serviceId);
  }

  // ==================== Sheet 1: 사용내역 ====================

  private void addUsageSummarySheet(
      SXSSFWorkbook workbook,
      BillingStatsSearchRequest request,
      List<UserMsgStatsResponse> msgStats,
      List<UserSurveyStatsResponse> surveyStats,
      List<UserQrStatsResponse> qrStats,
      Map<String, Map<String, BigDecimal>> rateCache) {
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

    // 표준 단가 조회 (단가 컬럼 표시용)
    Map<String, BigDecimal> stdRateMap = new LinkedHashMap<>();
    stdRateMap.put("SMS", standardRateService.getStandardRateWithVat("msg_sms"));
    stdRateMap.put("LMS", standardRateService.getStandardRateWithVat("msg_lms"));
    stdRateMap.put("MMS", standardRateService.getStandardRateWithVat("msg_mms"));
    stdRateMap.put("설문", standardRateService.getStandardRateWithVat("survey"));
    stdRateMap.put("QR코드", standardRateService.getStandardRateWithVat("qr_code"));
    stdRateMap.put("QR코드 추가과금", standardRateService.getStandardRateWithVat("qr_code_extra"));

    // 유저별 메시지 통계로 건수/사용료 합산
    Map<String, Integer> countMap = new LinkedHashMap<>();
    Map<String, BigDecimal> amountMap = new LinkedHashMap<>();

    for (UserMsgStatsResponse stat : msgStats) {
      BigDecimal userSmsRate = getCachedRate(rateCache, stat.getUserId(), "msg_sms");
      BigDecimal userLmsRate = getCachedRate(rateCache, stat.getUserId(), "msg_lms");
      BigDecimal userMmsRate = getCachedRate(rateCache, stat.getUserId(), "msg_mms");
      countMap.merge("SMS", (int) stat.getSmsSucc(), Integer::sum);
      countMap.merge("LMS", (int) stat.getLmsSucc(), Integer::sum);
      countMap.merge("MMS", (int) stat.getMmsSucc(), Integer::sum);
      amountMap.merge("SMS", userSmsRate.multiply(BigDecimal.valueOf(stat.getSmsSucc())), BigDecimal::add);
      amountMap.merge("LMS", userLmsRate.multiply(BigDecimal.valueOf(stat.getLmsSucc())), BigDecimal::add);
      amountMap.merge("MMS", userMmsRate.multiply(BigDecimal.valueOf(stat.getMmsSucc())), BigDecimal::add);
    }

    // 유저별 설문/QR 통계로 건수/사용료 합산
    for (UserSurveyStatsResponse stat : surveyStats) {
      BigDecimal userSurveyRate = getCachedRate(rateCache, stat.getUserId(), "survey");
      countMap.merge("설문", stat.getSurveySucc(), Integer::sum);
      amountMap.merge("설문", userSurveyRate.multiply(BigDecimal.valueOf(stat.getSurveySucc())), BigDecimal::add);
    }

    // QR 통계를 userId별로 합산
    Map<String, int[]> qrByUser = new LinkedHashMap<>();
    for (UserQrStatsResponse qr : qrStats) {
      qrByUser.merge(qr.getUserId(), new int[] {qr.getEventCount(), qr.getVisitCount()},
          (a, b) -> new int[] {a[0] + b[0], a[1] + b[1]});
    }
    for (Map.Entry<String, int[]> entry : qrByUser.entrySet()) {
      String uid = entry.getKey();
      int eventCount = entry.getValue()[0];
      int visitCount = entry.getValue()[1];
      BigDecimal userQrRate = getCachedRate(rateCache, uid, "qr_code");
      BigDecimal userQrExtraRate = getCachedRate(rateCache, uid, "qr_code_extra");
      int extraBlocks =
          visitCount <= 0 ? 0 : ((visitCount + QR_VISITS_PER_BLOCK - 1) / QR_VISITS_PER_BLOCK - 1);
      countMap.merge("QR코드", eventCount, Integer::sum);
      countMap.merge("QR코드 추가과금", extraBlocks, Integer::sum);
      amountMap.merge("QR코드", userQrRate.multiply(BigDecimal.valueOf(eventCount)), BigDecimal::add);
      amountMap.merge("QR코드 추가과금", userQrExtraRate.multiply(BigDecimal.valueOf(extraBlocks)), BigDecimal::add);
    }

    // 카테고리별 비고 정의: {그룹, 카테고리명, 비고}
    String[][] categories = {
        {"문자메시지", "SMS", ""},
        {"문자메시지", "LMS", ""},
        {"문자메시지", "MMS", ""},
        {"설문조사", "설문", ""},
        {"설문조사", "QR코드", "기본조회 3,000건, 이후 3,000건당 추가과금"},
        {"설문조사", "QR코드 추가과금", ""},
    };

    CellStyle sumStyle = sumStyle(workbook);
    CellStyle sumNumStyle = sumNumStyle(workbook);

    int rowIdx = 5;
    String currentGroup = "";
    BigDecimal groupAmount = BigDecimal.ZERO;
    BigDecimal grandTotal = BigDecimal.ZERO;

    for (String[] cat : categories) {
      String group = cat[0];
      String category = cat[1];
      String remarks = cat[2];
      int count = countMap.getOrDefault(category, 0);
      BigDecimal unitPrice = stdRateMap.getOrDefault(category, BigDecimal.ZERO);
      BigDecimal amount = amountMap.getOrDefault(category, BigDecimal.ZERO);

      // 그룹 변경 시 이전 그룹 소계 출력
      if (!group.equals(currentGroup) && !currentGroup.isEmpty()) {
        Row subRow = sheet.createRow(rowIdx++);
        setCellWithStyle(subRow, 0, "", sumStyle);
        setCellWithStyle(subRow, 1, "소계", sumStyle);
        setCellWithStyle(subRow, 2, "", sumStyle);
        setCellWithStyle(subRow, 3, "", sumStyle);
        makeMoneyCell(subRow, 4, groupAmount, sumNumStyle);
        setCellWithStyle(subRow, 5, "", sumStyle);
        grandTotal = grandTotal.add(groupAmount);
        groupAmount = BigDecimal.ZERO;
      }

      Row row = sheet.createRow(rowIdx++);
      // 서비스 종류는 그룹 첫 행에만 표시
      String displayGroup = !group.equals(currentGroup) ? group : "";
      setCellWithStyle(row, 0, displayGroup, borderStyle);
      setCellWithStyle(row, 1, category, borderStyle);
      makeNumberCell(row, 2, count, numBorderStyle);
      makeMoneyCell(row, 3, unitPrice, numBorderStyle);
      makeMoneyCell(row, 4, amount, numBorderStyle);
      setCellWithStyle(row, 5, remarks, borderStyle);

      groupAmount = groupAmount.add(amount);
      currentGroup = group;
    }

    // 마지막 그룹 소계
    if (!currentGroup.isEmpty()) {
      Row subRow = sheet.createRow(rowIdx++);
      setCellWithStyle(subRow, 0, "", sumStyle);
      setCellWithStyle(subRow, 1, "소계", sumStyle);
      setCellWithStyle(subRow, 2, "", sumStyle);
      setCellWithStyle(subRow, 3, "", sumStyle);
      makeMoneyCell(subRow, 4, groupAmount, sumNumStyle);
      setCellWithStyle(subRow, 5, "", sumStyle);
      grandTotal = grandTotal.add(groupAmount);
    }

    // 합계 행
    Row totalRow = sheet.createRow(rowIdx);
    setCellWithStyle(totalRow, 0, "합계", sumStyle);
    setCellWithStyle(totalRow, 1, "", sumStyle);
    setCellWithStyle(totalRow, 2, "", sumStyle);
    setCellWithStyle(totalRow, 3, "", sumStyle);
    makeMoneyCell(totalRow, 4, grandTotal, sumNumStyle);
    setCellWithStyle(totalRow, 5, "", sumStyle);

    // 컬럼 너비
    sheet.setColumnWidth(0, 5000);
    sheet.setColumnWidth(1, 5000);
    sheet.setColumnWidth(2, 3500);
    sheet.setColumnWidth(3, 3500);
    sheet.setColumnWidth(4, 5000);
    sheet.setColumnWidth(5, 8000);
  }

  // ==================== Sheet 2: 상세-와이즈애드(메시지) ====================

  private void addMessageDetailSheet(
      SXSSFWorkbook workbook,
      BillingStatsSearchRequest request,
      List<UserMsgStatsResponse> msgStats,
      Map<String, Map<String, BigDecimal>> rateCache) {
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

    int rowIdx = 4;
    long sumSmsTotal = 0, sumSmsSucc = 0, sumLmsTotal = 0, sumLmsSucc = 0;
    long sumMmsTotal = 0, sumMmsSucc = 0;
    BigDecimal sumAmount = BigDecimal.ZERO;

    for (UserMsgStatsResponse stat : msgStats) {
      Row row = sheet.createRow(rowIdx++);

      // 회사명 조회
      String corpName = findCorpName(stat.getUserId());
      boolean isPink = corpName != null && corpName.contains(PINK_HIGHLIGHT_CORP);
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

      // 사용금액 = 성공건수 × 유저별 단가
      BigDecimal userSmsRate = getCachedRate(rateCache, stat.getUserId(), "msg_sms");
      BigDecimal userLmsRate = getCachedRate(rateCache, stat.getUserId(), "msg_lms");
      BigDecimal userMmsRate = getCachedRate(rateCache, stat.getUserId(), "msg_mms");
      BigDecimal amount = calcMsgAmount(stat, userSmsRate, userLmsRate, userMmsRate);
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

  private void addSurveyDetailSheet(
      SXSSFWorkbook workbook,
      BillingStatsSearchRequest request,
      List<UserSurveyStatsResponse> surveyStats,
      List<UserQrStatsResponse> qrStats,
      Map<String, Map<String, BigDecimal>> rateCache) {
    Sheet sheet = workbook.createSheet("상세-와이즈애드(설문조사)");

    CellStyle titleStyle = titleStyle(workbook);
    CellStyle headerStyle = headerStyle(workbook);
    CellStyle borderStyle = borderStyle(workbook);
    CellStyle numBorderStyle = numBorderStyle(workbook);
    CellStyle pinkStyle = pinkBorderStyle(workbook);
    CellStyle pinkNumStyle = pinkNumBorderStyle(workbook);

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

      boolean isPink = corpName != null && corpName.contains(PINK_HIGHLIGHT_CORP);
      CellStyle ts = isPink ? pinkStyle : borderStyle;
      CellStyle ns = isPink ? pinkNumStyle : numBorderStyle;

      setCellWithStyle(row, 0, nvl(corpName), ts);
      setCellWithStyle(row, 1, nvl(uid), ts);
      makeNumberCell(row, 2, surveyTotal, ns);
      makeNumberCell(row, 3, surveySucc, ns);
      makeNumberCell(row, 4, eventCount, ns);
      makeNumberCell(row, 5, visitCount, ns);

      // 사용금액 = 설문 성공 × 유저별 설문단가 + QR 이벤트수 × 유저별 QR기본단가 + QR 추가과금블록 × 유저별 QR추가단가
      // QR 추가과금: 기본 3,000건 포함, 이후 3,000건당 추가과금
      BigDecimal userSurveyRate = getCachedRate(rateCache, uid, "survey");
      BigDecimal userQrRate = getCachedRate(rateCache, uid, "qr_code");
      BigDecimal userQrExtraRate = getCachedRate(rateCache, uid, "qr_code_extra");
      int extraBlocks =
          visitCount <= 0 ? 0 : ((visitCount + QR_VISITS_PER_BLOCK - 1) / QR_VISITS_PER_BLOCK - 1);
      BigDecimal amount =
          userSurveyRate
              .multiply(BigDecimal.valueOf(surveySucc))
              .add(userQrRate.multiply(BigDecimal.valueOf(eventCount)))
              .add(userQrExtraRate.multiply(BigDecimal.valueOf(extraBlocks)));
      makeMoneyCell(row, 6, amount, ns);

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
    // targetUserIds가 null이면 전체 사용자 조회 (관리자)
    if (targetUserIds == null) {
      targetUserIds = userMapper.findAllUserIds();
    }
    if (targetUserIds.isEmpty()) return;

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

  /** 유저별 적용 요금 조회 (user_service_rate 우선, 없으면 standard_rate) */
  private BigDecimal getEffectiveRate(String userId, String serviceId) {
    Integer userSeq = resolveUserSeq(userId);
    if (userSeq != null) {
      return userServiceRateService.getEffectiveRate(userSeq, serviceId);
    }
    return standardRateService.getStandardRateWithVat(serviceId);
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
