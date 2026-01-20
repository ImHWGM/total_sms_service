package kr.wisead.domain.company.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 고객사 검색 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerCompanySearchRequest {

    private String type;            // 검색 타입 (seqOpt, custCompNameOpt, custCompBizCodeOpt)
    private String keyword;         // 검색 키워드
    private int pageNum;            // 페이지 번호 (1부터 시작)
    private int amount;             // 페이지당 개수
    private Long excludeSeq;        // 제외할 사용자 SEQ (본인 제외용)

    public int getSkip() {
        return (pageNum - 1) * amount;
    }

    public static CustomerCompanySearchRequest of(int pageNum, int amount) {
        return CustomerCompanySearchRequest.builder()
                .pageNum(pageNum)
                .amount(amount)
                .build();
    }
}
