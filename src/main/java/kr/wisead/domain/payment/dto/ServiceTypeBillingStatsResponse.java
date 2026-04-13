package kr.wisead.domain.payment.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 서비스 타입별 과금 통계 응답 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceTypeBillingStatsResponse {

  private String serviceType; // 서비스 타입 (SMS, LMS, MMS, SURVEY, QR, QR_EXTRA)
  private String serviceTypeName; // 서비스 타입명
  private int transactionCount; // 발송 성공 건수
  private BigDecimal unitPrice; // 표준 단가 (VAT 포함)
  private BigDecimal totalAmount; // 총 사용료
  private String remarks; // 비고

  /** 서비스 타입 -> 서비스 타입명 변환 */
  public static String getServiceTypeName(String serviceType) {
    if (serviceType == null) return "기타";
    return switch (serviceType) {
      case "SMS" -> "SMS";
      case "LMS" -> "LMS";
      case "MMS" -> "MMS";
      case "KAKAO" -> "카카오";
      case "SURVEY" -> "설문조사";
      case "QR" -> "QR코드";
      case "CHARGE" -> "충전";
      case "REFUND" -> "환불";
      case "ETC" -> "기타";
      default -> serviceType;
    };
  }
}
