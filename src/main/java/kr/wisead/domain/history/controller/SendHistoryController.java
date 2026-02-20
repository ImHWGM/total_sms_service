package kr.wisead.domain.history.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.admin.service.ActionLogService;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.ars.dto.BlockedSenderResponse;
import kr.wisead.domain.history.dto.SendHistoryResponse;
import kr.wisead.domain.history.dto.SendHistorySearchRequest;
import kr.wisead.domain.history.service.SendHistoryService;
import kr.wisead.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 발송 이력 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class SendHistoryController {

    private final SendHistoryService sendHistoryService;
    private final ActionLogService actionLogService;
    private final AdminService adminService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserIdResolver userIdResolver;

    /**
     * 발송 이력 목록 조회
     * GET /api/history/send
     */
    @GetMapping("/send")
    public ApiResponse<PageResponse<SendHistoryResponse>> getSendHistory(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sendFailure,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("Authorization") String token) {

        String accessToken = token.replace("Bearer ", "");
        String userId = userIdResolver.resolveUserId(jwtTokenProvider.getUserId(accessToken));
        Integer userLevel = adminService.getUserLevel(userId);

        // 권한에 따른 조회 대상 설정
        String queryUserId = adminService.determineQueryUserIds(userId, userLevel);

        SendHistorySearchRequest request = SendHistorySearchRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .type(type)
                .keyword(keyword)
                .sendFailure(sendFailure)
                .userId(queryUserId)
                .page(page)
                .size(size)
                .build();

        PageResponse<SendHistoryResponse> response = sendHistoryService.getHistoryList(request);
        return ApiResponse.success(response);
    }

    /**
     * 발송 이력 엑셀 다운로드
     * POST /api/history/send/download
     */
    @PostMapping("/send/download")
    public void downloadSendHistory(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sendFailure,
            @RequestParam String reason,
            @RequestHeader("Authorization") String token,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        String accessToken = token.replace("Bearer ", "");
        String userId = userIdResolver.resolveUserId(jwtTokenProvider.getUserId(accessToken));
        String userName = decryptName(jwtTokenProvider.getUserName(accessToken));
        Integer userLevel = adminService.getUserLevel(userId);

        // 다운로드 로그 기록
        actionLogService.logDownloadAction(userId, userName, "발송 이력 다운로드", "D", reason, request);

        // 권한에 따른 조회 대상 설정
        String queryUserId = adminService.determineQueryUserIds(userId, userLevel);

        SendHistorySearchRequest searchRequest = SendHistorySearchRequest.builder()
                .startDate(startDate)
                .endDate(endDate)
                .type(type)
                .keyword(keyword)
                .sendFailure(sendFailure)
                .userId(queryUserId)
                .build();

        List<SendHistoryResponse> historyList = sendHistoryService.getHistoryListForDownload(searchRequest);

        // 엑셀 생성
        Workbook wb = new SXSSFWorkbook();
        Sheet sheet = wb.createSheet("발신 메시지 내역");

        // 스타일 설정
        CellStyle headerStyle = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        headerStyle.setFont(font);
        headerStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // 헤더
        Row headerRow = sheet.createRow(0);
        String[] headers = {"문자 타입", "수신번호", "발신번호", "상태", "발신결과", "문자 제목",
                "문자 내용", "파일 개수", "요청시간", "발송시간", "수신시간", "통신사", "발송형식", "발신 아이디"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // 데이터
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        int rowNum = 1;
        for (SendHistoryResponse history : historyList) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(history.getMsgType() != null ? history.getMsgType() : "");
            row.createCell(1).setCellValue(history.getReceiver() != null ? history.getReceiver() : "");
            row.createCell(2).setCellValue(history.getCallback() != null ? history.getCallback() : "");
            row.createCell(3).setCellValue(history.getStatName() != null ? history.getStatName() : "");
            row.createCell(4).setCellValue(history.getResult() != null ? history.getResult() : "");
            row.createCell(5).setCellValue(history.getSubject() != null ? history.getSubject() : "");
            row.createCell(6).setCellValue(history.getText() != null ? history.getText() : "");
            row.createCell(7).setCellValue(history.getFileCnt() != null ? history.getFileCnt() : 0);
            row.createCell(8).setCellValue(history.getRequestTime() != null ? history.getRequestTime().format(dtf) : "");
            row.createCell(9).setCellValue(history.getSendTime() != null ? history.getSendTime().format(dtf) : "");
            row.createCell(10).setCellValue(history.getReportTime() != null ? history.getReportTime().format(dtf) : "");
            row.createCell(11).setCellValue(history.getTelecom() != null ? history.getTelecom() : "");
            row.createCell(12).setCellValue(history.getSendType() != null ? history.getSendType() : "");
            row.createCell(13).setCellValue(history.getSenderId() != null ? history.getSenderId() : "");
        }

        // 파일 다운로드
        String fileName = "발신_메시지_내역_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");

        wb.write(response.getOutputStream());
        wb.close();

        log.info("발송 이력 엑셀 다운로드 완료: userId={}, 건수={}", userId, historyList.size());
    }

    /**
     * 수신거부 목록 조회
     * GET /api/history/optout
     */
    @GetMapping("/optout")
    public ApiResponse<PageResponse<BlockedSenderResponse>> getOptOutList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("Authorization") String token) {

        String userId = userIdResolver.resolveUserId(
            jwtTokenProvider.getUserId(token.replace("Bearer ", "")));
        // TODO: storeCode 조회 로직 필요 (사용자별 스토어 코드)
        String storeCode = userId; // 임시로 userId 사용

        PageResponse<BlockedSenderResponse> response = sendHistoryService.getBlockedSenders(storeCode, page, size);
        return ApiResponse.success(response);
    }

    /**
     * 수신거부 삭제
     * DELETE /api/history/optout
     */
    @DeleteMapping("/optout")
    public ApiResponse<Integer> deleteOptOut(
            @RequestBody List<Map<String, String>> keyList,
            @RequestHeader("Authorization") String token,
            HttpServletRequest request) {

        String accessToken = token.replace("Bearer ", "");
        String userId = userIdResolver.resolveUserId(jwtTokenProvider.getUserId(accessToken));
        String userName = decryptName(jwtTokenProvider.getUserName(accessToken));

        // 삭제 로그 기록
        actionLogService.logDownloadAction(userId, userName, "수신거부 삭제", "D",
                "삭제 건수: " + keyList.size(), request);

        int deleteCount = sendHistoryService.deleteBlockedSenders(keyList);
        return ApiResponse.success(deleteCount, deleteCount + "건이 삭제되었습니다.");
    }

    /**
     * 수신거부 엑셀 다운로드
     * GET /api/history/optout/download
     */
    @GetMapping("/optout/download")
    public void downloadOptOut(
            @RequestHeader("Authorization") String token,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        String accessToken = token.replace("Bearer ", "");
        String userId = userIdResolver.resolveUserId(jwtTokenProvider.getUserId(accessToken));
        String userName = decryptName(jwtTokenProvider.getUserName(accessToken));
        String storeCode = userId; // TODO: 스토어 코드 조회

        // 다운로드 로그 기록
        actionLogService.logDownloadAction(userId, userName, "수신거부 내역 다운로드", "D", "업무용", request);

        // 전체 조회 (페이징 없이)
        PageResponse<BlockedSenderResponse> pageResponse = sendHistoryService.getBlockedSenders(storeCode, 1, Integer.MAX_VALUE);
        List<BlockedSenderResponse> blockedList = pageResponse.getContent();

        // 엑셀 생성
        Workbook wb = new SXSSFWorkbook();
        Sheet sheet = wb.createSheet("수신거부 내역");

        // 스타일 설정
        CellStyle headerStyle = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        headerStyle.setFont(font);
        headerStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // 헤더
        Row headerRow = sheet.createRow(0);
        String[] headers = {"No", "080번호", "수신거부 번호", "등록일시"};

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // 데이터
        int rowNum = 1;
        for (BlockedSenderResponse blocked : blockedList) {
            Row row = sheet.createRow(rowNum);
            row.createCell(0).setCellValue(rowNum);
            row.createCell(1).setCellValue(blocked.getMenuName() != null ? blocked.getMenuName() : "");
            row.createCell(2).setCellValue(blocked.getAni() != null ? blocked.getAni() : "");
            row.createCell(3).setCellValue(blocked.getRegDate() != null ? blocked.getRegDate() : "");
            rowNum++;
        }

        // 파일 다운로드
        String fileName = "수신거부_내역_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");

        wb.write(response.getOutputStream());
        wb.close();

        log.info("수신거부 엑셀 다운로드 완료: userId={}, 건수={}", userId, blockedList.size());
    }

    private String decryptName(String encryptedName) {
        if (encryptedName == null) {
            return null;
        }
        try {
            return CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(encryptedName));
        } catch (Exception e) {
            return encryptedName;
        }
    }
}
