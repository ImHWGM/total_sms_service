package kr.wisead.mapper.primary;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import kr.wisead.domain.payment.entity.Wallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 지갑 Mapper (CASH 전용) */
@Mapper
public interface WalletMapper {

  /** 지갑 생성 */
  int insert(Wallet wallet);

  /** 지갑 생성 (중복 무시 - INSERT IGNORE) 동시성 환경에서 중복 삽입 방지 */
  int insertIgnore(Wallet wallet);

  /** 사용자 지갑 조회 */
  Optional<Wallet> selectByUserSeq(
      @Param("userSeq") Integer userSeq, @Param("currencyType") String currencyType);

  /** 사용자 지갑 조회 (FOR UPDATE - 동시성 제어) */
  Optional<Wallet> selectForUpdate(
      @Param("userSeq") Integer userSeq, @Param("currencyType") String currencyType);

  /** 잔액 업데이트 */
  int updateBalance(
      @Param("userSeq") Integer userSeq,
      @Param("currencyType") String currencyType,
      @Param("balance") BigDecimal balance);

  /** 잔액 증가 */
  int addBalance(
      @Param("userSeq") Integer userSeq,
      @Param("currencyType") String currencyType,
      @Param("amount") BigDecimal amount);

  /** 잔액 차감 */
  int subtractBalance(
      @Param("userSeq") Integer userSeq,
      @Param("currencyType") String currencyType,
      @Param("amount") BigDecimal amount);

  /** 다중 사용자 지갑 조회 */
  List<Wallet> selectByUserSeqs(
      @Param("userSeqs") List<Integer> userSeqs, @Param("currencyType") String currencyType);

  /** 지갑 존재 여부 확인 */
  boolean existsByUserSeq(
      @Param("userSeq") Integer userSeq, @Param("currencyType") String currencyType);
}
