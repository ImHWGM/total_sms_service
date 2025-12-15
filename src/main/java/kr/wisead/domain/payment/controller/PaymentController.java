package kr.wisead.domain.payment.controller;

import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.payment.dto.BalanceResponse;
import kr.wisead.domain.payment.dto.ChargeRequest;
import kr.wisead.domain.payment.entity.Payment;
import kr.wisead.domain.payment.service.BalanceService;
import kr.wisead.domain.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 결제/잔액 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final BalanceService balanceService;

    /**
     * 현재 잔액 조회
     * GET /api/payment/balance
     */
    @GetMapping("/balance")
    public ApiResponse<BalanceResponse> getCurrentBalance(
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = userDetails.getUsername();
        BalanceResponse response = balanceService.getCurrentBalance(userId);
        return ApiResponse.success(response);
    }

    /**
     * 잔액 내역 조회
     * GET /api/payment/balance/history?page=1&size=10
     */
    @GetMapping("/balance/history")
    public ApiResponse<PageResponse<BalanceResponse>> getBalanceHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        String userId = userDetails.getUsername();
        PageResponse<BalanceResponse> response = balanceService.getBalanceHistory(userId, page, size);
        return ApiResponse.success(response);
    }

    /**
     * 충전 (관리자용)
     * POST /api/payment/charge
     */
    @PostMapping("/charge")
    public ApiResponse<BalanceResponse> charge(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ChargeRequest request) {
        String operatorId = userDetails.getUsername();
        BalanceResponse response = balanceService.charge(request, operatorId);
        return ApiResponse.success(response);
    }

    /**
     * 차감 (관리자용)
     * POST /api/payment/deduct
     */
    @PostMapping("/deduct")
    public ApiResponse<BalanceResponse> deduct(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String userId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String comment) {
        String operatorId = userDetails.getUsername();
        BalanceResponse response = balanceService.deduct(userId, amount,
                comment != null ? comment : "관리자 차감", operatorId);
        return ApiResponse.success(response);
    }

    /**
     * 잔액 충분 여부 확인
     * GET /api/payment/balance/check?amount=10000
     */
    @GetMapping("/balance/check")
    public ApiResponse<Boolean> checkBalance(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam BigDecimal amount) {
        String userId = userDetails.getUsername();
        boolean hasEnough = balanceService.hasEnoughBalance(userId, amount);
        return ApiResponse.success(hasEnough);
    }

    /**
     * 결제 결과 콜백 (PG 연동)
     * POST /api/payment/callback
     */
    @PostMapping("/callback")
    public ApiResponse<Boolean> paymentCallback(@RequestParam Map<String, String> paymentResult) {
        log.info("결제 콜백 수신: {}", paymentResult);

        if (!paymentService.validatePayment(paymentResult)) {
            return ApiResponse.error("PAYMENT_FAILED", "결제 검증에 실패했습니다.");
        }

        boolean result = paymentService.processPayment(paymentResult);
        if (result) {
            return ApiResponse.success(true);
        } else {
            return ApiResponse.error("PAYMENT_FAILED", "결제 처리에 실패했습니다.");
        }
    }

    /**
     * 결제 내역 조회
     * GET /api/payment/history?page=1&size=10
     */
    @GetMapping("/history")
    public ApiResponse<PageResponse<Payment>> getPaymentHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        String userId = userDetails.getUsername();
        PageResponse<Payment> response = paymentService.getPaymentHistory(userId, page, size);
        return ApiResponse.success(response);
    }

    /**
     * 거래 ID로 결제 정보 조회
     * GET /api/payment/{tradeId}
     */
    @GetMapping("/{tradeId}")
    public ApiResponse<Payment> getPaymentByTradeId(@PathVariable String tradeId) {
        Payment payment = paymentService.getPaymentByTradeId(tradeId);
        return ApiResponse.success(payment);
    }
}
