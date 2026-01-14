package kr.wisead.domain.payment.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 통합 과금 서비스 (리팩토링 버전)
 * - QR 코드 방문 과금
 * - 설문 참여 과금
 * - 내부적으로 WalletService 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final WalletService walletService;
    private final SurveyMasterMapper surveyMasterMapper;

    // QR 코드 무료 제공 방문 수
    private static final long QR_FREE_VISITS = 3000L;
    // QR 코드 과금 단위 (3000건마다 추가 과금)
    private static final long QR_OVERAGE_UNIT = 3000L;
    // 서비스 ID
    private static final String SERVICE_QR = "qr_code";
    private static final String SERVICE_SURVEY = "survey";

    /**
     * QR 코드 신청 비용 차감 (최초 신청 시)
     * - 최초 3000건 기본 제공
     *
     * @param userId  사용자 ID
     * @param comment 차감 사유
     */
    @Transactional
    public void deductInitialQrFee(String userId, String comment) {
        // QR 단가 조회
        BigDecimal qrFee = walletService.getAppliedRate(userId, SERVICE_QR);

        // 잔액 확인
        if (!walletService.hasEnoughBalance(userId, qrFee)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE,
                    "QR 코드 신청 비용이 부족합니다. 필요 금액: " + qrFee + "원");
        }

        // 우선순위 차감 (BONUS → POINT → CASH)
        String txGroupId = walletService.deductWithPriority(
                userId,
                SERVICE_QR,
                BigDecimal.ONE,  // 수량 1
                comment != null ? comment : "QR 코드 신청"
        );

        log.info("QR 신청 비용 차감 완료: userId={}, fee={}, txGroupId={}", userId, qrFee, txGroupId);
    }

    /**
     * QR 코드 추가 방문 과금 (3000건 초과 시)
     * - 3000건 단위로 추가 과금
     *
     * @param userId      사용자 ID
     * @param comment     차감 사유
     * @param authCodeUrl QR 코드 URL (방문 수 조회용)
     * @return 과금 발생 여부
     */
    @Transactional
    public boolean deductQrOverageCharge(String userId, String comment, String authCodeUrl) {
        // 현재 방문 수 조회
        Long currentVisits = surveyMasterMapper.selectQrCodeVisits(authCodeUrl);

        if (currentVisits == null) {
            log.warn("QR 코드 방문 수를 조회할 수 없습니다: authCodeUrl={}", authCodeUrl);
            return false;
        }

        // 무료 제공 건수(3000건) 이하면 과금 없음
        if (currentVisits <= QR_FREE_VISITS) {
            return false;
        }

        // 정확히 3001, 6001, 9001 등에서만 과금
        long overageCount = currentVisits - QR_FREE_VISITS;
        if (overageCount <= 0 || (overageCount - 1) % QR_OVERAGE_UNIT != 0) {
            return false;
        }

        // QR 단가 조회
        BigDecimal overageFee = walletService.getAppliedRate(userId, SERVICE_QR);

        // 잔액 확인
        if (!walletService.hasEnoughBalance(userId, overageFee)) {
            log.warn("QR 추가 과금 실패 - 잔액 부족: userId={}, required={}", userId, overageFee);
            // 과금 실패 시 방문 수 롤백
            surveyMasterMapper.rollbackQrCodeVisits(authCodeUrl);
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE,
                    "QR 코드 추가 과금 비용이 부족합니다. 필요 금액: " + overageFee + "원");
        }

        // 우선순위 차감
        String txGroupId = walletService.deductWithPriority(
                userId,
                SERVICE_QR,
                BigDecimal.ONE,
                comment != null ? comment : "QR 코드 추가 과금 (" + currentVisits + "건)"
        );

        log.info("QR 추가 과금 완료: userId={}, visits={}, fee={}, txGroupId={}",
                userId, currentVisits, overageFee, txGroupId);

        return true;
    }

    /**
     * 설문 참여 비용 차감
     *
     * @param userId  사용자 ID
     * @param count   참여 건수
     * @param comment 차감 사유
     */
    @Transactional
    public void deductSurveyCharge(String userId, int count, String comment) {
        BigDecimal quantity = BigDecimal.valueOf(count);

        // 설문 단가 조회
        BigDecimal unitPrice = walletService.getAppliedRate(userId, SERVICE_SURVEY);
        BigDecimal totalCharge = unitPrice.multiply(quantity);

        // 잔액 확인
        if (!walletService.hasEnoughBalance(userId, totalCharge)) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE,
                    "설문 참여 비용이 부족합니다. 필요 금액: " + totalCharge + "원");
        }

        // 우선순위 차감
        String txGroupId = walletService.deductWithPriority(
                userId,
                SERVICE_SURVEY,
                quantity,
                comment != null ? comment : "설문 참여 비용"
        );

        log.info("설문 참여 비용 차감 완료: userId={}, count={}, charge={}, txGroupId={}",
                userId, count, totalCharge, txGroupId);
    }

    /**
     * QR 코드 과금 가능 여부 확인
     *
     * @param userId 사용자 ID
     * @return 과금 가능 여부
     */
    public boolean canChargeQr(String userId) {
        try {
            BigDecimal qrFee = walletService.getAppliedRate(userId, SERVICE_QR);
            return walletService.hasEnoughBalance(userId, qrFee);
        } catch (BusinessException e) {
            log.warn("QR 단가 조회 실패: userId={}, error={}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * 설문 과금 가능 여부 확인
     *
     * @param userId 사용자 ID
     * @param count  참여 건수
     * @return 과금 가능 여부
     */
    public boolean canChargeSurvey(String userId, int count) {
        try {
            BigDecimal unitPrice = walletService.getAppliedRate(userId, SERVICE_SURVEY);
            BigDecimal totalCharge = unitPrice.multiply(BigDecimal.valueOf(count));
            return walletService.hasEnoughBalance(userId, totalCharge);
        } catch (BusinessException e) {
            log.warn("설문 단가 조회 실패: userId={}, error={}", userId, e.getMessage());
            return false;
        }
    }
}