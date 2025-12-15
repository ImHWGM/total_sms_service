package kr.wisead.domain.survey.dto;

import lombok.*;

import java.util.List;

/**
 * 설문 통계 응답 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SurveyStatisticsResponse {

    private Integer eventSeq;               // 이벤트 시퀀스
    private String eventName;               // 이벤트 명

    // 참여자 통계
    private Integer totalParticipants;      // 전체 대상자 수
    private Integer completedParticipants;  // 설문 완료자 수
    private Integer absentees;              // 미참여자 수
    private Integer lurkers;                // 접속만 한 참여자 수
    private Double responseRate;            // 응답률

    // 문자 발송 통계
    private Integer totalMessagesSent;      // 전체 발송 수
    private Integer successMessagesSent;    // 발송 성공 수

    // 문항별 통계
    private List<QuestionStatistics> questionStatistics;

    /**
     * 문항 통계
     */
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Builder
    public static class QuestionStatistics {
        private Integer questionSeq;        // 문항 시퀀스
        private String question;            // 문항 내용
        private String questionType;        // 문항 종류
        private Integer totalAnswers;       // 전체 응답 수

        // 객관식 항목별 통계
        private List<ItemStatistics> itemStatistics;
    }

    /**
     * 항목 통계
     */
    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    @Builder
    public static class ItemStatistics {
        private Integer itemSeq;            // 항목 시퀀스
        private String item;                // 항목 내용
        private String itemValue;           // 항목 값
        private Integer count;              // 선택 수
        private Double percentage;          // 선택 비율
    }
}
