package kr.wisead.domain.payment.service;

import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.payment.dto.ChargeRequest;
import kr.wisead.domain.payment.entity.ChargeBonusEvent;
import kr.wisead.domain.payment.entity.Payment;
import kr.wisead.mapper.primary.ChargeBonusEventMapper;
import kr.wisead.mapper.primary.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mup.mcash.module.common.McashCipher.McashCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 결제 서비스 (KG모빌리언스 연동)
 * - Wallet 시스템 기반 충전 처리
 * - 충전 보너스 이벤트 지원
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final BalanceService balanceService;
    private final WalletService walletService;
    private final ChargeBonusEventMapper chargeBonusEventMapper;

    /**
     * 결제 결과 처리
     * - CASH 충전 (Wallet 시스템)
     * - 활성 보너스 이벤트 시 POINT 추가 적립
     */
    @Transactional
    public boolean processPayment(Map<String, String> paymentResult) {
        try {
            String tradeId = paymentResult.get("Tradeid");

            // 이미 처리된 결제인지 확인
            if (paymentMapper.existsByTradeId(tradeId)) {
                log.info("이미 처리된 결제입니다. tradeId={}", tradeId);
                return true;
            }

            // 결제 정보 저장
            Payment payment = convertToPayment(paymentResult);
            paymentMapper.insertPayment(payment);
            log.info("결제 정보 저장 완료: tradeId={}", tradeId);

            // 잔액 충전 처리 (CASH → Wallet)
            String userId = paymentResult.get("Userid");
            String amountStr = paymentResult.get("Prdtprice");
            BigDecimal chargeAmount = new BigDecimal(amountStr);

            // 1. CASH 충전 (WalletService 직접 호출)
            walletService.charge(userId, chargeAmount, "KG결제 충전 (tradeId: " + tradeId + ")");
            log.info("CASH 충전 완료: userId={}, amount={}", userId, chargeAmount);

            // 2. 보너스 포인트 이벤트 처리
            grantBonusPointIfEligible(userId, chargeAmount, tradeId);

            return true;
        } catch (Exception e) {
            log.error("결제 처리 중 오류 발생", e);
            return false;
        }
    }

    /**
     * 충전 보너스 포인트 적립 (이벤트 조건 충족 시)
     */
    private void grantBonusPointIfEligible(String userId, BigDecimal chargeAmount, String tradeId) {
        try {
            LocalDate today = LocalDate.now();

            // 활성 이벤트 조회 (충전 금액 조건 포함)
            List<ChargeBonusEvent> activeEvents = chargeBonusEventMapper.selectActiveEvents(today, chargeAmount);

            if (activeEvents.isEmpty()) {
                log.debug("적용 가능한 충전 보너스 이벤트 없음: userId={}, chargeAmount={}", userId, chargeAmount);
                return;
            }

            // 가장 유리한 이벤트 적용 (첫 번째 = 보너스율 높은 순)
            ChargeBonusEvent event = activeEvents.get(0);
            BigDecimal bonusAmount = event.calculateBonus(chargeAmount);

            if (bonusAmount.compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("보너스 금액이 0원: eventSeq={}", event.getEventSeq());
                return;
            }

            // 보너스 포인트 만료일 계산
            LocalDate expireDate = event.calculateExpireDate(today);

            // POINT 적립 (Wallet Lot 시스템)
            String source = String.format("충전 보너스 [%s] (tradeId: %s)", event.getEventName(), tradeId);
            walletService.grantPoint(userId, bonusAmount, expireDate, source);

            log.info("충전 보너스 포인트 적립 완료: userId={}, chargeAmount={}, bonusAmount={}, eventName={}, expireDate={}",
                    userId, chargeAmount, bonusAmount, event.getEventName(), expireDate);

        } catch (Exception e) {
            // 보너스 적립 실패는 결제 실패로 처리하지 않음 (로그만 남김)
            log.error("충전 보너스 포인트 적립 중 오류 (결제는 정상 처리됨): userId={}, chargeAmount={}", userId, chargeAmount, e);
        }
    }

    /**
     * 결제 검증
     * KG모빌리언스 결제 결과의 필수 파라미터 및 위변조 검증
     */
    public boolean validatePayment(Map<String, String> paymentResult) {
        // 필수 파라미터 확인
        String[] requiredParams = {"Resultcd", "Mobilid", "Svcid", "Tradeid", "Prdtprice", "Signdate", "chkValue"};
        for (String param : requiredParams) {
            if (!paymentResult.containsKey(param) || paymentResult.get(param) == null) {
                log.error("필수 파라미터가 누락되었습니다: {}", param);
                return false;
            }
        }

        // 결과 코드 확인
        String resultCd = paymentResult.get("Resultcd");
        if (!"0000".equals(resultCd)) {
            log.error("결제가 실패했습니다. 결과코드: {}", resultCd);
            return false;
        }

        // 결제 금액 유효성 검증
        try {
            String prdtPrice = paymentResult.get("Prdtprice");
            BigDecimal amount = new BigDecimal(prdtPrice);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                log.error("결제 금액이 유효하지 않습니다: {}", prdtPrice);
                return false;
            }
        } catch (NumberFormatException e) {
            log.error("결제 금액 파싱 실패: {}", paymentResult.get("Prdtprice"));
            return false;
        }

        // 위변조 검증 (chkValue 검증)
        if (!validateCheckValue(paymentResult)) {
            log.error("결제 정보가 위변조 되었습니다.");
            return false;
        }

        return true;
    }

    /**
     * 결제 위변조 검증
     * KG모빌리언스 McashCipher를 사용한 chkValue 검증
     */
    private boolean validateCheckValue(Map<String, String> paymentResult) {
        try {
            String mobilId = paymentResult.get("Mobilid");
            String svcId = paymentResult.get("Svcid");
            String tradeId = paymentResult.get("Tradeid");
            String signDate = paymentResult.get("Signdate");
            String prdtPrice = paymentResult.get("Prdtprice");
            String chkValue = paymentResult.get("chkValue");

            if (chkValue == null || chkValue.isEmpty()) {
                log.warn("chkValue가 비어있습니다.");
                return false;
            }

            // 체크값 원본 문자열 생성 (KG모빌리언스 규격)
            String cpChkValue = "Mobilid=" + mobilId +
                    "&Mrchid=null" +
                    "&Svcid=" + svcId +
                    "&Tradeid=" + tradeId +
                    "&Signdate=" + signDate +
                    "&Prdtprice=" + prdtPrice;

            // McashCipher를 사용한 암호화 검증
            String encChkValue = McashCipher.encodeString(cpChkValue, tradeId);

            if (!encChkValue.equals(chkValue)) {
                log.error("결제 정보 위변조 감지 - 기대값: {}, 수신값: {}", encChkValue, chkValue);
                return false;
            }

            log.info("결제 위변조 검증 통과 - tradeId: {}, signDate: {}", tradeId, signDate);
            return true;
        } catch (Exception e) {
            log.error("결제 검증 중 오류 발생: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 결제 내역 조회 (페이징)
     */
    public PageResponse<Payment> getPaymentHistory(String userId, int page, int size) {
        int offset = (page - 1) * size;
        List<Payment> list = paymentMapper.selectPaymentHistory(userId, offset, size);
        int total = paymentMapper.selectPaymentHistoryCount(userId);

        return PageResponse.of(list, page, size, total);
    }

    /**
     * 거래 ID로 결제 정보 조회
     */
    public Payment getPaymentByTradeId(String tradeId) {
        return paymentMapper.selectByTradeId(tradeId);
    }

    /**
     * 결제 결과 정보 수집 (클라이언트 표시용)
     */
    public Map<String, Object> collectPaymentResult(Map<String, String> paymentResult) {
        Map<String, Object> result = new java.util.HashMap<>();

        result.put("resultCd", paymentResult.get("Resultcd"));
        result.put("resultMsg", paymentResult.get("Resultmsg"));
        result.put("tradeId", paymentResult.get("Tradeid"));
        result.put("prdtNm", paymentResult.get("Prdtnm"));
        result.put("prdtPrice", paymentResult.get("Prdtprice"));
        result.put("signDate", paymentResult.get("Signdate"));
        result.put("cardName", paymentResult.get("Cardname"));
        result.put("apprNo", paymentResult.get("Apprno"));

        // 결제 성공 여부
        boolean isSuccess = "0000".equals(paymentResult.get("Resultcd"));
        result.put("success", isSuccess);

        return result;
    }

    /**
     * Map을 Payment Entity로 변환
     */
    private Payment convertToPayment(Map<String, String> paymentResult) {
        return Payment.builder()
                .svcId(paymentResult.get("Svcid"))
                .mobilId(paymentResult.get("Mobilid"))
                .tradeId(paymentResult.get("Tradeid"))
                .prdtNm(paymentResult.get("Prdtnm"))
                .prdtPrice(paymentResult.get("Prdtprice"))
                .resultCd(paymentResult.get("Resultcd"))
                .signDate(paymentResult.get("Signdate"))
                .userId(paymentResult.get("Userid"))
                .userName(paymentResult.get("Username"))
                .payerEmail(paymentResult.get("Payeremail"))
                .interest(paymentResult.get("Interest"))
                .cardNum(paymentResult.get("Cardnum"))
                .cardCode(paymentResult.get("Cardcode"))
                .cardName(paymentResult.get("Cardname"))
                .apprNo(paymentResult.get("Apprno"))
                .ownDivCd(paymentResult.get("Owndivcd"))
                .build();
    }
}
