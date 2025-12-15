package kr.wisead.mapper.primary;

import kr.wisead.domain.payment.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 결제 Mapper
 */
@Mapper
public interface PaymentMapper {

    /**
     * 결제 정보 등록
     */
    int insertPayment(Payment payment);

    /**
     * 거래 ID로 결제 정보 존재 여부 확인
     */
    boolean existsByTradeId(@Param("tradeId") String tradeId);

    /**
     * 거래 ID로 결제 정보 조회
     */
    Payment selectByTradeId(@Param("tradeId") String tradeId);

    /**
     * 사용자 결제 내역 조회
     */
    List<Payment> selectPaymentHistory(@Param("userId") String userId,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    /**
     * 사용자 결제 내역 총 개수
     */
    int selectPaymentHistoryCount(@Param("userId") String userId);
}
