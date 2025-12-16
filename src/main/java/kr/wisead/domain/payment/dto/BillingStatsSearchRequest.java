package kr.wisead.domain.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 과금 통계 검색 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BillingStatsSearchRequest {

    private String userId;              // 사용자 ID (특정 사용자 조회 시)
    private List<String> userIds;       // 사용자 ID 목록 (복수 사용자 조회 시)
    private String startDate;           // 시작일 (yyyy-MM-dd)
    private String endDate;             // 종료일 (yyyy-MM-dd)
    private String operation;           // 작업 유형 (P: 충전, M: 차감, R: 환불)
    private String serviceType;         // 서비스 타입 (SMS, LMS, MMS, SURVEY, QR 등)
}
