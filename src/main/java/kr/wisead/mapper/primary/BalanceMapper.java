package kr.wisead.mapper.primary;

import kr.wisead.domain.payment.entity.Balance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

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

    /**
     * 일별 과금 통계 조회
     */
    List<Map<String, Object>> selectDailyBillingStats(@Param("userId") String userId,
                                                       @Param("startDate") String startDate,
                                                       @Param("endDate") String endDate);

    /**
     * 월별 과금 통계 조회
     */
    List<Map<String, Object>> selectMonthlyBillingStats(@Param("userId") String userId,
                                                         @Param("startDate") String startDate,
                                                         @Param("endDate") String endDate);

    /**
     * 서비스 타입별 과금 통계
     */
    List<Map<String, Object>> selectBillingStatsByServiceType(@Param("userId") String userId,
                                                               @Param("startDate") String startDate,
                                                               @Param("endDate") String endDate);

    /**
     * 사용자별 과금 통계
     */
    List<Map<String, Object>> selectBillingStatsByUser(@Param("userIds") List<String> userIds,
                                                        @Param("startDate") String startDate,
                                                        @Param("endDate") String endDate);

    /**
     * 기간별 총계 조회
     */
    Map<String, Object> selectBillingSummary(@Param("userId") String userId,
                                              @Param("startDate") String startDate,
                                              @Param("endDate") String endDate);

    /**
     * 최근 거래 내역 조회
     */
    List<Balance> selectRecentTransactions(@Param("operation") String operation,
                                            @Param("limit") int limit);

    /**
     * 사용자별 현재 잔액 목록 조회
     */
    List<Map<String, Object>> selectCurrentBalanceByUsers(@Param("userIds") List<String> userIds);
}
