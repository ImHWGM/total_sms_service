package kr.wisead.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 액션 로그 검색 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionLogSearchRequest {

    private String startDate;        // 검색 시작일 (yyyy-MM-dd)
    private String endDate;          // 검색 종료일 (yyyy-MM-dd)
    private String searchField;      // 검색 필드 (logId, logName, logMenu)
    private String searchKeyword;    // 검색 키워드
    private String actionType;       // 액션 타입 필터

    // 페이징
    @Builder.Default
    private int page = 1;
    @Builder.Default
    private int size = 20;

    public int getOffset() {
        return (page - 1) * size;
    }
}
