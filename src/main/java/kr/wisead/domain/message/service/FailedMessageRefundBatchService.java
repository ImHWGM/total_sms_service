package kr.wisead.domain.message.service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import kr.wisead.domain.message.entity.MsgResult;
import kr.wisead.domain.payment.dto.RefundResult;
import kr.wisead.domain.payment.entity.Transaction;
import kr.wisead.domain.payment.service.WalletService;
import kr.wisead.mapper.primary.TransactionMapper;
import kr.wisead.mapper.sms.MsgResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 발송 실패 메시지 자동 환불 배치 서비스
 *
 * <p>발송 실패한 메시지에 대해 차감된 금액을 자동으로 환불 처리합니다.
 *
 * <pre>
 * 실행 주기: 10분마다
 * 처리 대상:
 * - RESULT != '0' (발송 실패)
 * - STAT = 3 (결과 수신 완료)
 * - REFUND_YN = 'N' (미환불)
 * - EXT_COL2 IS NOT NULL (txGroupId 존재)
 *
 * 환불 방식:
 * - 실패 건 1개당 단가만큼 부분 환불
 * - 환불 순서: CASH → POINT → BONUS (결제 역순)
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FailedMessageRefundBatchService {

  private final MsgResultMapper msgResultMapper;
  private final TransactionMapper transactionMapper;
  private final WalletService walletService;

  private static final int BATCH_SIZE = 100;

  /** 발송 실패 환불 배치 실행 (10분마다) */
  @Scheduled(cron = "0 */10 * * * *")
  public void processFailedMessageRefund() {
    log.info("발송 실패 환불 배치 시작");

    // 현재 월과 이전 월 테이블 처리 (월초에 이전 월 실패 건 처리 위해)
    YearMonth currentMonth = YearMonth.now();
    YearMonth previousMonth = currentMonth.minusMonths(1);

    int totalProcessed = 0;
    totalProcessed += processTable(getTableName(currentMonth));
    totalProcessed += processTable(getTableName(previousMonth));

    log.info("발송 실패 환불 배치 완료 - 총 처리 건수: {}", totalProcessed);
  }

  /** 특정 테이블의 실패 건 환불 처리 */
  private int processTable(String tableName) {
    // 1. REFUND_YN 컬럼 존재 확인 및 추가
    if (!ensureRefundColumn(tableName)) {
      log.warn("테이블 {} 에 REFUND_YN 컬럼 추가 실패 또는 테이블 없음", tableName);
      return 0;
    }

    // 2. 환불 안 된 실패 건 조회
    List<MsgResult> failedMessages;
    try {
      failedMessages = msgResultMapper.selectUnrefundedFailures(tableName, BATCH_SIZE);
    } catch (Exception e) {
      log.warn("테이블 {} 조회 실패: {}", tableName, e.getMessage());
      return 0;
    }

    if (failedMessages.isEmpty()) {
      log.debug("테이블 {} - 환불 대상 없음", tableName);
      return 0;
    }

    log.info("테이블 {} - 환불 대상 {} 건 발견", tableName, failedMessages.size());

    int processedCount = 0;
    for (MsgResult msg : failedMessages) {
      try {
        processRefund(tableName, msg);
        processedCount++;
      } catch (Exception e) {
        log.error(
            "환불 처리 실패 - tableName: {}, mseq: {}, txGroupId: {}, error: {}",
            tableName,
            msg.getMseq(),
            msg.getExtCol2(),
            e.getMessage());
        // 실패해도 REFUND_YN은 업데이트하지 않음 (다음 배치에서 재시도)
      }
    }

    log.info("테이블 {} - 환불 처리 완료: {}/{}", tableName, processedCount, failedMessages.size());
    return processedCount;
  }

  /** 개별 메시지 환불 처리 - 1건당 단가만큼 부분 환불 */
  private void processRefund(String tableName, MsgResult msg) {
    String txGroupId = msg.getExtCol2();

    log.debug(
        "환불 처리 시작 - tableName: {}, mseq: {}, txGroupId: {}, result: {}",
        tableName,
        msg.getMseq(),
        txGroupId,
        msg.getResult());

    // 1. txGroupId로 원래 거래 내역 조회하여 단가 확인
    List<Transaction> transactions = transactionMapper.selectByGroupId(txGroupId);
    if (transactions.isEmpty()) {
      log.warn("거래 내역 없음 - txGroupId: {}", txGroupId);
      // 거래 내역이 없어도 REFUND_YN은 Y로 변경 (중복 처리 방지)
      msgResultMapper.updateRefundStatus(tableName, msg.getMseq(), "Y");
      return;
    }

    // 2. 단가 확인 (첫 번째 DEDUCT 거래에서 unitPrice 조회)
    BigDecimal unitPrice =
        transactions.stream()
            .filter(tx -> Transaction.TX_TYPE_DEDUCT.equals(tx.getTxType()))
            .filter(tx -> tx.getUnitPrice() != null)
            .map(Transaction::getUnitPrice)
            .findFirst()
            .orElse(null);

    if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
      log.warn("단가 정보 없음 - txGroupId: {}", txGroupId);
      // 단가 정보가 없어도 REFUND_YN은 Y로 변경 (중복 처리 방지)
      msgResultMapper.updateRefundStatus(tableName, msg.getMseq(), "Y");
      return;
    }

    // 3. 1건당 단가만큼 부분 환불
    RefundResult refundResult = walletService.refundPartialByGroup(txGroupId, unitPrice);

    // 4. 환불 처리 완료 후 REFUND_YN = 'Y' 업데이트
    msgResultMapper.updateRefundStatus(tableName, msg.getMseq(), "Y");

    log.info(
        "환불 처리 완료 - tableName: {}, mseq: {}, txGroupId: {}, unitPrice: {}, refundedAmount: {},"
            + " expiredAmount: {}",
        tableName,
        msg.getMseq(),
        txGroupId,
        unitPrice,
        refundResult.getRefundedAmount(),
        refundResult.getExpiredAmount());
  }

  /** REFUND_YN 컬럼 존재 확인 및 추가 */
  private boolean ensureRefundColumn(String tableName) {
    try {
      int exists = msgResultMapper.checkRefundColumnExists(tableName);
      if (exists == 0) {
        log.info("테이블 {} 에 REFUND_YN 컬럼 추가", tableName);
        msgResultMapper.addRefundColumn(tableName);
      }
      return true;
    } catch (Exception e) {
      // 테이블이 존재하지 않는 경우 (아직 해당 월 데이터 없음)
      log.debug("테이블 {} 확인 실패: {}", tableName, e.getMessage());
      return false;
    }
  }

  /** 월별 테이블명 생성 */
  private String getTableName(YearMonth yearMonth) {
    return "msg_result_" + yearMonth.format(DateTimeFormatter.ofPattern("yyyyMM"));
  }
}
