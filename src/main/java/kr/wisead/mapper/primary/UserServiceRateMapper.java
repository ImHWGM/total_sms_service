package kr.wisead.mapper.primary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import kr.wisead.domain.payment.entity.UserServiceRate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 사용자별 서비스 단가 Mapper */
@Mapper
public interface UserServiceRateMapper {

  /** 단가 등록 */
  int insert(UserServiceRate rate);

  /** 현재 유효한 단가 조회 */
  Optional<UserServiceRate> selectActiveRate(
      @Param("userSeq") Integer userSeq,
      @Param("serviceId") String serviceId,
      @Param("today") LocalDate today);

  /** 사용자의 모든 유효한 단가 조회 */
  List<UserServiceRate> selectAllActiveRates(
      @Param("userSeq") Integer userSeq, @Param("today") LocalDate today);

  /** 단가 이력 조회 */
  List<UserServiceRate> selectHistory(
      @Param("userSeq") Integer userSeq, @Param("serviceId") String serviceId);

  /** 종료일 업데이트 (이력 관리) */
  int updateEndDate(@Param("seq") Long seq, @Param("endDate") LocalDate endDate);

  /** 단가 삭제 */
  int delete(@Param("seq") Long seq);

  /** 사용자 단가 조회 (없으면 NULL) - VAT 포함된 값 */
  BigDecimal selectUserRate(
      @Param("userSeq") Integer userSeq,
      @Param("serviceId") String serviceId,
      @Param("today") LocalDate today);

  /** 기준 단가 조회 (VAT 미포함) */
  BigDecimal selectStandardRate(@Param("serviceId") String serviceId);

  /** 모든 기준 단가 조회 (VAT 미포함) - N+1 최적화: 여러 서비스의 단가를 한 번에 조회 */
  List<ServiceRateEntry> selectAllStandardRates();

  /** 영구 요금 중 가장 최근 것 조회 (end_date IS NULL) */
  Optional<UserServiceRate> selectLatestPermanentRate(
      @Param("userSeq") Integer userSeq, @Param("serviceId") String serviceId);

  /** 기간 특별요금 조회 (오늘 기준으로 유효한 것) */
  Optional<UserServiceRate> selectPeriodRate(
      @Param("userSeq") Integer userSeq,
      @Param("serviceId") String serviceId,
      @Param("today") LocalDate today);

  /** 서비스 단가 DTO */
  record ServiceRateEntry(String serviceId, java.math.BigDecimal rate) {}
}
