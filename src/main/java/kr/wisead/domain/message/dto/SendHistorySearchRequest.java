package kr.wisead.domain.message.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 발송 이력 검색 요청 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SendHistorySearchRequest {

    private String regId;             // 등록자 ID (EXT_COL3), "ALL"이면 전체
    private LocalDateTime srhDateStart; // 검색 시작일
    private LocalDateTime srhDateEnd;   // 검색 종료일
    private String type;              // 검색 타입 (A~H)
    private String keyword;           // 검색 키워드
    private String sendFailure;       // 실패만 조회 ("1": 실패만)

    private int pageNum = 1;          // 현재 페이지
    private int amount = 20;          // 페이지당 건수

    @Builder
    public SendHistorySearchRequest(String regId, LocalDateTime srhDateStart, LocalDateTime srhDateEnd,
                                     String type, String keyword, String sendFailure,
                                     int pageNum, int amount) {
        this.regId = regId;
        this.srhDateStart = srhDateStart;
        this.srhDateEnd = srhDateEnd;
        this.type = type;
        this.keyword = keyword;
        this.sendFailure = sendFailure;
        this.pageNum = pageNum > 0 ? pageNum : 1;
        this.amount = amount > 0 ? amount : 20;
    }

    /**
     * 스킵 건수 계산
     */
    public int getSkip() {
        return (pageNum - 1) * amount;
    }

    /**
     * 검색 기간의 월별 테이블명 목록 생성
     * 예: 2025-11-15 ~ 2025-12-15 => [msg_result_202511, msg_result_202512]
     */
    public List<String> getTableNames() {
        List<String> tables = new ArrayList<>();

        if (srhDateStart == null || srhDateEnd == null) {
            // 날짜가 없으면 현재 월만
            tables.add("msg_result_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM")));
            return tables;
        }

        YearMonth start = YearMonth.from(srhDateStart);
        YearMonth end = YearMonth.from(srhDateEnd);

        YearMonth current = start;
        while (!current.isAfter(end)) {
            tables.add("msg_result_" + current.format(DateTimeFormatter.ofPattern("yyyyMM")));
            current = current.plusMonths(1);
        }

        return tables;
    }

    /**
     * 검색 타입 설명
     * A: 메시지타입, B: 수신번호, C: 발신번호, D: 결과,
     * E: 제목, F: 내용, G: 상태, H: 등록자
     */
    public String getTypeDescription() {
        return switch (type) {
            case "A" -> "메시지타입";
            case "B" -> "수신번호";
            case "C" -> "발신번호";
            case "D" -> "결과";
            case "E" -> "제목";
            case "F" -> "내용";
            case "G" -> "상태";
            case "H" -> "등록자";
            default -> type;
        };
    }
}
