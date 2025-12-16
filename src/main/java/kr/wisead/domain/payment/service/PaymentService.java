package kr.wisead.domain.payment.service;

import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.payment.dto.ChargeRequest;
import kr.wisead.domain.payment.entity.Payment;
import kr.wisead.mapper.primary.PaymentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mup.mcash.module.common.McashCipher.McashCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 결제 서비스 (KG모빌리언스 연동)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final BalanceService balanceService;

    /**
     * 결제 결과 처리
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

            // 잔액 충전 처리
            String userId = paymentResult.get("Userid");
            String amountStr = paymentResult.get("Prdtprice");
            BigDecimal amount = new BigDecimal(amountStr);

            ChargeRequest chargeRequest = ChargeRequest.builder()
                    .userId(userId)
                    .amount(amount)
                    .comment("결제 충전 (tradeId: " + tradeId + ")")
                    .tradeId(tradeId)
                    .build();

            balanceService.charge(chargeRequest, userId);
            log.info("결제 충전 처리 완료: userId={}, amount={}", userId, amount);

            return true;
        } catch (Exception e) {
            log.error("결제 처리 중 오류 발생", e);
            return false;
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
