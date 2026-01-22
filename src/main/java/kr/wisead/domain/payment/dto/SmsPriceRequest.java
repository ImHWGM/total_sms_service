package kr.wisead.domain.payment.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 문자 요금 설정 요청 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsPriceRequest {

  /** 대상 사용자 시퀀스 */
  private Integer userSeq;

  /** SMS 단가 */
  private BigDecimal smsPrice;

  /** LMS 단가 */
  private BigDecimal lmsPrice;

  /** MMS 단가 */
  private BigDecimal mmsPrice;
}
