package kr.wisead.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

/**
 * 잔액 차감 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeductRequest {

    @NotBlank(message = "사용자 ID는 필수입니다.")
    private String userId;

    @NotBlank(message = "서비스 ID는 필수입니다.")
    private String serviceId;

    @NotNull(message = "수량은 필수입니다.")
    @Positive(message = "수량은 0보다 커야 합니다.")
    private BigDecimal quantity;

    private String comment;

    /**
     * 총 금액 계산
     */
    public BigDecimal calculateTotalAmount(BigDecimal unitPrice) {
        return unitPrice.multiply(quantity);
    }
}