package kr.wisead.domain.statistics.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 통계 검색 조건 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatsSearchRequest {

  private String serviceType; // 서비스 타입 (SMS, LMS, MMS, KAKAO 등)
  private String msgType; // 메시지 타입
  private String userId; // 사용자 ID (단일)
  private List<String> userIds; // 사용자 ID 목록
  private String startDate; // 시작일 (YYYY-MM-DD)
  private String endDate; // 종료일 (YYYY-MM-DD)
  private String groupBy; // 그룹핑 기준 (daily, monthly, yearly)

  // 페이징
  private int page;
  private int size;

  // 정렬
  private String sortBy;
  private String sortDirection;

  /** 시작월 (YYYYMM 형식) */
  public String getStartMonth() {
    if (startDate != null && startDate.length() >= 7) {
      return startDate.substring(0, 4) + startDate.substring(5, 7);
    }
    return null;
  }

  /** 종료월 (YYYYMM 형식) */
  public String getEndMonth() {
    if (endDate != null && endDate.length() >= 7) {
      return endDate.substring(0, 4) + endDate.substring(5, 7);
    }
    return null;
  }
}
