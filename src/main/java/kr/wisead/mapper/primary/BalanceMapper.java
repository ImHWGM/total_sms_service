package kr.wisead.mapper.primary;

import kr.wisead.domain.payment.entity.Balance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 잔액 Mapper
 */
@Mapper
public interface BalanceMapper {

    /**
     * 잔액 내역 등록
     */
    int insertBalance(Balance balance);

    /**
     * 최신 잔액 조회
     */
    Balance selectLatestBalance(@Param("userId") String userId);

    /**
     * 잔액 내역 목록 조회
     */
    List<Balance> selectBalanceHistory(@Param("userId") String userId,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    /**
     * 잔액 내역 총 개수
     */
    int selectBalanceHistoryCount(@Param("userId") String userId);

    /**
     * 충전 내역 조회
     */
    List<Balance> selectChargeHistory(@Param("userId") String userId,
                                       @Param("startDate") String startDate,
                                       @Param("endDate") String endDate);
}
