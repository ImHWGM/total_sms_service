package kr.wisead.domain.payment.controller;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.Map;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.payment.dto.BalanceResponse;
import kr.wisead.domain.payment.dto.ChargeRequest;
import kr.wisead.domain.payment.dto.SmsPriceRequest;
import kr.wisead.domain.payment.dto.StandardRateResponse;
import kr.wisead.domain.payment.dto.UserServiceRateRequest;
import kr.wisead.domain.payment.dto.UserServiceRateResponse;
import kr.wisead.domain.payment.entity.Payment;
import kr.wisead.domain.payment.service.BalanceService;
import kr.wisead.domain.payment.service.BillingService;
import kr.wisead.domain.payment.service.PaymentService;
import kr.wisead.domain.payment.service.UserServiceRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/** 결제/잔액 Controller */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;
  private final BalanceService balanceService;
  private final BillingService billingService;
  private final UserServiceRateService userServiceRateService;

  /** 현재 잔액 조회 GET /api/payment/balance */
  @GetMapping("/balance")
  public ApiResponse<BalanceResponse> getCurrentBalance(
      @AuthenticationPrincipal UserDetails userDetails) {
    String userId = userDetails.getUsername();
    BalanceResponse response = balanceService.getCurrentBalance(userId);
    return ApiResponse.success(response);
  }

  /** 잔액 내역 조회 GET /api/payment/balance/history?page=1&size=10 */
  @GetMapping("/balance/history")
  public ApiResponse<PageResponse<BalanceResponse>> getBalanceHistory(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    String userId = userDetails.getUsername();
    PageResponse<BalanceResponse> response = balanceService.getBalanceHistory(userId, page, size);
    return ApiResponse.success(response);
  }

  /** 충전 (관리자용) POST /api/payment/charge */
  @PostMapping("/charge")
  public ApiResponse<BalanceResponse> charge(
      @AuthenticationPrincipal UserDetails userDetails, @RequestBody ChargeRequest request) {
    String operatorId = userDetails.getUsername();
    BalanceResponse response = balanceService.charge(request, operatorId);
    return ApiResponse.success(response);
  }

  /** 차감 (관리자용) POST /api/payment/deduct */
  @PostMapping("/deduct")
  public ApiResponse<BalanceResponse> deduct(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam String userId,
      @RequestParam BigDecimal amount,
      @RequestParam(required = false) String comment) {
    String operatorId = userDetails.getUsername();
    BalanceResponse response =
        balanceService.deduct(userId, amount, comment != null ? comment : "관리자 차감", operatorId);
    return ApiResponse.success(response);
  }

  /** 잔액 충분 여부 확인 GET /api/payment/balance/check?amount=10000 */
  @GetMapping("/balance/check")
  public ApiResponse<Boolean> checkBalance(
      @AuthenticationPrincipal UserDetails userDetails, @RequestParam BigDecimal amount) {
    String userId = userDetails.getUsername();
    boolean hasEnough = balanceService.hasEnoughBalance(userId, amount);
    return ApiResponse.success(hasEnough);
  }

  /** QR 코드 신청 비용 잔액 충분 여부 확인 GET /api/payment/balance/check-qr */
  @GetMapping("/balance/check-qr")
  public ApiResponse<Boolean> checkQrBalance(@AuthenticationPrincipal UserDetails userDetails) {
    String userId = userDetails.getUsername();
    boolean canCharge = billingService.canChargeQr(userId);
    return ApiResponse.success(canCharge);
  }

  /** 문자 요금 설정 (관리자용) PUT /api/payment/sms-price */
  @PutMapping("/sms-price")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<BalanceResponse> updateSmsPrice(
      @AuthenticationPrincipal UserDetails userDetails, @RequestBody SmsPriceRequest request) {
    String operatorId = userDetails.getUsername();
    BalanceResponse response = balanceService.updateSmsPrice(request, operatorId);
    return ApiResponse.success(response);
  }

  /** 특정 사용자 잔액 내역 조회 (관리자용) GET /api/payment/balance/history/{userId} */
  @GetMapping("/balance/history/{userId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<PageResponse<BalanceResponse>> getBalanceHistoryByUserId(
      @PathVariable String userId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    PageResponse<BalanceResponse> response = balanceService.getBalanceHistory(userId, page, size);
    return ApiResponse.success(response);
  }

  /** 결제 결과 콜백 (PG 연동) POST /api/payment/callback */
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

  /** 결제 내역 조회 GET /api/payment/history?page=1&size=10 */
  @GetMapping("/history")
  public ApiResponse<PageResponse<Payment>> getPaymentHistory(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    String userId = userDetails.getUsername();
    PageResponse<Payment> response = paymentService.getPaymentHistory(userId, page, size);
    return ApiResponse.success(response);
  }

  /** 거래 ID로 결제 정보 조회 GET /api/payment/{tradeId} */
  @GetMapping("/{tradeId}")
  public ApiResponse<Payment> getPaymentByTradeId(@PathVariable String tradeId) {
    Payment payment = paymentService.getPaymentByTradeId(tradeId);
    return ApiResponse.success(payment);
  }

  // ==================== KG 모빌리언스 전용 엔드포인트 ====================

  /**
   * KG 모빌리언스 결제 알림 콜백 POST /api/payment/kg/noti
   *
   * <p>레거시 호환용: KG 모빌리언스 PG에서 결제 완료 시 호출 반환값: "SUCCESS" 또는 "FAIL" (PG 규격)
   */
  @PostMapping(value = "/kg/noti", produces = "text/plain;charset=EUC-KR")
  @ResponseBody
  public String kgPaymentNoti(@RequestParam Map<String, String> paymentResult) {
    log.info("KG 결제 콜백 수신: {}", paymentResult);

    try {
      // 결제 검증
      if (!paymentService.validatePayment(paymentResult)) {
        log.error("KG 결제 검증 실패");
        return "FAIL";
      }

      // 결제 처리
      boolean isProcessed = paymentService.processPayment(paymentResult);
      if (isProcessed) {
        log.info("KG 결제 처리 성공");
        return "SUCCESS";
      } else {
        log.error("KG 결제 처리 실패");
        return "FAIL";
      }
    } catch (Exception e) {
      log.error("KG 결제 처리 중 오류: {}", e.getMessage(), e);
      return "FAIL";
    }
  }

  /**
   * KG 모빌리언스 결제 완료 결과 조회 POST /api/payment/kg/result
   *
   * <p>결제 완료 후 클라이언트에서 결과 조회용
   */
  @PostMapping("/kg/result")
  public ApiResponse<Map<String, Object>> kgPaymentResult(
      @RequestParam Map<String, String> paymentResult) {
    log.info("KG 결제 결과 조회: {}", paymentResult);

    Map<String, Object> result = paymentService.collectPaymentResult(paymentResult);
    return ApiResponse.success(result);
  }

  // ==================== 신규 결제 시스템 API ====================

  /** 지갑 요약 조회 (CASH/POINT/BONUS 분리) GET /api/payment/wallet/summary */
  @GetMapping("/wallet/summary")
  public ApiResponse<kr.wisead.domain.payment.dto.WalletSummaryResponse> getWalletSummary(
      @AuthenticationPrincipal UserDetails userDetails) {
    String userId = userDetails.getUsername();
    return ApiResponse.success(balanceService.getWalletSummary(userId));
  }

  /** 활성 Lot 목록 조회 (만료일 포함) GET /api/payment/wallet/lots */
  @GetMapping("/wallet/lots")
  public ApiResponse<java.util.List<kr.wisead.domain.payment.dto.WalletLotResponse>> getActiveLots(
      @AuthenticationPrincipal UserDetails userDetails) {
    String userId = userDetails.getUsername();
    return ApiResponse.success(balanceService.getActiveLots(userId));
  }

  /** 거래 이력 조회 (신규) GET /api/payment/transactions */
  @GetMapping("/transactions")
  public ApiResponse<PageResponse<kr.wisead.domain.payment.dto.TransactionResponse>>
      getTransactions(
          @AuthenticationPrincipal UserDetails userDetails,
          @RequestParam(defaultValue = "1") int page,
          @RequestParam(defaultValue = "10") int size) {
    String userId = userDetails.getUsername();
    return ApiResponse.success(balanceService.getTransactionHistory(userId, page, size));
  }

  /** 환불 미리보기 GET /api/payment/refund/preview?txGroupId={id} */
  @GetMapping("/refund/preview")
  public ApiResponse<kr.wisead.domain.payment.dto.RefundPreviewResponse> previewRefund(
      @RequestParam String txGroupId) {
    return ApiResponse.success(balanceService.previewRefund(txGroupId));
  }

  /** 환불 처리 POST /api/payment/refund/{txGroupId} */
  @PostMapping("/refund/{txGroupId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<kr.wisead.domain.payment.dto.RefundResult> processRefund(
      @PathVariable String txGroupId) {
    return ApiResponse.success(balanceService.refund(txGroupId));
  }

  // ==================== 사용자별 서비스 요금 API ====================

  /** 표준 요금 목록 조회 GET /api/payment/standard-rates */
  @GetMapping("/standard-rates")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<StandardRateResponse> getStandardRates() {
    return ApiResponse.success(userServiceRateService.getStandardRates());
  }

  /** 사용자별 요금 조회 GET /api/payment/user-rates/{userId} */
  @GetMapping("/user-rates/{userId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<UserServiceRateResponse> getUserRates(@PathVariable String userId) {
    return ApiResponse.success(userServiceRateService.getUserRates(userId));
  }

  /** 사용자별 요금 설정 PUT /api/payment/user-rates */
  @PutMapping("/user-rates")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<Void> updateUserRates(@Valid @RequestBody UserServiceRateRequest request) {
    userServiceRateService.updateUserRates(request);
    return ApiResponse.success(null);
  }
}
