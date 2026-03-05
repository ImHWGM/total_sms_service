package kr.wisead.domain.schedule.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.admin.service.ActionLogService;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.schedule.dto.RescheduleRequest;
import kr.wisead.domain.schedule.dto.ScheduledMessageResponse;
import kr.wisead.domain.schedule.dto.ScheduledMessageSearchRequest;
import kr.wisead.domain.schedule.service.ScheduledMessageService;
import kr.wisead.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 예약 메시지 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/scheduled-messages")
@RequiredArgsConstructor
public class ScheduledMessageController {

    private final ScheduledMessageService scheduledMessageService;
    private final AdminService adminService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserIdResolver userIdResolver;
    private final ActionLogService actionLogService;

    /**
     * 예약 메시지 목록 조회
     * GET /api/scheduled-messages
     */
    @GetMapping
    public ApiResponse<PageResponse<ScheduledMessageResponse>> getScheduledMessages(
            @RequestParam(required = false) String msgType,
            @RequestParam(required = false) String searchText,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader("Authorization") String token) {

        String accessToken = token.replace("Bearer ", "");
        String userId = userIdResolver.resolveUserId(jwtTokenProvider.getUserId(accessToken));
        Integer userLevel = adminService.getUserLevel(userId);

        // 권한에 따른 조회 대상 설정
        String queryUserId = adminService.determineQueryUserIds(userId, userLevel);

        ScheduledMessageSearchRequest request = ScheduledMessageSearchRequest.builder()
                .msgType(msgType)
                .searchText(searchText)
                .userId(queryUserId)
                .page(page)
                .size(size)
                .build();

        PageResponse<ScheduledMessageResponse> response = scheduledMessageService.getScheduledMessages(request);
        return ApiResponse.success(response);
    }

    /**
     * 예약 메시지 엑셀 다운로드
     */
    @PostMapping("/download")
    public void downloadScheduledMessages(
        @RequestParam(required = false) String msgType,
        @RequestParam(required = false) String searchText,
        @RequestParam String reason,
        @RequestHeader("Authorization") String token,
        HttpServletRequest request,
        HttpServletResponse response) throws Exception {
        String accessToken = token.replace("Bearer ", "");
        String userId = userIdResolver.resolveUserId(jwtTokenProvider.getUserId(accessToken));
        String userName = decryptName(jwtTokenProvider.getUserName(accessToken));
        Integer userLevel = adminService.getUserLevel(userId);
        // 1. 다운로드 로그 기록
        actionLogService.logDownloadAction(userId, userName, "예약 리스트 다운로드", "D", reason, request);
        // 2. 데이터 조회
        String queryUserId = adminService.determineQueryUserIds(userId, userLevel);
        ScheduledMessageSearchRequest searchRequest = ScheduledMessageSearchRequest.builder()
            .msgType(msgType)
            .searchText(searchText)
            .userId(queryUserId)
            .build();
        List<ScheduledMessageResponse> list = scheduledMessageService.getScheduledMessagesForDownload(
            searchRequest);
        // 3. 엑셀 생성 (POI 사용)
        Workbook wb = new SXSSFWorkbook();
        Sheet sheet = wb.createSheet("예약 메시지 내역");
        // 헤더 생성 및 스타일 설정
        String[] headers = {"문자 타입", "제목/내용", "발신번호", "예약시간", "요청건수", "등록자"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
        // 데이터 채우기
        int rowNum = 1;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (ScheduledMessageResponse item : list) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(item.getMsgType());
            row.createCell(1)
                .setCellValue(item.getSubject() != null ? item.getSubject() : item.getText());
            row.createCell(2).setCellValue(item.getCallBack());
            row.createCell(3).setCellValue(item.getRequestTime().format(dtf));
            row.createCell(4).setCellValue(item.getMessageCount());
            row.createCell(5).setCellValue(item.getUserId());
        }
        // 4. 파일 다운로드 응답 설정
        String fileName =
            "예약_메시지_내역_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ".xlsx";
        response.setContentType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
            "attachment; filename=\"" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + "\"");
        wb.write(response.getOutputStream());
        wb.close();
    }

    /**
     * 예약 메시지 상세 조회
     * GET /api/scheduled-messages/{mSeq}
     */
    @GetMapping("/{mSeq}")
    public ApiResponse<ScheduledMessageResponse> getScheduledMessageById(
            @PathVariable int mSeq,
            @RequestHeader("Authorization") String token) {

        String accessToken = token.replace("Bearer ", "");
        String userId = userIdResolver.resolveUserId(jwtTokenProvider.getUserId(accessToken));
        Integer userLevel = adminService.getUserLevel(userId);
        String queryUserId = adminService.determineQueryUserIds(userId, userLevel);

        ScheduledMessageResponse response = scheduledMessageService.getMessageById(mSeq, queryUserId);
        if (response == null) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND, "예약 메시지를 찾을 수 없습니다.");
        }

        return ApiResponse.success(response);
    }

    /**
     * 예약 시간 변경
     * PUT /api/scheduled-messages/{mSeq}/reschedule
     */
    @PutMapping("/{mSeq}/reschedule")
    public ApiResponse<Void> rescheduleMessage(
            @PathVariable int mSeq,
            @Valid @RequestBody RescheduleRequest request,
            @RequestHeader("Authorization") String token) {

        String accessToken = token.replace("Bearer ", "");
        String userId = userIdResolver.resolveUserId(jwtTokenProvider.getUserId(accessToken));
        Integer userLevel = adminService.getUserLevel(userId);
        String queryUserId = adminService.determineQueryUserIds(userId, userLevel);

        // 10분 이내 예약 불가
        LocalDateTime minTime = LocalDateTime.now().plusMinutes(10);
        if (request.getNewScheduleTime().isBefore(minTime)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "예약일시는 현재 시각으로부터 최소 10분 이후여야 합니다.");
        }

        // 메시지 조회
        ScheduledMessageResponse message = scheduledMessageService.getMessageById(mSeq, queryUserId);
        if (message == null) {
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND, "예약 메시지를 찾을 수 없습니다.");
        }

        // 예약 시간 변경
        scheduledMessageService.rescheduleMessageGroup(
                message.getUserId(),  // 실제 메시지 소유자
                message.getMsgType(),
                message.getInsertTime(),
                request.getNewScheduleTime()
        );

        return ApiResponse.success("예약 시간이 변경되었습니다.");
    }

    /**
     * 예약 메시지 삭제 (일괄)
     * DELETE /api/scheduled-messages
     */
    @DeleteMapping
    public ApiResponse<Void> cancelScheduledMessages(
            @RequestBody List<Integer> mSeqs,
            @RequestHeader("Authorization") String token) {

        String accessToken = token.replace("Bearer ", "");
        String userId = userIdResolver.resolveUserId(jwtTokenProvider.getUserId(accessToken));
        Integer userLevel = adminService.getUserLevel(userId);
        String queryUserId = adminService.determineQueryUserIds(userId, userLevel);

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        // 중복 그룹 삭제 방지용 Map
        Map<String, ScheduledMessageResponse> groupMap = new HashMap<>();

        for (Integer mSeq : mSeqs) {
            ScheduledMessageResponse message = scheduledMessageService.getMessageById(mSeq, queryUserId);
            if (message == null) {
                throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND, "예약 메시지를 찾을 수 없습니다: " + mSeq);
            }

            // 10분 이내 발송 예정 메시지 삭제 불가
            if (message.getRequestTime().isBefore(now.plusMinutes(10))) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "전송 10분 이내인 메시지는 삭제할 수 없습니다.");
            }

            // 그룹 키 생성 (msgType + requestTime)
            String key = message.getMsgType() + "_" + message.getRequestTime().toString();
            groupMap.put(key, message);
        }

        // 그룹별로 예약 취소
        for (ScheduledMessageResponse message : groupMap.values()) {
            scheduledMessageService.cancelMessageGroup(
                    message.getUserId(),  // 실제 메시지 소유자
                    message.getMsgType(),
                    message.getInsertTime()
            );
        }

        return ApiResponse.success("삭제되었습니다.");
    }

    /**
     * 암호화된 관리자명 복호화
     */
    private String decryptName(String encryptedName) {
        if (encryptedName == null || encryptedName.isEmpty()) {
            return encryptedName;
        }
        try {
            // Base64 디코딩 후 AES256 복호화
            return CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(encryptedName));
        } catch (Exception e) {
            log.warn("사용자명 복호화 실패: {}", e.getMessage());
            return encryptedName; // 실패 시 원본(암호문) 반환
        }
    }
}
