package kr.wisead.domain.payment.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.payment.dto.*;
import kr.wisead.domain.payment.entity.Transaction;
import kr.wisead.domain.payment.entity.UserServiceRate;
import kr.wisead.mapper.primary.BalanceMapper;
import kr.wisead.mapper.primary.TransactionMapper;
import kr.wisead.mapper.primary.UserServiceRateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 잔액 관리 서비스 (리팩토링 버전)
 * - 내부적으로 WalletService 사용
 * - 기존 API 호환성 유지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final WalletService walletService;
    private final TransactionMapper transactionMapper;
    private final UserServiceRateMapper userServiceRateMapper;
    private final BalanceMapper balanceMapper; // 레거시 호환용

    /**
     * 현재 잔액 조회
     * - N+1 최적화: 4개 단가 조회를 한 번에 처리 (최대 8회 → 최대 2회)
     */
    public BalanceResponse getCurrentBalance(String userId) {
        WalletSummaryResponse summary = walletService.getWalletSummary(userId);

        // N+1 최적화: 모든 단가를 한 번에 조회
        Map<String, BigDecimal> rates = getAllRates(userId);

        return BalanceResponse.builder()
                .userId(userId)
                .totalBalance(summary.getTotal())
                .balance(BigDecimal.ZERO)
                .operation("Q") // Query
                .operationName("조회")
                .smsPrice(rates.getOrDefault("msg_sms", BigDecimal.valueOf(12.1)))
                .lmsPrice(rates.getOrDefault("msg_lms", BigDecimal.valueOf(36.3)))
                .mmsPrice(rates.getOrDefault("msg_mms", BigDecimal.valueOf(121)))
                .subtractUnitPrice(rates.getOrDefault("survey", BigDecimal.valueOf(36.3)))
                .build();
    }

    /**
     * 확장된 잔액 조회 (CASH + POINT + BONUS 분리)
     */
    public WalletSummaryResponse getWalletSummary(String userId) {
        return walletService.getWalletSummary(userId);
    }

    /**
     * 활성 Lot 목록 조회
     */
    public List<WalletLotResponse> getActiveLots(String userId) {
        return walletService.getActiveLots(userId);
    }

    /**
     * 잔액 내역 조회 (페이징) - 새 트랜잭션 테이블 사용
     */
    public PageResponse<TransactionResponse> getTransactionHistory(String userId, int page, int size) {
        int offset = (page - 1) * size;
        List<Transaction> list = transactionMapper.selectHistory(userId, offset, size);
        int total = transactionMapper.selectHistoryCount(userId);

        List<TransactionResponse> responses = list.stream()
                .map(TransactionResponse::from)
                .collect(Collectors.toList());

        return PageResponse.of(responses, page, size, total);
    }

    /**
     * 잔액 내역 조회 (페이징) - 레거시 호환용
     */
    public PageResponse<BalanceResponse> getBalanceHistory(String userId, int page, int size) {
        int offset = (page - 1) * size;
        List<Transaction> list = transactionMapper.selectHistory(userId, offset, size);
        int total = transactionMapper.selectHistoryCount(userId);

        List<BalanceResponse> responses = list.stream()
                .map(this::convertToBalanceResponse)
                .collect(Collectors.toList());

        return PageResponse.of(responses, page, size, total);
    }

    /**
     * 충전
     */
    @Transactional
    public BalanceResponse charge(ChargeRequest request, String operatorId) {
        String comment = request.getComment() != null ? request.getComment() : "충전";
        TransactionResponse txResponse = walletService.charge(request.getUserId(), request.getAmount(), comment);

        log.info("충전 완료: userId={}, amount={}, balanceAfter={}",
                request.getUserId(), request.getAmount(), txResponse.getBalanceAfter());

        return BalanceResponse.builder()
                .seq(txResponse.getSeq())
                .userId(txResponse.getUserId())
                .balance(txResponse.getAmount())
                .totalBalance(txResponse.getBalanceAfter())
                .operation("P")
                .operationName("충전")
                .comment(comment)
                .regDate(txResponse.getRegDate())
                .smsPrice(getSmsPrice(request.getUserId()))
                .lmsPrice(getLmsPrice(request.getUserId()))
                .mmsPrice(getMmsPrice(request.getUserId()))
                .subtractUnitPrice(getSurveyPrice(request.getUserId()))
                .build();
    }

    /**
     * 차감 (금액 직접 지정)
     */
    @Transactional
    public BalanceResponse deduct(String userId, BigDecimal amount, String comment, String operatorId) {
        // 잔액 확인
        if (!walletService.hasEnoughBalance(userId, amount)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "잔액이 부족합니다.");
        }

        // 우선순위 차감 (BONUS → POINT → CASH) - 금액 직접 차감
        String txGroupId = walletService.deductByAmount(userId, amount, comment);

        WalletSummaryResponse summary = walletService.getWalletSummary(userId);

        log.info("차감 완료: userId={}, amount={}, balanceAfter={}", userId, amount, summary.getTotal());

        return BalanceResponse.builder()
                .userId(userId)
                .balance(amount)
                .totalBalance(summary.getTotal())
                .operation("M")
                .operationName("차감")
                .comment(comment)
                .smsPrice(getSmsPrice(userId))
                .lmsPrice(getLmsPrice(userId))
                .mmsPrice(getMmsPrice(userId))
                .subtractUnitPrice(getSurveyPrice(userId))
                .build();
    }

    /**
     * 메시지 발송 비용 차감
     */
    @Transactional
    public void deductMessageCharge(String userId, int count, String msgType, String comment) {
        String serviceId = getServiceIdByMsgType(msgType);
        BigDecimal quantity = BigDecimal.valueOf(count);

        // 단가 조회
        BigDecimal unitPrice = walletService.getAppliedRate(userId, serviceId);
        BigDecimal totalCharge = unitPrice.multiply(quantity);

        // 잔액 확인
        if (!walletService.hasEnoughBalance(userId, totalCharge)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "잔액이 부족합니다.");
        }

        // 우선순위 차감
        String txGroupId = walletService.deductWithPriority(userId, serviceId, quantity, comment);

        log.info("메시지 비용 차감: userId={}, count={}, type={}, charge={}, txGroupId={}",
                userId, count, msgType, totalCharge, txGroupId);
    }

    /**
     * 잔액 충분 여부 확인
     */
    public boolean hasEnoughBalance(String userId, BigDecimal requiredAmount) {
        return walletService.hasEnoughBalance(userId, requiredAmount);
    }

    /**
     * 문자 요금 설정 (사용자별 단가)
     */
    @Transactional
    public BalanceResponse updateSmsPrice(SmsPriceRequest request, String operatorId) {
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 ID가 필요합니다.");
        }

        LocalDate today = LocalDate.now();

        // SMS 단가 설정
        if (request.getSmsPrice() != null) {
            updateUserServiceRate(request.getUserId(), "msg_sms", request.getSmsPrice(), today);
        }

        // LMS 단가 설정
        if (request.getLmsPrice() != null) {
            updateUserServiceRate(request.getUserId(), "msg_lms", request.getLmsPrice(), today);
        }

        // MMS 단가 설정
        if (request.getMmsPrice() != null) {
            updateUserServiceRate(request.getUserId(), "msg_mms", request.getMmsPrice(), today);
        }

        log.info("문자 요금 변경: userId={}, SMS={}, LMS={}, MMS={}",
                request.getUserId(), request.getSmsPrice(), request.getLmsPrice(), request.getMmsPrice());

        WalletSummaryResponse summary = walletService.getWalletSummary(request.getUserId());

        return BalanceResponse.builder()
                .userId(request.getUserId())
                .totalBalance(summary.getTotal())
                .balance(BigDecimal.ZERO)
                .operation("U")
                .operationName("단가변경")
                .comment("문자 요금 설정 변경")
                .smsPrice(getSmsPrice(request.getUserId()))
                .lmsPrice(getLmsPrice(request.getUserId()))
                .mmsPrice(getMmsPrice(request.getUserId()))
                .subtractUnitPrice(getSurveyPrice(request.getUserId()))
                .build();
    }

    /**
     * 환불 미리보기
     */
    @Transactional(readOnly = true)
    public RefundPreviewResponse previewRefund(String txGroupId) {
        return walletService.previewRefund(txGroupId);
    }

    /**
     * 환불 처리
     */
    @Transactional
    public RefundResult refund(String txGroupId) {
        return walletService.refundByGroup(txGroupId);
    }

    // ========== Private Helper Methods ==========

    private static final BigDecimal VAT_RATE = new BigDecimal("1.1");

    /**
     * 모든 서비스 단가를 한 번에 조회 (N+1 최적화)
     * - 최대 8회 DB 호출 → 최대 2회 DB 호출
     */
    private Map<String, BigDecimal> getAllRates(String userId) {
        Map<String, BigDecimal> result = new HashMap<>();
        LocalDate today = LocalDate.now();

        // 1. 사용자 단가 조회 (VAT 포함) - 1회 쿼리
        List<UserServiceRate> userRates = userServiceRateMapper.selectAllActiveRates(userId, today);
        for (UserServiceRate rate : userRates) {
            result.put(rate.getServiceId(), rate.getRate());
        }

        // 2. 기준 단가 조회 (VAT 미포함) - 1회 쿼리
        List<UserServiceRateMapper.ServiceRateEntry> standardRates = userServiceRateMapper.selectAllStandardRates();
        for (UserServiceRateMapper.ServiceRateEntry entry : standardRates) {
            // 사용자 단가가 없는 경우에만 기준 단가 × VAT 적용
            if (!result.containsKey(entry.serviceId())) {
                BigDecimal rateWithVat = entry.rate().multiply(VAT_RATE).setScale(2, java.math.RoundingMode.HALF_UP);
                result.put(entry.serviceId(), rateWithVat);
            }
        }

        return result;
    }

    private String getServiceIdByMsgType(String msgType) {
        return switch (msgType.toUpperCase()) {
            case "SMS" -> "msg_sms";
            case "LMS" -> "msg_lms";
            case "MMS" -> "msg_mms";
            default -> "survey";
        };
    }

    private BigDecimal getSmsPrice(String userId) {
        return getRate(userId, "msg_sms", BigDecimal.valueOf(12.1));
    }

    private BigDecimal getLmsPrice(String userId) {
        return getRate(userId, "msg_lms", BigDecimal.valueOf(36.3));
    }

    private BigDecimal getMmsPrice(String userId) {
        return getRate(userId, "msg_mms", BigDecimal.valueOf(121));
    }

    private BigDecimal getSurveyPrice(String userId) {
        return getRate(userId, "survey", BigDecimal.valueOf(36.3));
    }

    private BigDecimal getRate(String userId, String serviceId, BigDecimal defaultRate) {
        try {
            // 1. 사용자 단가 조회 (VAT 포함)
            BigDecimal userRate = userServiceRateMapper.selectUserRate(userId, serviceId, LocalDate.now());
            if (userRate != null) {
                return userRate;
            }

            // 2. 기준 단가 조회 (VAT 미포함)
            BigDecimal standardRate = userServiceRateMapper.selectStandardRate(serviceId);
            if (standardRate != null) {
                return standardRate.multiply(VAT_RATE).setScale(2, java.math.RoundingMode.HALF_UP);
            }

            return defaultRate;
        } catch (Exception e) {
            return defaultRate;
        }
    }

    private void updateUserServiceRate(String userId, String serviceId, BigDecimal rate, LocalDate startDate) {
        // 기존 유효한 단가 종료 처리
        userServiceRateMapper.selectActiveRate(userId, serviceId, startDate)
                .ifPresent(existing -> {
                    userServiceRateMapper.updateEndDate(existing.getSeq(), startDate.minusDays(1));
                });

        // 새 단가 등록
        UserServiceRate newRate = UserServiceRate.create(userId, serviceId, rate, startDate);
        userServiceRateMapper.insert(newRate);
    }

    private BalanceResponse convertToBalanceResponse(Transaction tx) {
        String opName = switch (tx.getTxType()) {
            case "CHARGE" -> "충전";
            case "DEDUCT" -> "차감";
            case "REFUND" -> "환불";
            default -> tx.getTxType();
        };

        String operation = switch (tx.getTxType()) {
            case "CHARGE" -> "P";
            case "DEDUCT" -> "M";
            case "REFUND" -> "R";
            default -> "E";
        };

        return BalanceResponse.builder()
                .seq(tx.getSeq())
                .userId(tx.getUserId())
                .balance(tx.getAmount())
                .totalBalance(tx.getBalanceAfter())
                .operation(operation)
                .operationName(opName)
                .comment(tx.getComment())
                .regDate(tx.getRegDate())
                .build();
    }
}