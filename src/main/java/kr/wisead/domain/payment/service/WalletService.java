package kr.wisead.domain.payment.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.payment.dto.*;
import kr.wisead.domain.payment.entity.Transaction;
import kr.wisead.domain.payment.entity.Wallet;
import kr.wisead.domain.payment.entity.WalletLot;
import kr.wisead.mapper.primary.TransactionMapper;
import kr.wisead.mapper.primary.WalletLotMapper;
import kr.wisead.mapper.primary.WalletMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지갑 서비스 (결제/환불 핵심 로직) */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

  private final WalletMapper walletMapper;
  private final WalletLotMapper walletLotMapper;
  private final TransactionMapper transactionMapper;
  private final UserServiceRateService userServiceRateService;
  private final UserIdResolver userIdResolver;

  /** 지갑 요약 조회 (CASH + POINT + BONUS) - 최적화: DB 호출 3회 → 2회 (POINT/BONUS 합쳐서 조회) */
  @Transactional(readOnly = true)
  public WalletSummaryResponse getWalletSummary(Integer userSeq) {
    LocalDate today = LocalDate.now();

    // CASH 조회
    BigDecimal cash =
        walletMapper
            .selectByUserSeq(userSeq, "CASH")
            .map(Wallet::getBalance)
            .orElse(BigDecimal.ZERO);

    // POINT + BONUS 한 번에 조회
    List<WalletLotMapper.CurrencyBalance> lotBalances =
        walletLotMapper.selectAllSumRemaining(userSeq, today);

    BigDecimal point = BigDecimal.ZERO;
    BigDecimal bonus = BigDecimal.ZERO;

    for (WalletLotMapper.CurrencyBalance cb : lotBalances) {
      if ("POINT".equals(cb.currencyType())) {
        point = cb.balance();
      } else if ("BONUS".equals(cb.currencyType())) {
        bonus = cb.balance();
      }
    }

    return WalletSummaryResponse.of(userSeq, cash, point, bonus);
  }

  /** 잔액 충분 여부 확인 */
  @Transactional(readOnly = true)
  public boolean hasEnoughBalance(Integer userSeq, BigDecimal amount) {
    WalletSummaryResponse summary = getWalletSummary(userSeq);
    return summary.hasEnoughBalance(amount);
  }

  /** 적용 단가 조회 우선순위: 기간 특별요금 → 사용자 기본요금 → 표준요금 */
  @Transactional(readOnly = true)
  public BigDecimal getAppliedRate(Integer userSeq, String serviceId) {
    BigDecimal rate = userServiceRateService.getEffectiveRate(userSeq, serviceId);
    if (rate.compareTo(BigDecimal.ZERO) == 0) {
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "서비스 단가를 찾을 수 없습니다: " + serviceId);
    }
    return rate;
  }

  /** CASH 충전 */
  @Transactional
  public TransactionResponse charge(Integer userSeq, BigDecimal amount, String comment) {
    // 지갑 존재 확인
    Optional<Wallet> walletOpt = walletMapper.selectForUpdate(userSeq, "CASH");

    // 없으면 생성 (INSERT IGNORE로 동시성 문제 방지)
    if (walletOpt.isEmpty()) {
      Wallet newWallet = Wallet.createCashWallet(userSeq);
      walletMapper.insertIgnore(newWallet); // 중복 시 무시
      // 다시 조회 (다른 스레드가 먼저 생성했을 수 있음)
      walletOpt = walletMapper.selectForUpdate(userSeq, "CASH");
    }

    Wallet wallet =
        walletOpt.orElseThrow(
            () -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "지갑 생성 실패"));

    // 잔액 증가
    walletMapper.addBalance(userSeq, "CASH", amount);
    BigDecimal balanceAfter = wallet.getBalance().add(amount);

    // 거래 내역 기록
    Transaction tx = Transaction.createCharge(userSeq, amount, balanceAfter, comment);
    transactionMapper.insert(tx);

    log.info("충전 완료 - userSeq: {}, amount: {}, balanceAfter: {}", userSeq, amount, balanceAfter);
    return TransactionResponse.from(tx);
  }

  /** 금액 직접 차감 (서비스ID 없이) - 우선순위: BONUS → POINT → CASH */
  @Transactional
  public String deductByAmount(Integer userSeq, BigDecimal amount, String comment) {
    String txGroupId = UUID.randomUUID().toString().replace("-", "");
    return deductByAmount(userSeq, amount, comment, txGroupId);
  }

  /**
   * 금액 직접 차감 (서비스ID 없이, txGroupId 지정 버전) - 우선순위: BONUS → POINT → CASH - 외부에서 생성한 txGroupId를 사용 (메시지
   * 취소 시 환불 추적용)
   */
  @Transactional
  public String deductByAmount(
      Integer userSeq, BigDecimal amount, String comment, String txGroupId) {
    // 잔액 충분 여부 확인
    if (!hasEnoughBalance(userSeq, amount)) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "잔액이 부족합니다.");
    }

    LocalDate today = LocalDate.now();
    BigDecimal remaining = amount;

    // 1. BONUS Lot 차감 (만료일 빠른 순)
    remaining = deductFromLotsSimple(userSeq, "BONUS", remaining, txGroupId, today, comment);

    // 2. POINT Lot 차감 (만료일 빠른 순)
    remaining = deductFromLotsSimple(userSeq, "POINT", remaining, txGroupId, today, comment);

    // 3. CASH 차감
    if (remaining.compareTo(BigDecimal.ZERO) > 0) {
      deductFromCashSimple(userSeq, remaining, txGroupId, comment);
    }

    log.info("금액 직접 차감 완료 - userSeq: {}, amount: {}, txGroupId: {}", userSeq, amount, txGroupId);
    return txGroupId;
  }

  /** 우선순위 차감 (BONUS → POINT → CASH) - 서비스ID 기반 단가 조회 후 수량 × 단가로 차감 */
  @Transactional
  public String deductWithPriority(
      Integer userSeq, String serviceId, BigDecimal quantity, String comment) {
    String txGroupId = UUID.randomUUID().toString().replace("-", "");
    return deductWithPriority(userSeq, serviceId, quantity, comment, txGroupId);
  }

  /**
   * 우선순위 차감 (BONUS → POINT → CASH) - txGroupId 지정 버전 - 서비스ID 기반 단가 조회 후 수량 × 단가로 차감 - 외부에서 생성한
   * txGroupId를 사용 (메시지 취소 시 환불 추적용)
   */
  @Transactional
  public String deductWithPriority(
      Integer userSeq, String serviceId, BigDecimal quantity, String comment, String txGroupId) {
    BigDecimal unitPrice = getAppliedRate(userSeq, serviceId);
    BigDecimal totalAmount = unitPrice.multiply(quantity);

    // 잔액 충분 여부 확인
    if (!hasEnoughBalance(userSeq, totalAmount)) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "잔액이 부족합니다.");
    }

    LocalDate today = LocalDate.now();
    BigDecimal remaining = totalAmount;

    // 1. BONUS Lot 차감 (만료일 빠른 순)
    remaining =
        deductFromLots(
            userSeq, "BONUS", remaining, txGroupId, serviceId, unitPrice, quantity, today, comment);

    // 2. POINT Lot 차감 (만료일 빠른 순)
    remaining =
        deductFromLots(
            userSeq, "POINT", remaining, txGroupId, serviceId, unitPrice, quantity, today, comment);

    // 3. CASH 차감
    if (remaining.compareTo(BigDecimal.ZERO) > 0) {
      deductFromCash(userSeq, remaining, txGroupId, serviceId, unitPrice, quantity, comment);
    }

    log.info(
        "차감 완료 - userSeq: {}, serviceId: {}, totalAmount: {}, txGroupId: {}",
        userSeq,
        serviceId,
        totalAmount,
        txGroupId);
    return txGroupId;
  }

  /** Lot에서 차감 (내부 메서드) */
  private BigDecimal deductFromLots(
      Integer userSeq,
      String currencyType,
      BigDecimal remaining,
      String txGroupId,
      String serviceId,
      BigDecimal unitPrice,
      BigDecimal totalQuantity,
      LocalDate today,
      String comment) {
    if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
      return remaining;
    }

    List<WalletLot> lots =
        walletLotMapper.selectActiveByTypeForUpdate(userSeq, currencyType, today);

    for (WalletLot lot : lots) {
      if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

      BigDecimal deductFromThis = remaining.min(lot.getRemaining());
      BigDecimal deductQuantity =
          deductFromThis.divide(unitPrice, 3, java.math.RoundingMode.HALF_UP);

      // Lot 차감
      lot.deduct(deductFromThis);
      walletLotMapper.updateRemainingAndStatus(lot);

      // 거래 내역 기록
      Transaction tx =
          Transaction.createLotDeduct(
              txGroupId,
              userSeq,
              currencyType,
              serviceId,
              deductFromThis,
              unitPrice,
              deductQuantity,
              lot.getLotSeq(),
              lot.getExpireDate(),
              lot.getRemaining(),
              comment);
      transactionMapper.insert(tx);

      remaining = remaining.subtract(deductFromThis);

      log.debug(
          "Lot 차감 - lotSeq: {}, currencyType: {}, amount: {}, remaining: {}",
          lot.getLotSeq(),
          currencyType,
          deductFromThis,
          lot.getRemaining());
    }

    return remaining;
  }

  /** CASH에서 차감 (내부 메서드) */
  private void deductFromCash(
      Integer userSeq,
      BigDecimal amount,
      String txGroupId,
      String serviceId,
      BigDecimal unitPrice,
      BigDecimal totalQuantity,
      String comment) {
    Wallet wallet =
        walletMapper
            .selectForUpdate(userSeq, "CASH")
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "지갑을 찾을 수 없습니다."));

    if (!wallet.hasEnoughBalance(amount)) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "CASH 잔액이 부족합니다.");
    }

    BigDecimal deductQuantity = amount.divide(unitPrice, 3, java.math.RoundingMode.HALF_UP);

    walletMapper.subtractBalance(userSeq, "CASH", amount);
    BigDecimal balanceAfter = wallet.getBalance().subtract(amount);

    Transaction tx =
        Transaction.createCashDeduct(
            txGroupId,
            userSeq,
            serviceId,
            amount,
            unitPrice,
            deductQuantity,
            balanceAfter,
            comment);
    transactionMapper.insert(tx);

    log.debug("CASH 차감 - userSeq: {}, amount: {}, balanceAfter: {}", userSeq, amount, balanceAfter);
  }

  /** Lot에서 금액 직접 차감 (Simple - 서비스ID/단가 없이) */
  private BigDecimal deductFromLotsSimple(
      Integer userSeq,
      String currencyType,
      BigDecimal remaining,
      String txGroupId,
      LocalDate today,
      String comment) {
    if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
      return remaining;
    }

    List<WalletLot> lots =
        walletLotMapper.selectActiveByTypeForUpdate(userSeq, currencyType, today);

    for (WalletLot lot : lots) {
      if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

      BigDecimal deductFromThis = remaining.min(lot.getRemaining());

      // Lot 차감
      lot.deduct(deductFromThis);
      walletLotMapper.updateRemainingAndStatus(lot);

      // 거래 내역 기록 (serviceId, unitPrice, quantity 없이)
      Transaction tx =
          Transaction.builder()
              .txGroupId(txGroupId)
              .userSeq(userSeq)
              .currencyType(currencyType)
              .txType(Transaction.TX_TYPE_DEDUCT)
              .amount(deductFromThis)
              .balanceAfter(lot.getRemaining())
              .lotSeq(lot.getLotSeq())
              .lotExpireDate(lot.getExpireDate())
              .comment(comment)
              .build();
      transactionMapper.insert(tx);

      remaining = remaining.subtract(deductFromThis);

      log.debug(
          "Lot 차감 (Simple) - lotSeq: {}, currencyType: {}, amount: {}, remaining: {}",
          lot.getLotSeq(),
          currencyType,
          deductFromThis,
          lot.getRemaining());
    }

    return remaining;
  }

  /** CASH에서 금액 직접 차감 (Simple - 서비스ID/단가 없이) */
  private void deductFromCashSimple(
      Integer userSeq, BigDecimal amount, String txGroupId, String comment) {
    Wallet wallet =
        walletMapper
            .selectForUpdate(userSeq, "CASH")
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "지갑을 찾을 수 없습니다."));

    if (!wallet.hasEnoughBalance(amount)) {
      throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "CASH 잔액이 부족합니다.");
    }

    walletMapper.subtractBalance(userSeq, "CASH", amount);
    BigDecimal balanceAfter = wallet.getBalance().subtract(amount);

    Transaction tx =
        Transaction.builder()
            .txGroupId(txGroupId)
            .userSeq(userSeq)
            .currencyType(Transaction.CURRENCY_CASH)
            .txType(Transaction.TX_TYPE_DEDUCT)
            .amount(amount)
            .balanceAfter(balanceAfter)
            .comment(comment)
            .build();
    transactionMapper.insert(tx);

    log.debug(
        "CASH 차감 (Simple) - userSeq: {}, amount: {}, balanceAfter: {}",
        userSeq,
        amount,
        balanceAfter);
  }

  /** 환불 미리보기 */
  @Transactional(readOnly = true)
  public RefundPreviewResponse previewRefund(String txGroupId) {
    List<Transaction> originalTxs = transactionMapper.selectByGroupId(txGroupId);
    if (originalTxs.isEmpty()) {
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "거래 내역을 찾을 수 없습니다.");
    }

    LocalDate today = LocalDate.now();
    BigDecimal totalPaid = BigDecimal.ZERO;
    BigDecimal totalRefundable = BigDecimal.ZERO;
    BigDecimal totalExpired = BigDecimal.ZERO;
    List<RefundPreviewResponse.RefundItem> items = new ArrayList<>();

    for (Transaction tx : originalTxs) {
      if (!Transaction.TX_TYPE_DEDUCT.equals(tx.getTxType())) continue;

      totalPaid = totalPaid.add(tx.getAmount());
      boolean isRefundable = tx.isRefundable(today);

      if (isRefundable) {
        totalRefundable = totalRefundable.add(tx.getAmount());
      } else {
        totalExpired = totalExpired.add(tx.getAmount());
      }

      items.add(
          RefundPreviewResponse.RefundItem.builder()
              .currencyType(tx.getCurrencyType())
              .amount(tx.getAmount())
              .expireDate(tx.getLotExpireDate())
              .refundable(isRefundable)
              .reason(isRefundable ? null : "유효기간 만료")
              .build());
    }

    return RefundPreviewResponse.builder()
        .totalPaid(totalPaid)
        .totalRefundable(totalRefundable)
        .totalExpired(totalExpired)
        .hasExpiredItems(totalExpired.compareTo(BigDecimal.ZERO) > 0)
        .items(items)
        .build();
  }

  /** 환불 처리 (CASH → POINT → BONUS 역순) - N+1 최적화: Lot 환불 시 3회 쿼리 → 1회 쿼리 + 메모리 계산 */
  @Transactional
  public RefundResult refundByGroup(String txGroupId) {
    List<Transaction> originalTxs = transactionMapper.selectByGroupId(txGroupId);
    if (originalTxs.isEmpty()) {
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "거래 내역을 찾을 수 없습니다.");
    }

    // CASH → POINT → BONUS 역순 정렬 (차감 순서의 역순으로 환불)
    originalTxs.sort(
        Comparator.comparingInt(
            tx -> {
              String type = tx.getCurrencyType();
              return switch (type) {
                case "CASH" -> 0; // CASH 먼저 환불
                case "POINT" -> 1; // POINT 다음
                case "BONUS" -> 2; // BONUS 마지막
                default -> 3;
              };
            }));

    LocalDate today = LocalDate.now();
    String refundTxGroupId = UUID.randomUUID().toString().replace("-", "");
    BigDecimal totalRequested = BigDecimal.ZERO;
    BigDecimal refundedAmount = BigDecimal.ZERO;
    BigDecimal expiredAmount = BigDecimal.ZERO;
    List<RefundResult.RefundDetail> details = new ArrayList<>();
    int refundedCount = 0;

    // N+1 최적화: 환불 전 POINT/BONUS 잔액을 미리 조회하고, 환불 시 메모리에서 누적 계산
    Integer userSeq = originalTxs.get(0).getUserSeq();
    Map<String, BigDecimal> currencyBalances = new HashMap<>();
    List<WalletLotMapper.CurrencyBalance> lotBalances =
        walletLotMapper.selectAllSumRemaining(userSeq, today);
    for (WalletLotMapper.CurrencyBalance cb : lotBalances) {
      currencyBalances.put(cb.currencyType(), cb.balance());
    }

    for (Transaction tx : originalTxs) {
      if (!Transaction.TX_TYPE_DEDUCT.equals(tx.getTxType())) continue;

      totalRequested = totalRequested.add(tx.getAmount());

      if (Transaction.CURRENCY_CASH.equals(tx.getCurrencyType())) {
        // CASH는 무조건 환불
        walletMapper.addBalance(tx.getUserSeq(), "CASH", tx.getAmount());
        Wallet wallet = walletMapper.selectByUserSeq(tx.getUserSeq(), "CASH").orElseThrow();

        Transaction refundTx =
            Transaction.createRefund(
                refundTxGroupId,
                tx.getUserSeq(),
                "CASH",
                tx.getAmount(),
                wallet.getBalance(),
                tx.getSeq(),
                "환불");
        transactionMapper.insert(refundTx);

        refundedAmount = refundedAmount.add(tx.getAmount());
        refundedCount++;
        details.add(
            RefundResult.RefundDetail.builder()
                .currencyType("CASH")
                .amount(tx.getAmount())
                .refunded(true)
                .build());

      } else if (tx.isExpiredLot(today)) {
        // 포인트/보너스: 만료됨 → 환불 불가
        expiredAmount = expiredAmount.add(tx.getAmount());

        Transaction refundTx =
            Transaction.createRefund(
                refundTxGroupId,
                tx.getUserSeq(),
                tx.getCurrencyType(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                tx.getSeq(),
                "만료로 환불 불가");
        transactionMapper.insert(refundTx);

        details.add(
            RefundResult.RefundDetail.builder()
                .currencyType(tx.getCurrencyType())
                .amount(tx.getAmount())
                .refunded(false)
                .reason("유효기간 만료")
                .build());

      } else {
        // 포인트/보너스: 유효함 → 원래 Lot에 복원
        // N+1 최적화: addRemaining + reactivateIfNeeded → 단일 쿼리
        walletLotMapper.addRemainingAndReactivate(tx.getLotSeq(), tx.getAmount());

        // N+1 최적화: 루프 내 selectSumRemaining 제거 → 메모리에서 누적 계산
        String currencyType = tx.getCurrencyType();
        BigDecimal currentBalance = currencyBalances.getOrDefault(currencyType, BigDecimal.ZERO);
        BigDecimal newBalance = currentBalance.add(tx.getAmount());
        currencyBalances.put(currencyType, newBalance);

        Transaction refundTx =
            Transaction.createRefund(
                refundTxGroupId,
                tx.getUserSeq(),
                currencyType,
                tx.getAmount(),
                newBalance,
                tx.getSeq(),
                "환불");
        transactionMapper.insert(refundTx);

        refundedAmount = refundedAmount.add(tx.getAmount());
        refundedCount++;
        details.add(
            RefundResult.RefundDetail.builder()
                .currencyType(currencyType)
                .amount(tx.getAmount())
                .refunded(true)
                .build());
      }
    }

    String message = RefundResult.buildMessage(refundedAmount, expiredAmount);
    log.info(
        "환불 완료 - txGroupId: {}, refundedAmount: {}, expiredAmount: {}",
        txGroupId,
        refundedAmount,
        expiredAmount);

    return RefundResult.builder()
        .txGroupId(txGroupId)
        .refundTxGroupId(refundTxGroupId)
        .requestedAmount(totalRequested)
        .refundedAmount(refundedAmount)
        .expiredAmount(expiredAmount)
        .refundedCount(refundedCount)
        .details(details)
        .message(message)
        .build();
  }

  /** 포인트 적립 */
  @Transactional
  public WalletLotResponse grantPoint(
      Integer userSeq, BigDecimal amount, LocalDate expireDate, String source) {
    WalletLot lot = WalletLot.createPointLot(userSeq, amount, expireDate, source);
    walletLotMapper.insert(lot);

    // 거래 내역 기록
    LocalDate today = LocalDate.now();
    BigDecimal balanceAfter =
        walletLotMapper.selectSumRemaining(userSeq, Transaction.CURRENCY_POINT, today);
    Transaction tx =
        Transaction.createLotGrant(
            userSeq,
            Transaction.CURRENCY_POINT,
            amount,
            lot.getLotSeq(),
            expireDate,
            balanceAfter,
            source);
    transactionMapper.insert(tx);

    log.info("포인트 적립 - userSeq: {}, amount: {}, expireDate: {}", userSeq, amount, expireDate);
    return WalletLotResponse.from(lot);
  }

  /** 보너스 적립 */
  @Transactional
  public WalletLotResponse grantBonus(
      Integer userSeq, BigDecimal amount, LocalDate expireDate, String source) {
    WalletLot lot = WalletLot.createBonusLot(userSeq, amount, expireDate, source);
    walletLotMapper.insert(lot);

    // 거래 내역 기록
    LocalDate today = LocalDate.now();
    BigDecimal balanceAfter =
        walletLotMapper.selectSumRemaining(userSeq, Transaction.CURRENCY_BONUS, today);
    Transaction tx =
        Transaction.createLotGrant(
            userSeq,
            Transaction.CURRENCY_BONUS,
            amount,
            lot.getLotSeq(),
            expireDate,
            balanceAfter,
            source);
    transactionMapper.insert(tx);

    log.info("보너스 적립 - userSeq: {}, amount: {}, expireDate: {}", userSeq, amount, expireDate);
    return WalletLotResponse.from(lot);
  }

  /** 지갑 초기화 (신규 회원용) */
  @Transactional
  public void initializeWallet(Integer userSeq) {
    if (!walletMapper.existsByUserSeq(userSeq, "CASH")) {
      Wallet wallet = Wallet.createCashWallet(userSeq);
      walletMapper.insert(wallet);
      log.info("지갑 초기화 - userSeq: {}", userSeq);
    }
  }

  /** 사용자의 활성 Lot 목록 조회 */
  @Transactional(readOnly = true)
  public List<WalletLotResponse> getActiveLots(Integer userSeq) {
    LocalDate today = LocalDate.now();
    return walletLotMapper.selectAllActiveByUserId(userSeq, today).stream()
        .map(WalletLotResponse::from)
        .toList();
  }

  /**
   * CASH로 직접 환불 (취소 시 사용) - 원래 차감된 화폐를 추적하지 않고 CASH로 직접 환불
   *
   * @param userSeq 사용자 시퀀스
   * @param amount 환불 금액
   * @param comment 환불 사유
   * @return 환불 거래 ID
   */
  @Transactional
  public String refundToCash(Integer userSeq, BigDecimal amount, String comment) {
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      return null;
    }

    // 지갑 존재 확인
    Optional<Wallet> walletOpt = walletMapper.selectForUpdate(userSeq, "CASH");
    if (walletOpt.isEmpty()) {
      Wallet newWallet = Wallet.createCashWallet(userSeq);
      walletMapper.insertIgnore(newWallet);
      walletOpt = walletMapper.selectForUpdate(userSeq, "CASH");
    }

    Wallet wallet =
        walletOpt.orElseThrow(
            () -> new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "지갑 조회 실패"));

    // 잔액 증가
    walletMapper.addBalance(userSeq, "CASH", amount);
    BigDecimal balanceAfter = wallet.getBalance().add(amount);

    // 거래 내역 기록
    String txGroupId = UUID.randomUUID().toString().replace("-", "");
    Transaction tx =
        Transaction.builder()
            .txGroupId(txGroupId)
            .userSeq(userSeq)
            .currencyType(Transaction.CURRENCY_CASH)
            .txType(Transaction.TX_TYPE_REFUND)
            .amount(amount)
            .balanceAfter(balanceAfter)
            .comment(comment)
            .build();
    transactionMapper.insert(tx);

    log.info(
        "CASH 환불 완료 - userSeq: {}, amount: {}, balanceAfter: {}, comment: {}",
        userSeq,
        amount,
        balanceAfter,
        comment);
    return txGroupId;
  }

  /**
   * 부분 환불 처리 (txGroupId 기반, 지정 금액만 환불) - 환불 순서: CASH → POINT → BONUS (결제 역순) - 각 화폐별 결제 금액 범위 내에서
   * 순차 환불
   *
   * @param txGroupId 거래 그룹 ID
   * @param refundAmount 환불할 금액
   * @return 환불 결과
   */
  @Transactional
  public RefundResult refundPartialByGroup(String txGroupId, BigDecimal refundAmount) {
    List<Transaction> originalTxs = transactionMapper.selectByGroupId(txGroupId);
    if (originalTxs.isEmpty()) {
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "거래 내역을 찾을 수 없습니다.");
    }

    // DEDUCT 거래만 필터링
    List<Transaction> deductTxs =
        originalTxs.stream()
            .filter(tx -> Transaction.TX_TYPE_DEDUCT.equals(tx.getTxType()))
            .toList();

    if (deductTxs.isEmpty()) {
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "차감 거래 내역을 찾을 수 없습니다.");
    }

    // CASH → POINT → BONUS 순서로 정렬 (결제 역순으로 환불)
    List<Transaction> sortedTxs = new ArrayList<>(deductTxs);
    sortedTxs.sort(
        Comparator.comparingInt(
            tx -> {
              String type = tx.getCurrencyType();
              return switch (type) {
                case "CASH" -> 0;
                case "POINT" -> 1;
                case "BONUS" -> 2;
                default -> 3;
              };
            }));

    LocalDate today = LocalDate.now();
    String refundTxGroupId = UUID.randomUUID().toString().replace("-", "");
    BigDecimal remainingRefund = refundAmount;
    BigDecimal totalRefunded = BigDecimal.ZERO;
    BigDecimal expiredAmount = BigDecimal.ZERO;
    List<RefundResult.RefundDetail> details = new ArrayList<>();
    int refundedCount = 0;

    // userSeq 확인
    Integer userSeq = sortedTxs.get(0).getUserSeq();

    // N+1 최적화: POINT/BONUS 잔액 미리 조회
    Map<String, BigDecimal> currencyBalances = new HashMap<>();
    List<WalletLotMapper.CurrencyBalance> lotBalances =
        walletLotMapper.selectAllSumRemaining(userSeq, today);
    for (WalletLotMapper.CurrencyBalance cb : lotBalances) {
      currencyBalances.put(cb.currencyType(), cb.balance());
    }

    for (Transaction tx : sortedTxs) {
      if (remainingRefund.compareTo(BigDecimal.ZERO) <= 0) break;

      BigDecimal txAmount = tx.getAmount();
      BigDecimal refundFromThis = remainingRefund.min(txAmount);

      if (Transaction.CURRENCY_CASH.equals(tx.getCurrencyType())) {
        // CASH 환불
        walletMapper.addBalance(tx.getUserSeq(), "CASH", refundFromThis);
        Wallet wallet = walletMapper.selectByUserSeq(tx.getUserSeq(), "CASH").orElseThrow();

        Transaction refundTx =
            Transaction.createRefund(
                refundTxGroupId,
                tx.getUserSeq(),
                "CASH",
                refundFromThis,
                wallet.getBalance(),
                tx.getSeq(),
                "발송 실패 부분 환불");
        transactionMapper.insert(refundTx);

        totalRefunded = totalRefunded.add(refundFromThis);
        remainingRefund = remainingRefund.subtract(refundFromThis);
        refundedCount++;

        details.add(
            RefundResult.RefundDetail.builder()
                .currencyType("CASH")
                .amount(refundFromThis)
                .refunded(true)
                .build());

      } else if (tx.isExpiredLot(today)) {
        // POINT/BONUS 만료 → 해당 금액은 환불 불가, 다음 화폐로 이동
        expiredAmount = expiredAmount.add(refundFromThis);

        details.add(
            RefundResult.RefundDetail.builder()
                .currencyType(tx.getCurrencyType())
                .amount(refundFromThis)
                .refunded(false)
                .reason("유효기간 만료")
                .build());

        // 만료된 금액은 환불 대상에서 제외하지 않음 (다음 화폐에서 환불 시도)
        // remainingRefund는 유지

      } else {
        // POINT/BONUS 환불 (유효)
        walletLotMapper.addRemainingAndReactivate(tx.getLotSeq(), refundFromThis);

        String currencyType = tx.getCurrencyType();
        BigDecimal currentBalance = currencyBalances.getOrDefault(currencyType, BigDecimal.ZERO);
        BigDecimal newBalance = currentBalance.add(refundFromThis);
        currencyBalances.put(currencyType, newBalance);

        Transaction refundTx =
            Transaction.createRefund(
                refundTxGroupId,
                tx.getUserSeq(),
                currencyType,
                refundFromThis,
                newBalance,
                tx.getSeq(),
                "발송 실패 부분 환불");
        transactionMapper.insert(refundTx);

        totalRefunded = totalRefunded.add(refundFromThis);
        remainingRefund = remainingRefund.subtract(refundFromThis);
        refundedCount++;

        details.add(
            RefundResult.RefundDetail.builder()
                .currencyType(currencyType)
                .amount(refundFromThis)
                .refunded(true)
                .build());
      }
    }

    String message = RefundResult.buildMessage(totalRefunded, expiredAmount);
    log.info(
        "부분 환불 완료 - txGroupId: {}, requestedAmount: {}, refundedAmount: {}, expiredAmount: {}",
        txGroupId,
        refundAmount,
        totalRefunded,
        expiredAmount);

    return RefundResult.builder()
        .txGroupId(txGroupId)
        .refundTxGroupId(refundTxGroupId)
        .requestedAmount(refundAmount)
        .refundedAmount(totalRefunded)
        .expiredAmount(expiredAmount)
        .refundedCount(refundedCount)
        .details(details)
        .message(message)
        .build();
  }

  // ========== userId 기반 오버로드 메서드 (API 호환용) ==========

  /** CASH 충전 (userId 기반) */
  @Transactional
  public TransactionResponse charge(String userId, BigDecimal amount, String comment) {
    return charge(userIdResolver.toUserSeq(userId), amount, comment);
  }

  /** 포인트 적립 (userId 기반) */
  @Transactional
  public WalletLotResponse grantPoint(
      String userId, BigDecimal amount, LocalDate expireDate, String source) {
    return grantPoint(userIdResolver.toUserSeq(userId), amount, expireDate, source);
  }
}
