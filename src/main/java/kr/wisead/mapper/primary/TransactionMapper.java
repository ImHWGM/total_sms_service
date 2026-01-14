package kr.wisead.mapper.primary;

import kr.wisead.domain.payment.entity.Transaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 거래 내역 Mapper
 */
@Mapper
public interface TransactionMapper {

    /**
     * 거래 내역 등록
     */
    int insert(Transaction transaction);

    /**
     * 거래 내역 조회
     */
    Optional<Transaction> selectBySeq(@Param("seq") Long seq);

    /**
     * 거래 그룹 조회 (복합결제)
     */
    List<Transaction> selectByGroupId(@Param("txGroupId") String txGroupId);

    /**
     * 사용자 거래 내역 조회 (페이징)
     */
    List<Transaction> selectHistory(@Param("userId") String userId,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /**
     * 사용자 거래 내역 총 개수
     */
    int selectHistoryCount(@Param("userId") String userId);

    /**
     * 기간별 거래 내역 조회
     */
    List<Transaction> selectByDateRange(@Param("userId") String userId,
                                         @Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate);

    /**
     * 거래 유형별 조회
     */
    List<Transaction> selectByType(@Param("userId") String userId,
                                    @Param("txType") String txType,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    /**
     * 최근 거래 내역 조회
     */
    List<Transaction> selectRecent(@Param("txType") String txType,
                                    @Param("limit") int limit);

    /**
     * 일별 과금 통계
     */
    List<Map<String, Object>> selectDailyStats(@Param("userId") String userId,
                                                @Param("startDate") String startDate,
                                                @Param("endDate") String endDate);

    /**
     * 월별 과금 통계
     */
    List<Map<String, Object>> selectMonthlyStats(@Param("userId") String userId,
                                                  @Param("startDate") String startDate,
                                                  @Param("endDate") String endDate);

    /**
     * 서비스별 과금 통계
     */
    List<Map<String, Object>> selectStatsByServiceId(@Param("userId") String userId,
                                                      @Param("startDate") String startDate,
                                                      @Param("endDate") String endDate);

    /**
     * 기간별 총계
     */
    Map<String, Object> selectSummary(@Param("userId") String userId,
                                       @Param("startDate") String startDate,
                                       @Param("endDate") String endDate);

    /**
     * Lot별 거래 내역 조회
     */
    List<Transaction> selectByLotSeq(@Param("lotSeq") Long lotSeq);

    /**
     * 사용자별 과금 통계
     */
    List<Map<String, Object>> selectStatsByUsers(@Param("userIds") List<String> userIds,
                                                  @Param("startDate") String startDate,
                                                  @Param("endDate") String endDate);

    /**
     * 사용자별 현재 잔액 및 마지막 거래일 조회
     */
    List<Map<String, Object>> selectUserBalanceSummary(@Param("userIds") List<String> userIds);

    /**
     * 최근 거래일 조회
     */
    LocalDateTime selectLastTransactionDate(@Param("userId") String userId);
}