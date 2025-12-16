package kr.wisead.mapper.primary;

import kr.wisead.domain.payment.entity.StandardRate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 기준 단가 Mapper
 */
@Mapper
public interface StandardRateMapper {

    /**
     * 전체 기준 단가 목록 조회
     */
    List<StandardRate> selectStandardRates();

    /**
     * 서비스 ID로 기준 단가 조회
     */
    BigDecimal selectStandardRateByServiceId(@Param("serviceId") String serviceId);
}
