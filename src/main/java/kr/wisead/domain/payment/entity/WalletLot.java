package kr.wisead.domain.payment.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

/** 지갑 Lot Entity (POINT/BONUS 유효기간 관리) wallet_lot 테이블 매핑 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class WalletLot {

  private Long lotSeq; // Lot 시퀀스 (PK)
  private Integer userSeq; // 사용자 시퀀스
  private String currencyType; // 통화 유형 (POINT, BONUS)
  private BigDecimal amount; // 적립 금액
  private BigDecimal remaining; // 잔여 금액
  private LocalDate expireDate; // 만료일
  private String status; // 상태 (ACTIVE, EXPIRED, USED)
  private String source; // 적립 사유
  private LocalDateTime regDate; // 등록일시

  // 상태 상수
  public static final String STATUS_ACTIVE = "ACTIVE";
  public static final String STATUS_EXPIRED = "EXPIRED";
  public static final String STATUS_USED = "USED";

  /** 잔여 금액 차감 */
  public BigDecimal deduct(BigDecimal deductAmount) {
    BigDecimal actualDeduct = deductAmount.min(this.remaining);
    this.remaining = this.remaining.subtract(actualDeduct);

    // 전액 사용 시 상태 변경
    if (this.remaining.compareTo(BigDecimal.ZERO) == 0) {
      this.status = STATUS_USED;
    }

    return actualDeduct;
  }

  /** 잔여 금액 복원 (환불 시) */
  public void restore(BigDecimal restoreAmount) {
    this.remaining = this.remaining.add(restoreAmount);

    // 잔여 금액이 생기면 ACTIVE로 복원
    if (this.remaining.compareTo(BigDecimal.ZERO) > 0 && STATUS_USED.equals(this.status)) {
      this.status = STATUS_ACTIVE;
    }
  }

  /** 만료 처리 */
  public void expire() {
    this.status = STATUS_EXPIRED;
  }

  /** 활성 상태 여부 */
  public boolean isActive() {
    return STATUS_ACTIVE.equals(this.status);
  }

  /** 만료 여부 (날짜 기준) */
  public boolean isExpired(LocalDate today) {
    return this.expireDate.isBefore(today);
  }

  /** 사용 가능 여부 */
  public boolean isUsable(LocalDate today) {
    return isActive() && !isExpired(today) && this.remaining.compareTo(BigDecimal.ZERO) > 0;
  }

  /** 포인트 Lot 생성 */
  public static WalletLot createPointLot(
      Integer userSeq, BigDecimal amount, LocalDate expireDate, String source) {
    return WalletLot.builder()
        .userSeq(userSeq)
        .currencyType("POINT")
        .amount(amount)
        .remaining(amount)
        .expireDate(expireDate)
        .status(STATUS_ACTIVE)
        .source(source)
        .build();
  }

  /** 보너스 Lot 생성 */
  public static WalletLot createBonusLot(
      Integer userSeq, BigDecimal amount, LocalDate expireDate, String source) {
    return WalletLot.builder()
        .userSeq(userSeq)
        .currencyType("BONUS")
        .amount(amount)
        .remaining(amount)
        .expireDate(expireDate)
        .status(STATUS_ACTIVE)
        .source(source)
        .build();
  }
}
