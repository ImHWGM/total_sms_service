package kr.wisead.domain.schedule.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.payment.dto.BalanceResponse;
import kr.wisead.domain.payment.dto.ChargeRequest;
import kr.wisead.domain.payment.service.BalanceService;
import kr.wisead.domain.schedule.dto.ScheduledMessageResponse;
import kr.wisead.domain.schedule.dto.ScheduledMessageSearchRequest;
import kr.wisead.domain.schedule.entity.ScheduledMessage;
import kr.wisead.mapper.sms.ScheduledMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 예약 메시지 서비스 (리팩토링 버전)
 * - BalanceService 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledMessageService {

    private final ScheduledMessageMapper scheduledMessageMapper;
    private final BalanceService balanceService;

    /**
     * 예약 메시지 목록 조회
     */
    @Transactional(readOnly = true)
    public PageResponse<ScheduledMessageResponse> getScheduledMessages(ScheduledMessageSearchRequest request) {
        String convertedMsgType = request.getConvertedMsgType();

        List<ScheduledMessage> messages = scheduledMessageMapper.selectScheduledMessages(
                request.getUserId(),
                convertedMsgType,
                request.getSearchText(),
                request.getOffset(),
                request.getSize()
        );

        int total = scheduledMessageMapper.countScheduledMessages(
                request.getUserId(),
                convertedMsgType,
                request.getSearchText()
        );

        List<ScheduledMessageResponse> responses = messages.stream()
                .map(ScheduledMessageResponse::from)
                .toList();

        return PageResponse.of(responses, request.getPage(), request.getSize(), total);
    }

    /**
     * 예약 메시지 상세 조회
     */
    @Transactional(readOnly = true)
    public ScheduledMessageResponse getMessageById(int mSeq, String userId) {
        ScheduledMessage message = scheduledMessageMapper.selectMessageById(mSeq, userId);
        return ScheduledMessageResponse.from(message);
    }

    /**
     * 예약 시간 변경
     */
    @Transactional
    public void rescheduleMessageGroup(String userId, String msgType, LocalDateTime insertTime,
                                        LocalDateTime newScheduleTime) {
        // 10분 이내 예약은 불가
        LocalDateTime minTime = LocalDateTime.now().plusMinutes(10);
        if (newScheduleTime.isBefore(minTime)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "예약일시는 현재 시각으로부터 최소 10분 이후여야 합니다.");
        }

        int updated = scheduledMessageMapper.updateGroupRequestTime(userId, msgType, insertTime, newScheduleTime);
        log.info("예약 시간 변경: userId={}, msgType={}, 변경건수={}", userId, msgType, updated);
    }

    /**
     * 예약 취소 (환불 포함)
     */
    @Transactional
    public void cancelMessageGroup(String userId, String msgType, LocalDateTime insertTime) {
        // 1. 삭제 대상 메시지 건수 조회
        int messageCount = scheduledMessageMapper.countMessagesForCancellation(userId, msgType, insertTime);
        log.info("예약 취소 - 사용자: {}, 메시지 타입: {}, 개수: {}", userId, msgType, messageCount);

        if (messageCount > 0) {
            // 2. 현재 잔액 정보 조회
            BalanceResponse currentBalance = balanceService.getCurrentBalance(userId);
            if (currentBalance == null) {
                log.error("예약 취소 - 사용자 잔액 정보를 찾을 수 없습니다: {}", userId);
                throw new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "사용자 잔액 정보를 찾을 수 없습니다.");
            }

            // 3. 환불 금액 계산
            BigDecimal refundAmount = calculateRefundAmount(msgType, messageCount, currentBalance);
            log.info("예약 취소 - 환불 금액: {}", refundAmount);

            // 4. 환불 처리 (BalanceService.charge 사용)
            if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                String comment = "예약문자 취소 환불: " + getTypeLabel(msgType) + " " + messageCount + "건";

                ChargeRequest chargeRequest = ChargeRequest.builder()
                        .userId(userId)
                        .amount(refundAmount)
                        .comment(comment)
                        .build();

                balanceService.charge(chargeRequest, userId);
                log.info("예약 취소 - 환불 완료: {} 원", refundAmount);
            }
        }

        // 5. 예약 메시지 삭제
        int deleted = scheduledMessageMapper.deleteScheduledMessageGroup(userId, msgType, insertTime);
        log.info("예약 취소 - 메시지 삭제 완료: {}건", deleted);
    }

    // ==================== Private Methods ====================

    private BigDecimal calculateRefundAmount(String msgType, int messageCount, BalanceResponse balance) {
        BigDecimal unitPrice = switch (msgType) {
            case "S" -> balance.getSmsPrice() != null ? balance.getSmsPrice() : balance.getSubtractUnitPrice();
            case "L" -> balance.getLmsPrice() != null ? balance.getLmsPrice() : balance.getSubtractUnitPrice();
            case "M" -> balance.getMmsPrice() != null ? balance.getMmsPrice() : balance.getSubtractUnitPrice();
            default -> balance.getSubtractUnitPrice();
        };

        if (unitPrice == null) {
            unitPrice = BigDecimal.ZERO;
        }

        return unitPrice.multiply(BigDecimal.valueOf(messageCount));
    }

    private String getTypeLabel(String msgType) {
        return switch (msgType) {
            case "S" -> "SMS";
            case "L" -> "LMS";
            case "M" -> "MMS";
            default -> "문자";
        };
    }
}