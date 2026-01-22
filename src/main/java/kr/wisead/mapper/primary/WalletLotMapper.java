package kr.wisead.mapper.primary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import kr.wisead.domain.payment.entity.WalletLot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 지갑 Lot Mapper (POINT/BONUS 유효기간 관리) */
@Mapper
public interface WalletLotMapper {

  /** Lot 생성 */
  int insert(WalletLot lot);

  /** Lot 조회 */
  Optional<WalletLot> selectBySeq(@Param("lotSeq") Long lotSeq);

  /** 활성 Lot 목록 조회 (만료일 빠른 순) */
  List<WalletLot> selectActiveByType(
      @Param("userSeq") Integer userSeq,
      @Param("currencyType") String currencyType,
      @Param("today") LocalDate today);

  /** 활성 Lot 목록 조회 (FOR UPDATE - 동시성 제어) */
  List<WalletLot> selectActiveByTypeForUpdate(
      @Param("userSeq") Integer userSeq,
      @Param("currencyType") String currencyType,
      @Param("today") LocalDate today);

  /** 사용자별 통화 유형 잔여 금액 합계 조회 */
  BigDecimal selectSumRemaining(
      @Param("userSeq") Integer userSeq,
      @Param("currencyType") String currencyType,
      @Param("today") LocalDate today);

  /** 사용자별 모든 통화 유형 잔여 금액 합계 조회 (POINT + BONUS) - 단일 쿼리로 POINT/BONUS 합계 동시 조회 */
  List<CurrencyBalance> selectAllSumRemaining(
      @Param("userSeq") Integer userSeq, @Param("today") LocalDate today);

  /** 통화별 잔액 DTO */
  record CurrencyBalance(String currencyType, java.math.BigDecimal balance) {}

  /** Lot 잔여 금액 및 상태 업데이트 */
  int updateRemainingAndStatus(WalletLot lot);

  /** Lot 잔여 금액 증가 (환불 시) */
  int addRemaining(@Param("lotSeq") Long lotSeq, @Param("amount") BigDecimal amount);

  /** 환불 시 상태 복원 (USED → ACTIVE) */
  int reactivateIfNeeded(@Param("lotSeq") Long lotSeq);

  /** 환불 시 잔여 금액 증가 + 상태 복원 (단일 쿼리) - N+1 최적화: addRemaining + reactivateIfNeeded 통합 */
  int addRemainingAndReactivate(@Param("lotSeq") Long lotSeq, @Param("amount") BigDecimal amount);

  /** 만료 처리 (배치용) */
  int updateExpiredStatus(@Param("today") LocalDate today);

  /** 합산 대상 그룹 조회 (같은 user_seq + currency_type + expire_date) */
  List<LotGroup> selectDuplicateLotGroups();

  /** 그룹별 Lot 목록 조회 */
  List<WalletLot> selectByGroup(
      @Param("userSeq") Integer userSeq,
      @Param("currencyType") String currencyType,
      @Param("expireDate") LocalDate expireDate);

  /** Lot 업데이트 (합산 시) */
  int update(WalletLot lot);

  /** Lot 삭제 (합산 시 중복 제거) */
  int delete(@Param("lotSeq") Long lotSeq);

  /** 사용자의 모든 활성 Lot 조회 */
  List<WalletLot> selectAllActiveByUserId(
      @Param("userSeq") Integer userSeq, @Param("today") LocalDate today);

  /** Lot 합산 그룹 DTO */
  record LotGroup(Integer userSeq, String currencyType, LocalDate expireDate, int count) {}
}
