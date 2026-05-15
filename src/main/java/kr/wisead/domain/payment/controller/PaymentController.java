package kr.wisead.domain.payment.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.payment.dto.BalanceResponse;
import kr.wisead.domain.payment.dto.ChargeRequest;
import kr.wisead.domain.payment.dto.RefundPreviewResponse;
import kr.wisead.domain.payment.dto.RefundResult;
import kr.wisead.domain.payment.dto.SmsPriceRequest;
import kr.wisead.domain.payment.dto.StandardRateResponse;
import kr.wisead.domain.payment.dto.TransactionResponse;
import kr.wisead.domain.payment.dto.UserServiceRateRequest;
import kr.wisead.domain.payment.dto.UserServiceRateResponse;
import kr.wisead.domain.payment.dto.WalletLotResponse;
import kr.wisead.domain.payment.dto.WalletSummaryResponse;
import kr.wisead.domain.payment.entity.Payment;
import kr.wisead.domain.payment.service.BalanceService;
import kr.wisead.domain.payment.service.BillingService;
import kr.wisead.domain.payment.service.PaymentService;
import kr.wisead.domain.payment.service.UserServiceRateService;
import kr.wisead.security.jwt.CurrentUser;
import kr.wisead.security.jwt.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
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
  private final AdminService adminService;

  @Value("${wisead.url}")
  private String wiseadUrl;

  /** 현재 잔액 조회 GET /api/payment/balance */
  @GetMapping("/balance")
  public ApiResponse<BalanceResponse> getCurrentBalance(@CurrentUser JwtPrincipal user) {
    return ApiResponse.success(balanceService.getCurrentBalance(user.seq()));
  }

  /** 잔액 내역 조회 GET /api/payment/balance/history?page=1&size=10 */
  @GetMapping("/balance/history")
  public ApiResponse<PageResponse<BalanceResponse>> getBalanceHistory(
      @CurrentUser JwtPrincipal user,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(balanceService.getBalanceHistory(user.seq(), page, size));
  }

  /** 충전 (관리자용) POST /api/payment/charge */
  @PostMapping("/charge")
  public ApiResponse<BalanceResponse> charge(
      @CurrentUser JwtPrincipal user, @RequestBody ChargeRequest request) {
    return ApiResponse.success(balanceService.charge(request, user.userId()));
  }

  /** 차감 (관리자용) POST /api/payment/deduct */
  @PostMapping("/deduct")
  public ApiResponse<BalanceResponse> deduct(
      @CurrentUser JwtPrincipal user,
      @RequestParam String userId,
      @RequestParam BigDecimal amount,
      @RequestParam(required = false) String comment) {
    return ApiResponse.success(
        balanceService.deduct(
            userId, amount, comment != null ? comment : "관리자 차감", user.userId()));
  }

  /** 잔액 충분 여부 확인 GET /api/payment/balance/check?amount=10000 */
  @GetMapping("/balance/check")
  public ApiResponse<Boolean> checkBalance(
      @CurrentUser JwtPrincipal user, @RequestParam BigDecimal amount) {
    return ApiResponse.success(balanceService.hasEnoughBalance(user.seq(), amount));
  }

  /** QR 코드 신청 비용 잔액 충분 여부 확인 GET /api/payment/balance/check-qr */
  @GetMapping("/balance/check-qr")
  public ApiResponse<Boolean> checkQrBalance(@CurrentUser JwtPrincipal user) {
    return ApiResponse.success(billingService.canChargeQr(user.seq()));
  }

  /** 문자 요금 설정 (관리자용) PUT /api/payment/sms-price */
  @PutMapping("/sms-price")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<BalanceResponse> updateSmsPrice(
      @CurrentUser JwtPrincipal user, @RequestBody SmsPriceRequest request) {
    return ApiResponse.success(balanceService.updateSmsPrice(request, user.userId()));
  }

  /** 특정 사용자 잔액 내역 조회 (본인/운영관리자/최고관리자) GET /api/payment/balance/history/{userId} */
  @GetMapping("/balance/history/{userId}")
  public ApiResponse<PageResponse<BalanceResponse>> getBalanceHistoryByUserId(
      @CurrentUser JwtPrincipal user,
      @PathVariable String userId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    String currentUserId = user.userId();
    Integer userLevel = adminService.getUserLevel(currentUserId);

    if (!currentUserId.equals(userId)) {
      String queryScope = adminService.determineQueryUserIds(currentUserId, userLevel);
      boolean hasAccess =
          "ALL".equals(queryScope) || Arrays.asList(queryScope.split(",")).contains(userId);
      if (!hasAccess) {
        throw new AccessDeniedException("접근 권한이 없습니다.");
      }
    }

    return ApiResponse.success(balanceService.getBalanceHistory(userId, page, size));
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

  /** 결제 내역 조회 GET /api/payment/history?page=1&size=10 (미사용 - KG_PAYMENT 테이블 없음) */
  @GetMapping("/history")
  public ApiResponse<PageResponse<Payment>> getPaymentHistory(
      @CurrentUser JwtPrincipal user,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(paymentService.getPaymentHistory(user.userId(), page, size));
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
   * KG 모빌리언스 결제 완료 리다이렉트 POST /api/payment/kg/ok
   *
   * <p>KG PG가 결제 완료 후 POST로 결과를 전송하면, 파라미터를 query string으로 변환하여 프론트엔드 SPA로 GET
   * 리다이렉트합니다.
   */
  @PostMapping(value = "/kg/ok", produces = "text/html;charset=EUC-KR")
  public void kgPaymentOk(
      @RequestParam Map<String, String> params, HttpServletResponse response) throws IOException {
    log.info("KG 결제 완료 리다이렉트 수신: {}", params);

    String queryString =
        params.entrySet().stream()
            .map(
                e ->
                    URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));

    String redirectUrl = wiseadUrl + "/payment/result?" + queryString;
    response.sendRedirect(redirectUrl);
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
  public ApiResponse<WalletSummaryResponse> getWalletSummary(@CurrentUser JwtPrincipal user) {
    return ApiResponse.success(balanceService.getWalletSummary(user.seq()));
  }

  /** 활성 Lot 목록 조회 (만료일 포함) GET /api/payment/wallet/lots */
  @GetMapping("/wallet/lots")
  public ApiResponse<List<WalletLotResponse>> getActiveLots(@CurrentUser JwtPrincipal user) {
    return ApiResponse.success(balanceService.getActiveLots(user.seq()));
  }

  /** 거래 이력 조회 (신규) GET /api/payment/transactions */
  @GetMapping("/transactions")
  public ApiResponse<PageResponse<TransactionResponse>> getTransactions(
      @CurrentUser JwtPrincipal user,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(balanceService.getTransactionHistory(user.seq(), page, size));
  }

  /** 환불 미리보기 GET /api/payment/refund/preview?txGroupId={id} */
  @GetMapping("/refund/preview")
  public ApiResponse<RefundPreviewResponse> previewRefund(@RequestParam String txGroupId) {
    return ApiResponse.success(balanceService.previewRefund(txGroupId));
  }

  /** 환불 처리 POST /api/payment/refund/{txGroupId} */
  @PostMapping("/refund/{txGroupId}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<RefundResult> processRefund(
      @CurrentUser JwtPrincipal user, @PathVariable String txGroupId) {
    return ApiResponse.success(balanceService.refund(txGroupId, user.userId()));
  }

  // ==================== 사용자별 서비스 요금 API ====================

  /** 표준 요금 목록 조회 GET /api/payment/standard-rates */
  @GetMapping("/standard-rates")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<StandardRateResponse> getStandardRates() {
    return ApiResponse.success(userServiceRateService.getStandardRates());
  }

  /** 사용자별 요금 조회 GET /api/payment/user-rates/{userSeq} */
  @GetMapping("/user-rates/{userSeq}")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<UserServiceRateResponse> getUserRates(@PathVariable Integer userSeq) {
    return ApiResponse.success(userServiceRateService.getUserRates(userSeq));
  }

  /** 사용자별 요금 설정 PUT /api/payment/user-rates */
  @PutMapping("/user-rates")
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<Void> updateUserRates(@Valid @RequestBody UserServiceRateRequest request) {
    userServiceRateService.updateUserRates(request.userSeq(), request);
    return ApiResponse.success(null);
  }
}
