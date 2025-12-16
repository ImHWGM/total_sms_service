package kr.wisead.domain.survey.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 설문 답변 통계 응답 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnswerStatisticsResponse {

    private Integer eventSeq;
    private Integer questionSeq;
    private String questionType;
    private String questionTypeDetail;
    private int respondentCount;

    // 객관식 문항인 경우 항목별 통계
    private List<ItemStatistics> itemStatistics;

    // 주관식 문항인 경우 답변 목록
    private List<String> textAnswers;

    /**
     * 항목별 통계
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ItemStatistics {
        private Integer itemSeq;
        private String itemValue;
        private String itemName;
        private int selectCount;
        private double percentage;
    }
}
