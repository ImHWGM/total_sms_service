package kr.wisead.domain.survey.controller;

import kr.wisead.common.response.ApiResponse;
import kr.wisead.domain.survey.dto.AnswerResponse;
import kr.wisead.domain.survey.dto.AnswerStatisticsResponse;
import kr.wisead.domain.survey.service.SurveyAnswerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 설문 답변 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/survey/answers")
@RequiredArgsConstructor
public class SurveyAnswerController {

    private final SurveyAnswerService surveyAnswerService;

    /**
     * 이벤트별 답변 수 조회
     * GET /api/survey/answers/count?eventSeq=1
     */
    @GetMapping("/count")
    public ApiResponse<Integer> getAnswerCount(@RequestParam Integer eventSeq) {
        int count = surveyAnswerService.getAnswerCount(eventSeq);
        return ApiResponse.success(count);
    }

    /**
     * 이벤트별 전체 답변 목록 조회
     * GET /api/survey/answers?eventSeq=1
     */
    @GetMapping
    public ApiResponse<List<AnswerResponse>> getAnswersByEvent(@RequestParam Integer eventSeq) {
        List<AnswerResponse> answers = surveyAnswerService.getAnswersByEvent(eventSeq);
        return ApiResponse.success(answers);
    }

    /**
     * 사용자별 답변 조회
     * GET /api/survey/answers/user?eventSeq=1&userSeq=1
     */
    @GetMapping("/user")
    public ApiResponse<List<AnswerResponse>> getAnswersByUser(
            @RequestParam Integer eventSeq,
            @RequestParam Integer userSeq) {
        List<AnswerResponse> answers = surveyAnswerService.getAnswersByUser(eventSeq, userSeq);
        return ApiResponse.success(answers);
    }

    /**
     * 문항별 답변 조회
     * GET /api/survey/answers/question?eventSeq=1&questionSeq=1
     */
    @GetMapping("/question")
    public ApiResponse<List<AnswerResponse>> getAnswersByQuestion(
            @RequestParam Integer eventSeq,
            @RequestParam Integer questionSeq) {
        List<AnswerResponse> answers = surveyAnswerService.getAnswersByQuestion(eventSeq, questionSeq);
        return ApiResponse.success(answers);
    }

    /**
     * 문항별 응답 통계 조회
     * GET /api/survey/answers/statistics/question?eventSeq=1&questionSeq=1
     */
    @GetMapping("/statistics/question")
    public ApiResponse<AnswerStatisticsResponse> getQuestionStatistics(
            @RequestParam Integer eventSeq,
            @RequestParam Integer questionSeq) {
        AnswerStatisticsResponse statistics = surveyAnswerService.getQuestionStatistics(eventSeq, questionSeq);
        return ApiResponse.success(statistics);
    }

    /**
     * 이벤트 전체 문항 통계 조회
     * GET /api/survey/answers/statistics?eventSeq=1
     */
    @GetMapping("/statistics")
    public ApiResponse<List<AnswerStatisticsResponse>> getEventStatistics(@RequestParam Integer eventSeq) {
        List<AnswerStatisticsResponse> statistics = surveyAnswerService.getEventStatistics(eventSeq);
        return ApiResponse.success(statistics);
    }
}
