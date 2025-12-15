package kr.wisead.domain.excel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Excel 내보내기 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExcelExportRequest {

    private String fileName;            // 파일명
    private String sheetName;           // 시트명
    private List<String> headers;       // 헤더 목록
    private String exportType;          // 내보내기 타입 (STATISTICS, BILLING, SURVEY_RESULT 등)

    // 검색 조건
    private String startDate;
    private String endDate;
    private Integer userId;
    private String serviceType;
    private String msgType;
    private Integer eventSeq;
}
