package kr.wisead.mapper.primary;

import kr.wisead.domain.payment.entity.ChargeBonusEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 충전 보너스 이벤트 Mapper
 */
@Mapper
public interface ChargeBonusEventMapper {

    /**
     * 이벤트 생성
     */
    int insert(ChargeBonusEvent event);

    /**
     * 이벤트 조회
     */
    Optional<ChargeBonusEvent> selectBySeq(@Param("eventSeq") Long eventSeq);

    /**
     * 현재 활성화된 이벤트 목록 조회
     * - 충전 금액 조건에 맞는 이벤트만 반환
     */
    List<ChargeBonusEvent> selectActiveEvents(@Param("today") LocalDate today,
                                               @Param("chargeAmount") BigDecimal chargeAmount);

    /**
     * 모든 이벤트 목록 조회 (관리자용)
     */
    List<ChargeBonusEvent> selectAll();

    /**
     * 이벤트 수정
     */
    int update(ChargeBonusEvent event);

    /**
     * 이벤트 상태 변경
     */
    int updateStatus(@Param("eventSeq") Long eventSeq,
                     @Param("status") String status);

    /**
     * 이벤트 삭제
     */
    int delete(@Param("eventSeq") Long eventSeq);
}
