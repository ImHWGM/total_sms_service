package kr.wisead.domain.history.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 발송 이력 검색 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendHistorySearchRequest {

    private String startDate;       // 검색 시작일 (yyyy-MM-dd)
    private String endDate;         // 검색 종료일 (yyyy-MM-dd)
    private String type;            // 검색 타입 (dstAddr, callBack, subject, text)
    private String keyword;         // 검색 키워드
    private String sendFailure;     // 실패만 조회 (Y/N)
    private String userId;          // 사용자 ID (권한에 따라 설정)

    // 페이징
    @Builder.Default
    private int page = 1;
    @Builder.Default
    private int size = 10;

    public int getOffset() {
        return (page - 1) * size;
    }
}
