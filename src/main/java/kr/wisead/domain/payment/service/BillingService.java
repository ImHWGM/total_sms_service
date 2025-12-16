package kr.wisead.domain.payment.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.payment.entity.Balance;
import kr.wisead.mapper.primary.BalanceMapper;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 통합 과금 서비스
 * - QR 코드 방문 과금
 * - 설문 참여 과금
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private final BalanceMapper balanceMapper;
    private final SurveyMasterMapper surveyMasterMapper;
    private final StandardRateService standardRateService;

    // QR 코드 무료 제공 방문 수
    private static final long QR_FREE_VISITS = 3000L;
    // QR 코드 과금 단위 (3000건마다 추가 과금)
    private static final long QR_OVERAGE_UNIT = 3000L;

    /**
     * QR 코드 신청 비용 차감 (최초 신청 시)
     * - 최초 3000건 기본 제공
     *
     * @param userId  사용자 ID
     * @param comment 차감 사유
     */
    @Transactional
    public void deductInitialQrFee(String userId, String comment) {
        Balance latest = balanceMapper.selectLatestBalance(userId);

        if (latest == null) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "잔액 정보가 없습니다.");
        }

        // survey 단가 조회 (QR 신청 비용 = 설문 단가)
        BigDecimal qrFee = standardRateService.getStandardRateWithVat("survey");

        if (latest.getTotalBalance().compareTo(qrFee) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE,
                    "QR 코드 신청 비용이 부족합니다. 필요 금액: " + qrFee + "원");
        }

        BigDecimal newBalance = latest.getTotalBalance().subtract(qrFee);

        Balance balance = Balance.builder()
                .userId(userId)
                .balance(qrFee)
                .totalBalance(newBalance)
                .operation("M")
                .comment(comment != null ? comment : "QR 코드 신청")
                .subtractUnitPrice(latest.getSubtractUnitPrice())
                .smsPrice(latest.getSmsPrice())
                .lmsPrice(latest.getLmsPrice())
                .mmsPrice(latest.getMmsPrice())
                .regId(userId)
                .build();

        balanceMapper.insertBalance(balance);
        log.info("QR 신청 비용 차감 완료: userId={}, fee={}, newBalance={}", userId, qrFee, newBalance);
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

        // 과금 필요 여부 계산
        // 3001~6000: 1회 과금, 6001~9000: 2회 과금, ...
        long paidUnits = (currentVisits - 1 - QR_FREE_VISITS) / QR_OVERAGE_UNIT;
        long requiredUnits = (currentVisits - QR_FREE_VISITS - 1) / QR_OVERAGE_UNIT + 1;

        // 이미 과금된 단위와 필요한 단위가 같으면 추가 과금 없음
        if (paidUnits >= requiredUnits - 1 && currentVisits % QR_OVERAGE_UNIT != 1) {
            // 3001, 6001, 9001 ... 에서만 과금
            if ((currentVisits - QR_FREE_VISITS) % QR_OVERAGE_UNIT != 1) {
                return false;
            }
        }

        // 정확히 3001, 6001, 9001 등에서만 과금
        long overageCount = currentVisits - QR_FREE_VISITS;
        if (overageCount <= 0 || (overageCount - 1) % QR_OVERAGE_UNIT != 0) {
            return false;
        }

        Balance latest = balanceMapper.selectLatestBalance(userId);

        if (latest == null) {
            log.error("잔액 정보가 없어 QR 과금 실패: userId={}", userId);
            // 과금 실패 시 방문 수 롤백
            surveyMasterMapper.rollbackQrCodeVisits(authCodeUrl);
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "잔액 정보가 없습니다.");
        }

        // survey 단가 조회
        BigDecimal overageFee = standardRateService.getStandardRateWithVat("survey");

        if (latest.getTotalBalance().compareTo(overageFee) < 0) {
            log.warn("QR 추가 과금 실패 - 잔액 부족: userId={}, required={}, current={}",
                    userId, overageFee, latest.getTotalBalance());
            // 과금 실패 시 방문 수 롤백
            surveyMasterMapper.rollbackQrCodeVisits(authCodeUrl);
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE,
                    "QR 코드 추가 과금 비용이 부족합니다. 필요 금액: " + overageFee + "원");
        }

        BigDecimal newBalance = latest.getTotalBalance().subtract(overageFee);

        Balance balance = Balance.builder()
                .userId(userId)
                .balance(overageFee)
                .totalBalance(newBalance)
                .operation("M")
                .comment(comment != null ? comment : "QR 코드 추가 과금 (" + currentVisits + "건)")
                .subtractUnitPrice(latest.getSubtractUnitPrice())
                .smsPrice(latest.getSmsPrice())
                .lmsPrice(latest.getLmsPrice())
                .mmsPrice(latest.getMmsPrice())
                .regId(userId)
                .build();

        balanceMapper.insertBalance(balance);
        log.info("QR 추가 과금 완료: userId={}, visits={}, fee={}, newBalance={}",
                userId, currentVisits, overageFee, newBalance);

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
        Balance latest = balanceMapper.selectLatestBalance(userId);

        if (latest == null) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "잔액 정보가 없습니다.");
        }

        BigDecimal unitPrice = latest.getSubtractUnitPrice();
        if (unitPrice == null) {
            unitPrice = standardRateService.getStandardRateWithVat("survey");
        }

        BigDecimal totalCharge = unitPrice.multiply(BigDecimal.valueOf(count));

        if (latest.getTotalBalance().compareTo(totalCharge) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE,
                    "설문 참여 비용이 부족합니다. 필요 금액: " + totalCharge + "원");
        }

        BigDecimal newBalance = latest.getTotalBalance().subtract(totalCharge);

        Balance balance = Balance.builder()
                .userId(userId)
                .balance(totalCharge)
                .totalBalance(newBalance)
                .operation("M")
                .comment(comment != null ? comment : "설문 참여 비용")
                .subtractUnitPrice(latest.getSubtractUnitPrice())
                .smsPrice(latest.getSmsPrice())
                .lmsPrice(latest.getLmsPrice())
                .mmsPrice(latest.getMmsPrice())
                .regId(userId)
                .build();

        balanceMapper.insertBalance(balance);
        log.info("설문 참여 비용 차감 완료: userId={}, count={}, charge={}, newBalance={}",
                userId, count, totalCharge, newBalance);
    }

    /**
     * QR 코드 과금 가능 여부 확인
     *
     * @param userId 사용자 ID
     * @return 과금 가능 여부
     */
    public boolean canChargeQr(String userId) {
        Balance latest = balanceMapper.selectLatestBalance(userId);
        if (latest == null) {
            return false;
        }

        BigDecimal qrFee = standardRateService.getStandardRateWithVat("survey");
        return latest.getTotalBalance().compareTo(qrFee) >= 0;
    }
}
