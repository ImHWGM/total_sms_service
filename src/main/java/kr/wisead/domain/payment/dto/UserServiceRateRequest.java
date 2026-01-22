package kr.wisead.domain.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 사용자별 서비스 요금 설정 요청 DTO */
public record UserServiceRateRequest(
    @NotNull(message = "사용자 seq는 필수입니다") Integer userSeq,
    @NotEmpty(message = "요금 목록은 필수입니다") @Valid List<RateEntry> rates,
    boolean vatIncluded) {
  /** 개별 요금 항목 */
  public record RateEntry(
      @NotBlank(message = "서비스 ID는 필수입니다") String serviceId,
      @NotNull(message = "단가는 필수입니다") @Positive(message = "단가는 0보다 커야 합니다") BigDecimal rate,
      LocalDate startDate,
      LocalDate endDate) {}
}
