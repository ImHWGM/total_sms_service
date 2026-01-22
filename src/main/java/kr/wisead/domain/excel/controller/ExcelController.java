package kr.wisead.domain.excel.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.admin.service.ActionLogService;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.excel.dto.SurveyExcelDownloadRequest;
import kr.wisead.domain.excel.service.BillingExcelService;
import kr.wisead.domain.excel.service.ExcelService;
import kr.wisead.domain.payment.dto.BillingStatsSearchRequest;
import kr.wisead.domain.statistics.dto.DailyStatsResponse;
import kr.wisead.domain.statistics.dto.StatsSearchRequest;
import kr.wisead.domain.statistics.service.StatisticsService;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.SurveyUserMapper;
import kr.wisead.mapper.primary.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Excel 다운로드/업로드 Controller */
@Slf4j
@RestController
@RequestMapping("/api/excel")
@RequiredArgsConstructor
public class ExcelController {

  private final ExcelService excelService;
  private final BillingExcelService billingExcelService;
  private final StatisticsService statisticsService;
  private final SurveyUserMapper surveyUserMapper;
  private final UserMapper userMapper;
  private final UserIdResolver userIdResolver;
  private final ActionLogService actionLogService;
  private final AdminService adminService;

  private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter DATETIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /** UserDetails에서 userId 추출 (null-safe) */
  private String extractUserId(UserDetails userDetails) {
    if (userDetails == null) {
      return null;
    }
    Integer userSeq = userIdResolver.fromJwtUsername(userDetails.getUsername());
    return userIdResolver.toUserId(userSeq);
  }

  /** 통계 Excel 다운로드 GET /api/excel/statistics/download?startDate=2025-01-01&endDate=2025-01-31 */
  @GetMapping("/statistics/download")
  public ResponseEntity<byte[]> downloadStatisticsExcel(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String serviceType) {

    String userId = extractUserId(userDetails);

    // 기본 날짜 설정
    LocalDate now = LocalDate.now();
    if (startDate == null || startDate.isEmpty()) {
      startDate = now.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE);
    }
    if (endDate == null || endDate.isEmpty()) {
      endDate = now.format(DateTimeFormatter.ISO_DATE);
    }

    // 통계 데이터 조회
    StatsSearchRequest request =
        StatsSearchRequest.builder()
            .userId(userId)
            .startDate(startDate)
            .endDate(endDate)
            .serviceType(serviceType)
            .build();

    List<DailyStatsResponse> stats = statisticsService.getDailyStats(request);

    // Excel 생성
    try (SXSSFWorkbook workbook = excelService.createWorkbook()) {
      Sheet sheet = excelService.createSheet(workbook, "일별통계");

      // 헤더 스타일
      CellStyle headerStyle = excelService.createHeaderStyle(workbook, 11, true, 192, 192, 192);

      // 헤더 생성
      List<String> headers = Arrays.asList("일자", "접수건수", "성공건수", "에러건수", "실패건수", "진행중", "대기");
      excelService.createHeaderRow(sheet, 0, headers, headerStyle);

      // 데이터 행 생성
      int rowNum = 1;
      for (DailyStatsResponse stat : stats) {
        excelService.createDataRow(
            sheet,
            rowNum++,
            Arrays.asList(
                stat.getDtStats(),
                stat.getInCnt(),
                stat.getSuccCnt(),
                stat.getErrorCnt(),
                stat.getFailCnt(),
                stat.getIngCnt(),
                stat.getWaitCnt()),
            null);
      }

      byte[] content = excelService.toByteArray(workbook);

      String fileName = "통계_" + startDate + "_" + endDate + ".xlsx";
      String encodedFileName =
          URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

      return ResponseEntity.ok()
          .header(
              HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
          .contentType(
              MediaType.parseMediaType(
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .body(content);
    } catch (Exception e) {
      log.error("Excel 다운로드 실패", e);
      throw new RuntimeException("Excel 파일 생성에 실패했습니다.");
    }
  }

  /**
   * 과금 통계 Excel 다운로드 (다중 시트) GET
   * /api/excel/billing/download?startDate=2025-01-01&endDate=2025-01-31
   */
  @GetMapping("/billing/download")
  public ResponseEntity<byte[]> downloadBillingExcel(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) String reason,
      HttpServletRequest httpRequest) {

    String currentUserId = extractUserId(userDetails);
    User user = userMapper.findByUserId(currentUserId).orElseThrow();
    Integer userLevel = user.getUserLevel();

    log.info("[엑셀 다운로드 시작] 과금 통계 - 사용자: {}, 기간: {} ~ {}", currentUserId, startDate, endDate);

    // 활동 로그 기록
    actionLogService.logDownloadAction(
        currentUserId, user.getPerson(), "과금통계 엑셀다운로드", "R", reason, httpRequest);

    // 기본 날짜 설정
    LocalDate now = LocalDate.now();
    if (startDate == null || startDate.isEmpty()) {
      startDate = now.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE);
    }
    if (endDate == null || endDate.isEmpty()) {
      endDate = now.format(DateTimeFormatter.ISO_DATE);
    }

    // 조회 대상 사용자 결정
    List<String> targetUserIds = new ArrayList<>();
    if (userId != null && !userId.isEmpty()) {
      targetUserIds.add(userId);
    } else {
      // 권한에 따른 조회 대상 결정
      String queryUserIds = adminService.determineQueryUserIds(currentUserId, userLevel);
      if ("ALL".equals(queryUserIds)) {
        // 전체 조회 (관리자)
        targetUserIds = null; // 전체
      } else if (queryUserIds.contains(",")) {
        targetUserIds.addAll(Arrays.asList(queryUserIds.split(",")));
      } else {
        targetUserIds.add(queryUserIds);
      }
    }

    // Excel 생성
    BillingStatsSearchRequest request =
        BillingStatsSearchRequest.builder()
            .userId(userId)
            .startDate(startDate)
            .endDate(endDate)
            .build();

    byte[] content =
        billingExcelService.generateBillingExcel(request, currentUserId, targetUserIds);

    String fileName = "과금통계_" + startDate + "_" + endDate + ".xlsx";
    String encodedFileName =
        URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(content);
  }

  /** Excel 파일 업로드 (데이터 파싱) POST /api/excel/upload */
  @PostMapping("/upload")
  public ApiResponse<List<Map<String, String>>> uploadExcel(
      @RequestParam("file") MultipartFile file,
      @RequestParam(defaultValue = "2") int startRow,
      @RequestParam(required = false) String columns) {

    String[] outputColumns =
        columns != null && !columns.isEmpty()
            ? columns.split(",")
            : new String[] {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J"};

    List<Map<String, String>> data = excelService.readExcel(file, startRow, outputColumns);
    return ApiResponse.success(data);
  }

  /** Excel 템플릿 다운로드 GET /api/excel/template/{type} */
  @GetMapping("/template/{type}")
  public ResponseEntity<byte[]> downloadTemplate(@PathVariable String type) {

    try (SXSSFWorkbook workbook = excelService.createWorkbook()) {
      Sheet sheet;
      List<String> headers;
      String fileName;

      switch (type.toLowerCase()) {
        case "phone":
          sheet = excelService.createSheet(workbook, "수신자목록");
          headers = Arrays.asList("휴대폰번호", "대치문자1", "대치문자2", "대치문자3");
          fileName = "수신자목록_템플릿.xlsx";
          break;
        case "survey":
          sheet = excelService.createSheet(workbook, "설문대상자");
          headers = Arrays.asList("이름", "휴대폰번호", "인증코드");
          fileName = "설문대상자_템플릿.xlsx";
          break;
        default:
          sheet = excelService.createSheet(workbook, "데이터");
          headers = Arrays.asList("컬럼1", "컬럼2", "컬럼3");
          fileName = "템플릿.xlsx";
      }

      CellStyle headerStyle = excelService.createHeaderStyle(workbook, 11, true, 192, 192, 192);
      excelService.createHeaderRow(sheet, 0, headers, headerStyle);

      byte[] content = excelService.toByteArray(workbook);

      String encodedFileName =
          URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

      return ResponseEntity.ok()
          .header(
              HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
          .contentType(
              MediaType.parseMediaType(
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .body(content);
    } catch (Exception e) {
      log.error("템플릿 다운로드 실패", e);
      throw new RuntimeException("템플릿 파일 생성에 실패했습니다.");
    }
  }

  /** 설문조사 참여현황 Excel 다운로드 GET /api/excel/survey/download */
  @GetMapping("/survey/download")
  public ResponseEntity<byte[]> downloadSurveyExcel(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(required = false) Integer eventSeq,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String searchType,
      @RequestParam(required = false) String submissionStatus,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String reason,
      HttpServletRequest request) {

    String userId = extractUserId(userDetails);
    User user = userMapper.findByUserId(userId).orElseThrow();

    log.info("[엑셀 다운로드 시작] 설문조사 참여현황 - 사용자: {}", userId);

    // 활동 로그 기록
    actionLogService.logDownloadAction(
        userId, user.getPerson(), "설문조사 참여현황 엑셀다운로드", "R", reason, request);

    // 검색 조건 설정
    Map<String, Object> params = new HashMap<>();
    params.put("eventSeq", eventSeq);
    params.put("eventType", "S"); // Survey
    params.put("keyword", keyword);
    params.put("searchType", searchType);
    params.put("submissionStatus", submissionStatus);
    params.put("startDate", startDate);
    params.put("endDate", endDate);
    params.put("userLevel", user.getUserLevel());
    params.put("regId", userId);

    List<Map<String, Object>> dataList = surveyUserMapper.selectForExcelDownload(params);

    try (SXSSFWorkbook workbook = excelService.createWorkbook()) {
      Sheet sheet = excelService.createSheet(workbook, "설문조사 참여현황");

      // 헤더 스타일
      CellStyle headerStyle = excelService.createHeaderStyle(workbook, 11, true, 192, 192, 192);

      // 헤더 생성
      List<String> headers = Arrays.asList("번호", "고객사명", "이벤트명", "전화번호", "난수", "최종접속일", "최종완료일");
      excelService.createHeaderRow(sheet, 0, headers, headerStyle);

      // 데이터 행 생성
      int rowNum = 1;
      for (Map<String, Object> data : dataList) {
        // 전화번호 복호화
        String phone = decryptPhone(data.get("resendUserPhone"));

        excelService.createDataRow(
            sheet,
            rowNum++,
            Arrays.asList(
                data.get("seq"),
                data.get("corpName") != null ? data.get("corpName") : "",
                removeEmphasis(data.get("eventName")),
                phone != null ? phone : "",
                data.get("userKey") != null ? data.get("userKey") : "",
                formatDateTime(data.get("surveyStartTime")),
                formatDateTime(data.get("submissionDate"))),
            null);
      }

      byte[] content = excelService.toByteArray(workbook);

      String fileName = "설문조사_참여현황_" + LocalDate.now().format(DATE_FORMATTER) + ".xlsx";
      String encodedFileName =
          URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

      return ResponseEntity.ok()
          .header(
              HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
          .contentType(
              MediaType.parseMediaType(
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .body(content);
    } catch (Exception e) {
      log.error("설문조사 엑셀 다운로드 실패", e);
      throw new RuntimeException("Excel 파일 생성에 실패했습니다.");
    }
  }

  /** 개인정보취합 참여현황 Excel 다운로드 GET /api/excel/privacy/download */
  @GetMapping("/privacy/download")
  public ResponseEntity<byte[]> downloadPrivacyExcel(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(required = false) Integer eventSeq,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String searchType,
      @RequestParam(required = false) String submissionStatus,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(required = false) String reason,
      HttpServletRequest request) {

    String userId = extractUserId(userDetails);
    User user = userMapper.findByUserId(userId).orElseThrow();

    log.info("[엑셀 다운로드 시작] 개인정보취합 참여현황 - 사용자: {}", userId);

    // 활동 로그 기록
    actionLogService.logDownloadAction(
        userId, user.getPerson(), "개인정보취합 참여현황 엑셀다운로드", "R", reason, request);

    // 검색 조건 설정
    Map<String, Object> params = new HashMap<>();
    params.put("eventSeq", eventSeq);
    params.put("eventType", "P"); // Privacy
    params.put("keyword", keyword);
    params.put("searchType", searchType);
    params.put("submissionStatus", submissionStatus);
    params.put("startDate", startDate);
    params.put("endDate", endDate);
    params.put("userLevel", user.getUserLevel());
    params.put("regId", userId);

    List<Map<String, Object>> dataList = surveyUserMapper.selectForExcelDownload(params);

    // 모바일이앤엠애드 회사 여부 확인 (입금일자, 입금금액, 출고일자 컬럼 추가용)
    boolean includePaymentInfo =
        user.getCorpName() != null && user.getCorpName().contains("모바일이앤엠애드");

    try (SXSSFWorkbook workbook = excelService.createWorkbook()) {
      Sheet sheet = excelService.createSheet(workbook, "개인정보취합 참여현황");

      // 헤더 스타일
      CellStyle headerStyle = excelService.createHeaderStyle(workbook, 11, true, 192, 192, 192);

      // 헤더 생성
      List<String> headers =
          new ArrayList<>(Arrays.asList("번호", "고객사명", "이벤트명", "당첨자명", "전화번호", "주민번호", "주소"));
      if (includePaymentInfo) {
        headers.addAll(Arrays.asList("입금일자", "입금금액", "출고일자"));
      }
      headers.addAll(Arrays.asList("등록일", "제출일"));

      excelService.createHeaderRow(sheet, 0, headers, headerStyle);

      // 데이터 행 생성
      int rowNum = 1;
      for (Map<String, Object> data : dataList) {
        // 복호화 처리
        String userName = decryptData(data.get("userName"));
        String phone = decryptPhone(data.get("userPhone"));
        String juminNum = decryptJumin(data.get("juminNum"));
        String address = normalizeAddress(data.get("address"), data.get("address2"));

        List<Object> rowData =
            new ArrayList<>(
                Arrays.asList(
                    data.get("seq"),
                    data.get("corpName") != null ? data.get("corpName") : "",
                    removeEmphasis(data.get("eventName")),
                    userName != null ? userName : "",
                    phone != null ? phone : "",
                    juminNum != null ? juminNum : "",
                    address));

        if (includePaymentInfo) {
          rowData.add(formatDate(data.get("depositDate")));
          rowData.add(data.get("depositAmount") != null ? data.get("depositAmount") : "");
          rowData.add(formatDate(data.get("shipmentDate")));
        }

        rowData.add(formatDateTime(data.get("regDate")));
        rowData.add(formatDateTime(data.get("submissionDate")));

        excelService.createDataRow(sheet, rowNum++, rowData, null);
      }

      byte[] content = excelService.toByteArray(workbook);

      String fileName = "개인정보취합_참여현황_" + LocalDate.now().format(DATE_FORMATTER) + ".xlsx";
      String encodedFileName =
          URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

      return ResponseEntity.ok()
          .header(
              HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
          .contentType(
              MediaType.parseMediaType(
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .body(content);
    } catch (Exception e) {
      log.error("개인정보취합 엑셀 다운로드 실패", e);
      throw new RuntimeException("Excel 파일 생성에 실패했습니다.");
    }
  }

  /**
   * 설문조사/개인정보취합 참여현황 Excel 다운로드 (3가지 모드 지원) POST /api/excel/participant/download
   *
   * <p>downloadType: - ALL: 전체 데이터 다운로드 - SEARCH: 검색 조건에 맞는 데이터 다운로드 - SELECTED: 선택된 데이터만 다운로드
   */
  @PostMapping("/participant/download")
  public ResponseEntity<byte[]> downloadParticipantExcel(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestBody SurveyExcelDownloadRequest downloadRequest,
      HttpServletRequest request) {

    String userId = extractUserId(userDetails);
    User user = userMapper.findByUserId(userId).orElseThrow();

    SurveyExcelDownloadRequest.DownloadType downloadType = downloadRequest.getDownloadType();
    String eventType = downloadRequest.getEventType();
    boolean isSurvey = "S".equals(eventType);
    String menuName = isSurvey ? "설문조사" : "개인정보취합";

    log.info("[엑셀 다운로드 시작] {} 참여현황 - 사용자: {}, 다운로드타입: {}", menuName, userId, downloadType);

    // 활동 로그 기록
    String logMenuName =
        String.format("%s 참여현황 엑셀다운로드 (%s)", menuName, getDownloadTypeText(downloadType));
    actionLogService.logDownloadAction(
        userId, user.getPerson(), logMenuName, "R", downloadRequest.getReason(), request);

    // 데이터 조회
    List<Map<String, Object>> dataList =
        getDataListByDownloadType(downloadType, downloadRequest, user, userId);

    if (dataList.isEmpty()) {
      throw new RuntimeException("다운로드할 데이터가 없습니다.");
    }

    // Excel 생성
    try (SXSSFWorkbook workbook = excelService.createWorkbook()) {
      String sheetName = menuName + " 참여현황";
      Sheet sheet = excelService.createSheet(workbook, sheetName);
      CellStyle headerStyle = excelService.createHeaderStyle(workbook, 11, true, 192, 192, 192);

      if (isSurvey) {
        // 설문조사 헤더
        List<String> headers = Arrays.asList("번호", "고객사명", "이벤트명", "전화번호", "난수", "최종접속일", "최종완료일");
        excelService.createHeaderRow(sheet, 0, headers, headerStyle);

        // 데이터 행 생성
        int rowNum = 1;
        for (Map<String, Object> data : dataList) {
          String phone = decryptPhone(data.get("resendUserPhone"));
          excelService.createDataRow(
              sheet,
              rowNum++,
              Arrays.asList(
                  data.get("seq"),
                  data.get("corpName") != null ? data.get("corpName") : "",
                  removeEmphasis(data.get("eventName")),
                  phone != null ? phone : "",
                  data.get("userKey") != null ? data.get("userKey") : "",
                  formatDateTime(data.get("surveyStartTime")),
                  formatDateTime(data.get("submissionDate"))),
              null);
        }
      } else {
        // 개인정보취합 헤더
        boolean includePaymentInfo =
            user.getCorpName() != null && user.getCorpName().contains("모바일이앤엠애드");

        List<String> headers =
            new ArrayList<>(Arrays.asList("번호", "고객사명", "이벤트명", "당첨자명", "전화번호", "주민번호", "주소"));
        if (includePaymentInfo) {
          headers.addAll(Arrays.asList("입금일자", "입금금액", "출고일자"));
        }
        headers.addAll(Arrays.asList("등록일", "제출일"));
        excelService.createHeaderRow(sheet, 0, headers, headerStyle);

        // 데이터 행 생성
        int rowNum = 1;
        for (Map<String, Object> data : dataList) {
          String userName = decryptData(data.get("userName"));
          String phone = decryptPhone(data.get("userPhone"));
          String juminNum = decryptJumin(data.get("juminNum"));
          String address = normalizeAddress(data.get("address"), data.get("address2"));

          List<Object> rowData =
              new ArrayList<>(
                  Arrays.asList(
                      data.get("seq"),
                      data.get("corpName") != null ? data.get("corpName") : "",
                      removeEmphasis(data.get("eventName")),
                      userName != null ? userName : "",
                      phone != null ? phone : "",
                      juminNum != null ? juminNum : "",
                      address));

          if (includePaymentInfo) {
            rowData.add(formatDate(data.get("depositDate")));
            rowData.add(data.get("depositAmount") != null ? data.get("depositAmount") : "");
            rowData.add(formatDate(data.get("shipmentDate")));
          }

          rowData.add(formatDateTime(data.get("regDate")));
          rowData.add(formatDateTime(data.get("submissionDate")));

          excelService.createDataRow(sheet, rowNum++, rowData, null);
        }
      }

      byte[] content = excelService.toByteArray(workbook);

      String fileName =
          String.format(
              "%s_참여현황_%s_%s.xlsx",
              menuName, getDownloadTypeText(downloadType), LocalDate.now().format(DATE_FORMATTER));
      String encodedFileName =
          URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

      return ResponseEntity.ok()
          .header(
              HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
          .contentType(
              MediaType.parseMediaType(
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .body(content);
    } catch (Exception e) {
      log.error("{} 엑셀 다운로드 실패", menuName, e);
      throw new RuntimeException("Excel 파일 생성에 실패했습니다.");
    }
  }

  /** 다운로드 타입에 따른 데이터 조회 */
  private List<Map<String, Object>> getDataListByDownloadType(
      SurveyExcelDownloadRequest.DownloadType downloadType,
      SurveyExcelDownloadRequest downloadRequest,
      User user,
      String userId) {

    switch (downloadType) {
      case ALL:
        // 전체 다운로드 - 검색 조건 무시
        Map<String, Object> allParams = new HashMap<>();
        allParams.put("eventType", downloadRequest.getEventType());
        allParams.put("userLevel", user.getUserLevel());
        allParams.put("regId", userId);
        return surveyUserMapper.selectForExcelDownload(allParams);

      case SELECTED:
        // 선택 다운로드 - 선택된 시퀀스만
        List<Integer> selectedSeqs = downloadRequest.getSelectedSeqs();
        if (selectedSeqs == null || selectedSeqs.isEmpty()) {
          throw new RuntimeException("선택된 항목이 없습니다.");
        }
        return surveyUserMapper.selectBySeqListForExcel(selectedSeqs);

      case SEARCH:
      default:
        // 검색 다운로드 - 검색 조건 적용
        Map<String, Object> searchParams = new HashMap<>();
        searchParams.put("eventSeq", downloadRequest.getEventSeq());
        searchParams.put("eventType", downloadRequest.getEventType());
        searchParams.put("keyword", downloadRequest.getKeyword());
        searchParams.put("searchType", downloadRequest.getSearchType());
        searchParams.put("submissionStatus", downloadRequest.getSubmissionStatus());
        searchParams.put("startDate", downloadRequest.getStartDate());
        searchParams.put("endDate", downloadRequest.getEndDate());
        searchParams.put("userLevel", user.getUserLevel());
        searchParams.put("regId", userId);
        return surveyUserMapper.selectForExcelDownload(searchParams);
    }
  }

  /** 다운로드 타입 텍스트 */
  private String getDownloadTypeText(SurveyExcelDownloadRequest.DownloadType downloadType) {
    switch (downloadType) {
      case ALL:
        return "전체";
      case SELECTED:
        return "선택";
      case SEARCH:
      default:
        return "검색";
    }
  }

  // ==================== Private Helper Methods ====================

  /** 암호화된 데이터 복호화 */
  private String decryptData(Object data) {
    if (data == null) return null;
    try {
      return CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(data.toString()));
    } catch (Exception e) {
      log.warn("데이터 복호화 실패", e);
      return data.toString();
    }
  }

  /** 전화번호 복호화 */
  private String decryptPhone(Object phone) {
    if (phone == null) return null;
    try {
      return CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(phone.toString()));
    } catch (Exception e) {
      log.warn("전화번호 복호화 실패", e);
      return null;
    }
  }

  /** 주민번호 복호화 */
  private String decryptJumin(Object juminNum) {
    if (juminNum == null) return null;
    try {
      String decrypted = CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(juminNum.toString()));
      if (decrypted != null && decrypted.length() >= 13) {
        // 뒷자리 마스킹
        return decrypted.substring(0, 7) + "******";
      }
      return decrypted;
    } catch (Exception e) {
      log.warn("주민번호 복호화 실패", e);
      return null;
    }
  }

  /** 주소 정규화 */
  private String normalizeAddress(Object address, Object address2) {
    StringBuilder sb = new StringBuilder();
    if (address != null) {
      try {
        String decrypted = CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(address.toString()));
        sb.append(decrypted != null ? decrypted : address.toString());
      } catch (Exception e) {
        sb.append(address.toString());
      }
    }
    if (address2 != null && !address2.toString().isEmpty()) {
      sb.append(" ").append(address2);
    }
    return sb.toString();
  }

  /** 이벤트명에서 강조 표시 제거 */
  private String removeEmphasis(Object eventName) {
    if (eventName == null) return "";
    return eventName.toString().replaceAll("[{}]", "");
  }

  /** 날짜 포맷팅 */
  private String formatDate(Object date) {
    if (date == null) return "";
    if (date instanceof LocalDate) {
      return ((LocalDate) date).format(DATE_FORMATTER);
    }
    if (date instanceof java.sql.Date) {
      return ((java.sql.Date) date).toLocalDate().format(DATE_FORMATTER);
    }
    return date.toString();
  }

  /** 날짜시간 포맷팅 */
  private String formatDateTime(Object dateTime) {
    if (dateTime == null) return "";
    if (dateTime instanceof LocalDateTime) {
      return ((LocalDateTime) dateTime).format(DATETIME_FORMATTER);
    }
    if (dateTime instanceof java.sql.Timestamp) {
      return ((java.sql.Timestamp) dateTime).toLocalDateTime().format(DATETIME_FORMATTER);
    }
    return dateTime.toString();
  }
}
