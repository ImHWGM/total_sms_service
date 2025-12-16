package kr.wisead.domain.payment.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 기준 단가 Entity
 * standard_rate 테이블 매핑
 *
 * service_id 종류:
 * - msg_sms: SMS 단문(90Byte)
 * - msg_lms: LMS 장문(2,000Byte)
 * - msg_mms: MMS 단/장문 + 이미지
 * - coupon_sms/lms/mms: 쿠폰 메시지
 * - msg_kc: KC 본인인증
 * - survey: 모바일설문조사
 * - qr_code: QR코드
 * - qr_code_extra: QR코드 추가과금
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StandardRate {

    private Integer serviceSeq;         // 서비스 시퀀스
    private String serviceId;           // 서비스 ID
    private String serviceName;         // 서비스 명
    private BigDecimal serviceRate;     // 서비스 단가
    private String serviceDescription;  // 서비스 설명
}
