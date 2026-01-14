package kr.wisead.domain.payment.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.payment.dto.BalanceResponse;
import kr.wisead.domain.payment.dto.ChargeRequest;
import kr.wisead.domain.payment.dto.SmsPriceRequest;
import kr.wisead.domain.payment.entity.Balance;
import kr.wisead.mapper.primary.BalanceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 잔액 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final BalanceMapper balanceMapper;

    private static final BigDecimal VAT = BigDecimal.valueOf(1.1);
    private static final BigDecimal DEFAULT_SURVEY_PRICE = BigDecimal.valueOf(33);
    private static final BigDecimal DEFAULT_SMS_PRICE = BigDecimal.valueOf(11);
    private static final BigDecimal DEFAULT_LMS_PRICE = BigDecimal.valueOf(33);
    private static final BigDecimal DEFAULT_MMS_PRICE = BigDecimal.valueOf(110);

    /**
     * 현재 잔액 조회
     */
    public BalanceResponse getCurrentBalance(String userId) {
        Balance balance = balanceMapper.selectLatestBalance(userId);
        if (balance == null) {
            return BalanceResponse.builder()
                    .userId(userId)
                    .totalBalance(BigDecimal.ZERO)
                    .build();
        }
        return BalanceResponse.from(balance);
    }

    /**
     * 잔액 내역 조회 (페이징)
     */
    public PageResponse<BalanceResponse> getBalanceHistory(String userId, int page, int size) {
        int offset = (page - 1) * size;
        List<Balance> list = balanceMapper.selectBalanceHistory(userId, offset, size);
        int total = balanceMapper.selectBalanceHistoryCount(userId);

        List<BalanceResponse> responses = list.stream()
                .map(BalanceResponse::from)
                .collect(Collectors.toList());

        return PageResponse.of(responses, page, size, total);
    }

    /**
     * 충전
     */
    @Transactional
    public BalanceResponse charge(ChargeRequest request, String operatorId) {
        Balance latest = balanceMapper.selectLatestBalance(request.getUserId());

        BigDecimal currentBalance = BigDecimal.ZERO;
        BigDecimal surveyPrice = DEFAULT_SURVEY_PRICE.multiply(VAT);
        BigDecimal smsPrice = DEFAULT_SMS_PRICE.multiply(VAT);
        BigDecimal lmsPrice = DEFAULT_LMS_PRICE.multiply(VAT);
        BigDecimal mmsPrice = DEFAULT_MMS_PRICE.multiply(VAT);

        if (latest != null) {
            currentBalance = latest.getTotalBalance();
            if (latest.getSubtractUnitPrice() != null) {
                surveyPrice = latest.getSubtractUnitPrice();
            }
            if (latest.getSmsPrice() != null)
                smsPrice = latest.getSmsPrice();
            if (latest.getLmsPrice() != null)
                lmsPrice = latest.getLmsPrice();
            if (latest.getMmsPrice() != null)
                mmsPrice = latest.getMmsPrice();
        }

        BigDecimal newBalance = currentBalance.add(request.getAmount());

        Balance balance = Balance.builder()
                .userId(request.getUserId())
                .balance(request.getAmount())
                .totalBalance(newBalance)
                .operation("P")
                .comment(request.getComment() != null ? request.getComment() : "충전")
                .subtractUnitPrice(surveyPrice)
                .smsPrice(smsPrice)
                .lmsPrice(lmsPrice)
                .mmsPrice(mmsPrice)
                .regId(operatorId)
                .build();

        balanceMapper.insertBalance(balance);
        log.info("충전 완료: userId={}, amount={}, newBalance={}", request.getUserId(), request.getAmount(), newBalance);

        return BalanceResponse.from(balance);
    }

    /**
     * 차감
     */
    @Transactional
    public BalanceResponse deduct(String userId, BigDecimal amount, String comment, String operatorId) {
        Balance latest = balanceMapper.selectLatestBalance(userId);

        if (latest == null || latest.getTotalBalance().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "잔액이 부족합니다.");
        }

        BigDecimal newBalance = latest.getTotalBalance().subtract(amount);

        Balance balance = Balance.builder()
                .userId(userId)
                .balance(amount)
                .totalBalance(newBalance)
                .operation("M")
                .comment(comment)
                .subtractUnitPrice(latest.getSubtractUnitPrice())
                .smsPrice(latest.getSmsPrice())
                .lmsPrice(latest.getLmsPrice())
                .mmsPrice(latest.getMmsPrice())
                .regId(operatorId)
                .build();

        balanceMapper.insertBalance(balance);
        log.info("차감 완료: userId={}, amount={}, newBalance={}", userId, amount, newBalance);

        return BalanceResponse.from(balance);
    }

    /**
     * 메시지 발송 비용 차감
     */
    @Transactional
    public void deductMessageCharge(String userId, int count, String msgType, String comment) {
        Balance latest = balanceMapper.selectLatestBalance(userId);

        if (latest == null) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "잔액 정보가 없습니다.");
        }

        BigDecimal unitPrice = switch (msgType.toUpperCase()) {
            case "SMS" -> latest.getSmsPrice();
            case "LMS" -> latest.getLmsPrice();
            case "MMS" -> latest.getMmsPrice();
            default -> latest.getSubtractUnitPrice();
        };

        BigDecimal totalCharge = unitPrice.multiply(BigDecimal.valueOf(count));

        if (latest.getTotalBalance().compareTo(totalCharge) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "잔액이 부족합니다.");
        }

        BigDecimal newBalance = latest.getTotalBalance().subtract(totalCharge);

        Balance balance = Balance.builder()
                .userId(userId)
                .balance(totalCharge)
                .totalBalance(newBalance)
                .operation("M")
                .comment(comment)
                .subtractUnitPrice(latest.getSubtractUnitPrice())
                .smsPrice(latest.getSmsPrice())
                .lmsPrice(latest.getLmsPrice())
                .mmsPrice(latest.getMmsPrice())
                .regId(userId)
                .build();

        balanceMapper.insertBalance(balance);
        log.info("메시지 비용 차감: userId={}, count={}, type={}, charge={}, newBalance={}",
                userId, count, msgType, totalCharge, newBalance);
    }

    /**
     * 잔액 충분 여부 확인
     */
    public boolean hasEnoughBalance(String userId, BigDecimal requiredAmount) {
        Balance latest = balanceMapper.selectLatestBalance(userId);
        if (latest == null) {
            return false;
        }
        return latest.getTotalBalance().compareTo(requiredAmount) >= 0;
    }

    /**
     * 문자 요금 설정
     */
    @Transactional
    public BalanceResponse updateSmsPrice(SmsPriceRequest request, String operatorId) {
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 ID가 필요합니다.");
        }

        Balance latest = balanceMapper.selectLatestBalance(request.getUserId());

        if (latest == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "해당 사용자의 잔액 정보가 없습니다.");
        }

        // 변경된 단가만 업데이트, 없으면 기존 값 유지
        BigDecimal smsPrice = request.getSmsPrice() != null ? request.getSmsPrice() : latest.getSmsPrice();
        BigDecimal lmsPrice = request.getLmsPrice() != null ? request.getLmsPrice() : latest.getLmsPrice();
        BigDecimal mmsPrice = request.getMmsPrice() != null ? request.getMmsPrice() : latest.getMmsPrice();

        // 단가 변경 이력을 위한 레코드 추가 (잔액 변동 없이 단가만 변경)
        Balance balance = Balance.builder()
                .userId(request.getUserId())
                .balance(BigDecimal.ZERO)
                .totalBalance(latest.getTotalBalance())
                .operation("U") // Update
                .comment("문자 요금 설정 변경")
                .subtractUnitPrice(latest.getSubtractUnitPrice())
                .smsPrice(smsPrice)
                .lmsPrice(lmsPrice)
                .mmsPrice(mmsPrice)
                .regId(operatorId)
                .build();

        balanceMapper.insertBalance(balance);
        log.info("문자 요금 변경: userId={}, SMS={}, LMS={}, MMS={}",
                request.getUserId(), smsPrice, lmsPrice, mmsPrice);

        return BalanceResponse.from(balance);
    }
}
