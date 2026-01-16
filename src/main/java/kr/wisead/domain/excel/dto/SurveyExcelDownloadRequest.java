package kr.wisead.domain.excel.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 설문조사/개인정보취합 Excel 다운로드 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
public class SurveyExcelDownloadRequest {

    /**
     * 다운로드 유형
     * - ALL: 전체 데이터 다운로드
     * - SEARCH: 검색 조건에 맞는 데이터 다운로드
     * - SELECTED: 선택된 데이터만 다운로드
     */
    private DownloadType downloadType = DownloadType.SEARCH;

    /**
     * 이벤트 타입 (S: 설문조사, P: 개인정보취합)
     */
    private String eventType;

    // === 검색 조건 (SEARCH 모드용) ===
    private Integer eventSeq;
    private String keyword;
    private String searchType;
    private String submissionStatus;
    private String startDate;
    private String endDate;

    // === 선택된 항목 (SELECTED 모드용) ===
    private List<Integer> selectedSeqs;

    // === 공통 ===
    private String reason;

    public enum DownloadType {
        ALL,      // 전체 다운로드
        SEARCH,   // 검색 결과 다운로드
        SELECTED  // 선택 항목 다운로드
    }
}