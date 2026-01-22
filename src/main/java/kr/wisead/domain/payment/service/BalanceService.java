package kr.wisead.domain.payment.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.payment.dto.*;
import kr.wisead.domain.payment.entity.Transaction;
import kr.wisead.domain.payment.entity.UserServiceRate;
import kr.wisead.mapper.primary.TransactionMapper;
import kr.wisead.mapper.primary.UserServiceRateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 잔액 관리 서비스 (리팩토링 버전) - 내부적으로 WalletService 사용 - 기존 API 호환성 유지 - 내부적으로 userSeq(Integer) 기반으로 동작 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

  private final WalletService walletService;
  private final TransactionMapper transactionMapper;
  private final UserServiceRateService userServiceRateService;
  private final UserServiceRateMapper userServiceRateMapper; // 레거시 updateSmsPrice용
  private final UserIdResolver userIdResolver;

  /** 현재 잔액 조회 - N+1 최적화: 4개 단가 조회를 한 번에 처리 (최대 8회 → 최대 2회) */
  public BalanceResponse getCurrentBalance(Integer userSeq) {
    WalletSummaryResponse summary = walletService.getWalletSummary(userSeq);

    // N+1 최적화: 모든 단가를 한 번에 조회
    Map<String, BigDecimal> rates = getAllRates(userSeq);

    return BalanceResponse.builder()
        .userSeq(userSeq)
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

  /** 확장된 잔액 조회 (CASH + POINT + BONUS 분리) */
  public WalletSummaryResponse getWalletSummary(Integer userSeq) {
    return walletService.getWalletSummary(userSeq);
  }

  /** 활성 Lot 목록 조회 */
  public List<WalletLotResponse> getActiveLots(Integer userSeq) {
    return walletService.getActiveLots(userSeq);
  }

  /** 잔액 내역 조회 (페이징) - 새 트랜잭션 테이블 사용 */
  public PageResponse<TransactionResponse> getTransactionHistory(
      Integer userSeq, int page, int size) {
    int offset = (page - 1) * size;
    List<Transaction> list = transactionMapper.selectHistory(userSeq, offset, size);
    int total = transactionMapper.selectHistoryCount(userSeq);

    List<TransactionResponse> responses = list.stream().map(TransactionResponse::from).toList();

    return PageResponse.of(responses, page, size, total);
  }

  /** 잔액 내역 조회 (페이징) - 레거시 호환용 */
  public PageResponse<BalanceResponse> getBalanceHistory(Integer userSeq, int page, int size) {
    int offset = (page - 1) * size;
    List<Transaction> list = transactionMapper.selectHistory(userSeq, offset, size);
    int total = transactionMapper.selectHistoryCount(userSeq);

    List<BalanceResponse> responses = list.stream().map(this::convertToBalanceResponse).toList();

    return PageResponse.of(responses, page, size, total);
  }

  /** 충전 (CASH, BONUS, POINT 지원) */
  @Transactional
  public BalanceResponse charge(ChargeRequest request, String operatorId) {
    String comment = request.getComment() != null ? request.getComment() : "충전";
    String currencyType = request.getCurrencyType(); // 기본값: CASH
    Integer userSeq = request.getUserSeq();
    BigDecimal amount = request.getAmount();

    BigDecimal balanceAfter;
    Long seq = null;
    java.time.LocalDateTime regDate = null;

    switch (currencyType) {
      case "BONUS" -> {
        // BONUS 적립 (wallet_lot 테이블)
        var lotResponse =
            walletService.grantBonus(userSeq, amount, request.getExpireDate(), comment);
        seq = lotResponse.getLotSeq();
        regDate = lotResponse.getRegDate();
        // 전체 잔액 조회
        balanceAfter = walletService.getWalletSummary(userSeq).getTotal();
        log.info(
            "BONUS 충전 완료: userSeq={}, amount={}, expireDate={}, balanceAfter={}",
            userSeq,
            amount,
            request.getExpireDate(),
            balanceAfter);
      }
      case "POINT" -> {
        // POINT 적립 (wallet_lot 테이블)
        var lotResponse =
            walletService.grantPoint(userSeq, amount, request.getExpireDate(), comment);
        seq = lotResponse.getLotSeq();
        regDate = lotResponse.getRegDate();
        // 전체 잔액 조회
        balanceAfter = walletService.getWalletSummary(userSeq).getTotal();
        log.info(
            "POINT 충전 완료: userSeq={}, amount={}, expireDate={}, balanceAfter={}",
            userSeq,
            amount,
            request.getExpireDate(),
            balanceAfter);
      }
      default -> {
        // CASH 충전 (wallet 테이블) - 기존 로직
        TransactionResponse txResponse = walletService.charge(userSeq, amount, comment);
        seq = (long) txResponse.getSeq();
        regDate = txResponse.getRegDate();
        balanceAfter = txResponse.getBalanceAfter();
        log.info(
            "CASH 충전 완료: userSeq={}, amount={}, balanceAfter={}", userSeq, amount, balanceAfter);
      }
    }

    // N+1 최적화: 모든 단가를 한 번에 조회
    Map<String, BigDecimal> rates = getAllRates(userSeq);

    return BalanceResponse.builder()
        .seq(seq)
        .userSeq(userSeq)
        .balance(amount)
        .totalBalance(balanceAfter)
        .operation("P")
        .operationName(currencyType + " 충전")
        .comment(comment)
        .regDate(regDate)
        .smsPrice(rates.getOrDefault("msg_sms", BigDecimal.valueOf(12.1)))
        .lmsPrice(rates.getOrDefault("msg_lms", BigDecimal.valueOf(36.3)))
        .mmsPrice(rates.getOrDefault("msg_mms", BigDecimal.valueOf(121)))
        .subtractUnitPrice(rates.getOrDefault("survey", BigDecimal.valueOf(36.3)))
        .build();
  }

  /** 차감 (금액 직접 지정) */
  @Transactional
  public BalanceResponse deduct(
      Integer userSeq, BigDecimal amount, String comment, String operatorId) {
    // 잔액 확인
    if (!walletService.hasEnoughBalance(userSeq, amount)) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "잔액이 부족합니다.");
    }

    // 우선순위 차감 (BONUS → POINT → CASH) - 금액 직접 차감
    String txGroupId = walletService.deductByAmount(userSeq, amount, comment);

    WalletSummaryResponse summary = walletService.getWalletSummary(userSeq);

    log.info("차감 완료: userSeq={}, amount={}, balanceAfter={}", userSeq, amount, summary.getTotal());

    // N+1 최적화: 모든 단가를 한 번에 조회
    Map<String, BigDecimal> rates = getAllRates(userSeq);

    return BalanceResponse.builder()
        .userSeq(userSeq)
        .balance(amount)
        .totalBalance(summary.getTotal())
        .operation("M")
        .operationName("차감")
        .comment(comment)
        .smsPrice(rates.getOrDefault("msg_sms", BigDecimal.valueOf(12.1)))
        .lmsPrice(rates.getOrDefault("msg_lms", BigDecimal.valueOf(36.3)))
        .mmsPrice(rates.getOrDefault("msg_mms", BigDecimal.valueOf(121)))
        .subtractUnitPrice(rates.getOrDefault("survey", BigDecimal.valueOf(36.3)))
        .build();
  }

  /** 차감 (금액 직접 지정, txGroupId 지정 버전) - 외부에서 생성한 txGroupId를 사용 (MsgQueue에 저장 후 차감 시 동일 ID 사용) */
  @Transactional
  public BalanceResponse deductWithTxGroupId(
      Integer userSeq, BigDecimal amount, String comment, String operatorId, String txGroupId) {
    // 잔액 확인
    if (!walletService.hasEnoughBalance(userSeq, amount)) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "잔액이 부족합니다.");
    }

    // 우선순위 차감 (BONUS → POINT → CASH) - 지정된 txGroupId 사용
    walletService.deductByAmount(userSeq, amount, comment, txGroupId);

    WalletSummaryResponse summary = walletService.getWalletSummary(userSeq);

    log.info(
        "차감 완료: userSeq={}, amount={}, balanceAfter={}, txGroupId={}",
        userSeq,
        amount,
        summary.getTotal(),
        txGroupId);

    // N+1 최적화: 모든 단가를 한 번에 조회
    Map<String, BigDecimal> rates = getAllRates(userSeq);

    return BalanceResponse.builder()
        .userSeq(userSeq)
        .balance(amount)
        .totalBalance(summary.getTotal())
        .operation("M")
        .operationName("차감")
        .comment(comment)
        .smsPrice(rates.getOrDefault("msg_sms", BigDecimal.valueOf(12.1)))
        .lmsPrice(rates.getOrDefault("msg_lms", BigDecimal.valueOf(36.3)))
        .mmsPrice(rates.getOrDefault("msg_mms", BigDecimal.valueOf(121)))
        .subtractUnitPrice(rates.getOrDefault("survey", BigDecimal.valueOf(36.3)))
        .build();
  }

  /**
   * 메시지 발송 비용 차감
   *
   * @return txGroupId 거래 그룹 ID
   */
  @Transactional
  public String deductMessageCharge(Integer userSeq, int count, String msgType, String comment) {
    String serviceId = getServiceIdByMsgType(msgType);
    BigDecimal quantity = BigDecimal.valueOf(count);

    // 단가 조회
    BigDecimal unitPrice = walletService.getAppliedRate(userSeq, serviceId);
    BigDecimal totalCharge = unitPrice.multiply(quantity);

    // 잔액 확인
    if (!walletService.hasEnoughBalance(userSeq, totalCharge)) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "잔액이 부족합니다.");
    }

    // 우선순위 차감
    String txGroupId = walletService.deductWithPriority(userSeq, serviceId, quantity, comment);

    log.info(
        "메시지 비용 차감: userSeq={}, count={}, type={}, charge={}, txGroupId={}",
        userSeq,
        count,
        msgType,
        totalCharge,
        txGroupId);

    return txGroupId;
  }

  /** 메시지 발송 비용 차감 (txGroupId 지정 버전) - 외부에서 생성한 txGroupId를 사용 (MsgQueue에 저장 후 차감 시 동일 ID 사용) */
  @Transactional
  public void deductMessageChargeWithTxGroupId(
      Integer userSeq, int count, String msgType, String comment, String txGroupId) {
    String serviceId = getServiceIdByMsgType(msgType);
    BigDecimal quantity = BigDecimal.valueOf(count);

    // 단가 조회
    BigDecimal unitPrice = walletService.getAppliedRate(userSeq, serviceId);
    BigDecimal totalCharge = unitPrice.multiply(quantity);

    // 잔액 확인
    if (!walletService.hasEnoughBalance(userSeq, totalCharge)) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "잔액이 부족합니다.");
    }

    // 우선순위 차감 (지정된 txGroupId 사용)
    walletService.deductWithPriority(userSeq, serviceId, quantity, comment, txGroupId);

    log.info(
        "메시지 비용 차감: userSeq={}, count={}, type={}, charge={}, txGroupId={}",
        userSeq,
        count,
        msgType,
        totalCharge,
        txGroupId);
  }

  /** 잔액 충분 여부 확인 */
  public boolean hasEnoughBalance(Integer userSeq, BigDecimal requiredAmount) {
    return walletService.hasEnoughBalance(userSeq, requiredAmount);
  }

  /** 문자 요금 설정 (사용자별 단가) */
  @Transactional
  public BalanceResponse updateSmsPrice(SmsPriceRequest request, String operatorId) {
    if (request.getUserSeq() == null) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 시퀀스가 필요합니다.");
    }

    Integer userSeq = request.getUserSeq();
    LocalDate today = LocalDate.now();

    // SMS 단가 설정
    if (request.getSmsPrice() != null) {
      updateUserServiceRate(userSeq, "msg_sms", request.getSmsPrice(), today);
    }

    // LMS 단가 설정
    if (request.getLmsPrice() != null) {
      updateUserServiceRate(userSeq, "msg_lms", request.getLmsPrice(), today);
    }

    // MMS 단가 설정
    if (request.getMmsPrice() != null) {
      updateUserServiceRate(userSeq, "msg_mms", request.getMmsPrice(), today);
    }

    log.info(
        "문자 요금 변경: userSeq={}, SMS={}, LMS={}, MMS={}",
        userSeq,
        request.getSmsPrice(),
        request.getLmsPrice(),
        request.getMmsPrice());

    WalletSummaryResponse summary = walletService.getWalletSummary(userSeq);

    // N+1 최적화: 모든 단가를 한 번에 조회
    Map<String, BigDecimal> rates = getAllRates(userSeq);

    return BalanceResponse.builder()
        .userSeq(userSeq)
        .totalBalance(summary.getTotal())
        .balance(BigDecimal.ZERO)
        .operation("U")
        .operationName("단가변경")
        .comment("문자 요금 설정 변경")
        .smsPrice(rates.getOrDefault("msg_sms", BigDecimal.valueOf(12.1)))
        .lmsPrice(rates.getOrDefault("msg_lms", BigDecimal.valueOf(36.3)))
        .mmsPrice(rates.getOrDefault("msg_mms", BigDecimal.valueOf(121)))
        .subtractUnitPrice(rates.getOrDefault("survey", BigDecimal.valueOf(36.3)))
        .build();
  }

  /** 환불 미리보기 */
  @Transactional(readOnly = true)
  public RefundPreviewResponse previewRefund(String txGroupId) {
    return walletService.previewRefund(txGroupId);
  }

  /** 환불 처리 */
  @Transactional
  public RefundResult refund(String txGroupId) {
    return walletService.refundByGroup(txGroupId);
  }

  // ========== userId 기반 오버로드 메서드 (API 호환용) ==========

  /** 현재 잔액 조회 (userId 기반) */
  public BalanceResponse getCurrentBalance(String userId) {
    return getCurrentBalance(userIdResolver.toUserSeq(userId));
  }

  /** 확장된 잔액 조회 (userId 기반) */
  public WalletSummaryResponse getWalletSummary(String userId) {
    return getWalletSummary(userIdResolver.toUserSeq(userId));
  }

  /** 활성 Lot 목록 조회 (userId 기반) */
  public List<WalletLotResponse> getActiveLots(String userId) {
    return getActiveLots(userIdResolver.toUserSeq(userId));
  }

  /** 잔액 내역 조회 (userId 기반) */
  public PageResponse<TransactionResponse> getTransactionHistory(
      String userId, int page, int size) {
    return getTransactionHistory(userIdResolver.toUserSeq(userId), page, size);
  }

  /** 잔액 내역 조회 - 레거시 (userId 기반) */
  public PageResponse<BalanceResponse> getBalanceHistory(String userId, int page, int size) {
    return getBalanceHistory(userIdResolver.toUserSeq(userId), page, size);
  }

  /** 차감 (userId 기반) */
  @Transactional
  public BalanceResponse deduct(
      String userId, BigDecimal amount, String comment, String operatorId) {
    return deduct(userIdResolver.toUserSeq(userId), amount, comment, operatorId);
  }

  /** 잔액 충분 여부 확인 (userId 기반) */
  public boolean hasEnoughBalance(String userId, BigDecimal requiredAmount) {
    return hasEnoughBalance(userIdResolver.toUserSeq(userId), requiredAmount);
  }

  /** 차감 + txGroupId 지정 (userId 기반) */
  @Transactional
  public BalanceResponse deductWithTxGroupId(
      String userId, BigDecimal amount, String comment, String operatorId, String txGroupId) {
    return deductWithTxGroupId(
        userIdResolver.toUserSeq(userId), amount, comment, operatorId, txGroupId);
  }

  // ========== Private Helper Methods ==========

  /** 모든 서비스 단가를 한 번에 조회 우선순위: 기간 특별요금 → 사용자 기본요금 → 표준요금 */
  private Map<String, BigDecimal> getAllRates(Integer userSeq) {
    UserServiceRateResponse response = userServiceRateService.getUserRates(userSeq);
    return response.rates().stream()
        .collect(
            Collectors.toMap(
                UserServiceRateResponse.ServiceRateInfo::serviceId,
                UserServiceRateResponse.ServiceRateInfo::userRate));
  }

  private String getServiceIdByMsgType(String msgType) {
    return switch (msgType.toUpperCase()) {
      case "SMS" -> "msg_sms";
      case "LMS" -> "msg_lms";
      case "MMS" -> "msg_mms";
      default -> "survey";
    };
  }

  private void updateUserServiceRate(
      Integer userSeq, String serviceId, BigDecimal rate, LocalDate startDate) {
    // 기존 유효한 단가 종료 처리
    userServiceRateMapper
        .selectActiveRate(userSeq, serviceId, startDate)
        .ifPresent(
            existing -> {
              userServiceRateMapper.updateEndDate(existing.getSeq(), startDate.minusDays(1));
            });

    // 새 단가 등록
    UserServiceRate newRate = UserServiceRate.create(userSeq, serviceId, rate, startDate);
    userServiceRateMapper.insert(newRate);
  }

  private BalanceResponse convertToBalanceResponse(Transaction tx) {
    String opName =
        switch (tx.getTxType()) {
          case "CHARGE" -> "충전";
          case "DEDUCT" -> "차감";
          case "REFUND" -> "환불";
          default -> tx.getTxType();
        };

    String operation =
        switch (tx.getTxType()) {
          case "CHARGE" -> "P";
          case "DEDUCT" -> "M";
          case "REFUND" -> "R";
          default -> "E";
        };

    return BalanceResponse.builder()
        .seq(tx.getSeq())
        .userSeq(tx.getUserSeq())
        .balance(tx.getAmount())
        .totalBalance(tx.getBalanceAfter())
        .operation(operation)
        .operationName(opName)
        .comment(tx.getComment())
        .regDate(tx.getRegDate())
        .build();
  }
}
