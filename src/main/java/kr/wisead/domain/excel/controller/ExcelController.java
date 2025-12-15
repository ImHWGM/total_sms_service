package kr.wisead.domain.excel.controller;

import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.excel.dto.ExcelExportRequest;
import kr.wisead.domain.excel.service.ExcelService;
import kr.wisead.domain.statistics.dto.DailyStatsResponse;
import kr.wisead.domain.statistics.dto.StatsSearchRequest;
import kr.wisead.domain.statistics.service.StatisticsService;
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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Excel 다운로드/업로드 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/excel")
@RequiredArgsConstructor
public class ExcelController {

    private final ExcelService excelService;
    private final StatisticsService statisticsService;

    /**
     * 통계 Excel 다운로드
     * GET /api/excel/statistics/download?startDate=2025-01-01&endDate=2025-01-31
     */
    @GetMapping("/statistics/download")
    public ResponseEntity<byte[]> downloadStatisticsExcel(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String serviceType) {

        Integer userSeq = Integer.parseInt(userDetails.getUsername());

        // 기본 날짜 설정
        LocalDate now = LocalDate.now();
        if (startDate == null || startDate.isEmpty()) {
            startDate = now.withDayOfMonth(1).format(DateTimeFormatter.ISO_DATE);
        }
        if (endDate == null || endDate.isEmpty()) {
            endDate = now.format(DateTimeFormatter.ISO_DATE);
        }

        // 통계 데이터 조회
        StatsSearchRequest request = StatsSearchRequest.builder()
                .userId(userSeq)
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
            List<String> headers = Arrays.asList(
                    "일자", "접수건수", "성공건수", "에러건수", "실패건수", "진행중", "대기"
            );
            excelService.createHeaderRow(sheet, 0, headers, headerStyle);

            // 데이터 행 생성
            int rowNum = 1;
            for (DailyStatsResponse stat : stats) {
                excelService.createDataRow(sheet, rowNum++, Arrays.asList(
                        stat.getDtStats(),
                        stat.getInCnt(),
                        stat.getSuccCnt(),
                        stat.getErrorCnt(),
                        stat.getFailCnt(),
                        stat.getIngCnt(),
                        stat.getWaitCnt()
                ), null);
            }

            byte[] content = excelService.toByteArray(workbook);

            String fileName = "통계_" + startDate + "_" + endDate + ".xlsx";
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(content);
        } catch (Exception e) {
            log.error("Excel 다운로드 실패", e);
            throw new RuntimeException("Excel 파일 생성에 실패했습니다.");
        }
    }

    /**
     * Excel 파일 업로드 (데이터 파싱)
     * POST /api/excel/upload
     */
    @PostMapping("/upload")
    public ApiResponse<List<Map<String, String>>> uploadExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "2") int startRow,
            @RequestParam(required = false) String columns) {

        String[] outputColumns = columns != null && !columns.isEmpty()
                ? columns.split(",")
                : new String[]{"A", "B", "C", "D", "E", "F", "G", "H", "I", "J"};

        List<Map<String, String>> data = excelService.readExcel(file, startRow, outputColumns);
        return ApiResponse.success(data);
    }

    /**
     * Excel 템플릿 다운로드
     * GET /api/excel/template/{type}
     */
    @GetMapping("/template/{type}")
    public ResponseEntity<byte[]> downloadTemplate(@PathVariable String type) {

        try (SXSSFWorkbook workbook = excelService.createWorkbook()) {
            Sheet sheet;
            List<String> headers;
            String fileName;

            switch (type.toLowerCase()) {
                case "phone":
                    sheet = excelService.createSheet(workbook, "수신자목록");
                    headers = Arrays.asList("이름", "휴대폰번호", "변수1", "변수2", "변수3");
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

            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFileName + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(content);
        } catch (Exception e) {
            log.error("템플릿 다운로드 실패", e);
            throw new RuntimeException("템플릿 파일 생성에 실패했습니다.");
        }
    }
}
