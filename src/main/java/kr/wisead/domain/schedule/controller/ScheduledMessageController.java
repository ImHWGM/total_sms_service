package kr.wisead.domain.schedule.controller;

import jakarta.validation.Valid;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.schedule.dto.RescheduleRequest;
import kr.wisead.domain.schedule.dto.ScheduledMessageResponse;
import kr.wisead.domain.schedule.dto.ScheduledMessageSearchRequest;
import kr.wisead.domain.schedule.service.ScheduledMessageService;
import kr.wisead.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        String userId = jwtTokenProvider.getUserId(accessToken);
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
     * 예약 메시지 상세 조회
     * GET /api/scheduled-messages/{mSeq}
     */
    @GetMapping("/{mSeq}")
    public ApiResponse<ScheduledMessageResponse> getScheduledMessageById(
            @PathVariable int mSeq,
            @RequestHeader("Authorization") String token) {

        String accessToken = token.replace("Bearer ", "");
        String userId = jwtTokenProvider.getUserId(accessToken);
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
        String userId = jwtTokenProvider.getUserId(accessToken);
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
        String userId = jwtTokenProvider.getUserId(accessToken);
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

}
