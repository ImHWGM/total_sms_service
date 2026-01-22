package kr.wisead.domain.payment.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import kr.wisead.domain.payment.entity.WalletLot;
import kr.wisead.mapper.primary.WalletLotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지갑 Lot 배치 서비스 - 만료 처리 - Lot 합산 (같은 만료일) */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletLotBatchService {

  private final WalletLotMapper walletLotMapper;

  /** 만료 처리 배치 (매일 자정) */
  @Scheduled(cron = "0 0 0 * * *")
  @Transactional
  public void expireLots() {
    LocalDate today = LocalDate.now();
    int updated = walletLotMapper.updateExpiredStatus(today);
    log.info("[배치] 만료된 Lot {} 건 처리 완료", updated);
  }

  /** Lot 합산 배치 (매일 새벽 3시) 같은 user_seq + currency_type + expire_date의 Lot을 하나로 합산 */
  @Scheduled(cron = "0 0 3 * * *")
  @Transactional
  public void consolidateLots() {
    List<WalletLotMapper.LotGroup> groups = walletLotMapper.selectDuplicateLotGroups();

    int consolidatedCount = 0;
    for (WalletLotMapper.LotGroup group : groups) {
      try {
        consolidateLotGroup(group);
        consolidatedCount++;
      } catch (Exception e) {
        log.error(
            "[배치] Lot 합산 실패 - userSeq: {}, currencyType: {}, expireDate: {}",
            group.userSeq(),
            group.currencyType(),
            group.expireDate(),
            e);
      }
    }

    log.info("[배치] Lot 합산 완료 - {} 그룹 처리", consolidatedCount);
  }

  /** 개별 그룹 합산 처리 */
  private void consolidateLotGroup(WalletLotMapper.LotGroup group) {
    List<WalletLot> lots =
        walletLotMapper.selectByGroup(group.userSeq(), group.currencyType(), group.expireDate());

    if (lots.size() <= 1) return;

    // 합산
    BigDecimal totalAmount =
        lots.stream().map(WalletLot::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalRemaining =
        lots.stream().map(WalletLot::getRemaining).reduce(BigDecimal.ZERO, BigDecimal::add);

    // 첫 번째 Lot에 합산 값 저장
    WalletLot firstLot = lots.get(0);
    WalletLot consolidatedLot =
        WalletLot.builder()
            .lotSeq(firstLot.getLotSeq())
            .userSeq(firstLot.getUserSeq())
            .currencyType(firstLot.getCurrencyType())
            .amount(totalAmount)
            .remaining(totalRemaining)
            .expireDate(firstLot.getExpireDate())
            .status(totalRemaining.compareTo(BigDecimal.ZERO) > 0 ? "ACTIVE" : "USED")
            .source(firstLot.getSource())
            .build();

    walletLotMapper.update(consolidatedLot);

    // 나머지 Lot 삭제
    for (int i = 1; i < lots.size(); i++) {
      walletLotMapper.delete(lots.get(i).getLotSeq());
    }

    log.debug(
        "[배치] Lot 합산 - userSeq: {}, currencyType: {}, expireDate: {}, {} → 1개",
        group.userSeq(),
        group.currencyType(),
        group.expireDate(),
        lots.size());
  }

  /** 수동 만료 처리 (관리자용) */
  @Transactional
  public int manualExpireLots() {
    LocalDate today = LocalDate.now();
    int updated = walletLotMapper.updateExpiredStatus(today);
    log.info("[수동] 만료된 Lot {} 건 처리 완료", updated);
    return updated;
  }

  /** 수동 Lot 합산 (관리자용) */
  @Transactional
  public int manualConsolidateLots() {
    List<WalletLotMapper.LotGroup> groups = walletLotMapper.selectDuplicateLotGroups();

    int consolidatedCount = 0;
    for (WalletLotMapper.LotGroup group : groups) {
      try {
        consolidateLotGroup(group);
        consolidatedCount++;
      } catch (Exception e) {
        log.error(
            "[수동] Lot 합산 실패 - userSeq: {}, currencyType: {}, expireDate: {}",
            group.userSeq(),
            group.currencyType(),
            group.expireDate(),
            e);
      }
    }

    log.info("[수동] Lot 합산 완료 - {} 그룹 처리", consolidatedCount);
    return consolidatedCount;
  }
}
