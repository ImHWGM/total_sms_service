package kr.wisead.domain.survey.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.survey.dto.AnswerResponse;
import kr.wisead.domain.survey.dto.AnswerStatisticsResponse;
import kr.wisead.domain.survey.entity.OtherType;
import kr.wisead.domain.survey.entity.SurveyAnswer;
import kr.wisead.domain.survey.entity.SurveyItem;
import kr.wisead.domain.survey.entity.SurveyQuestion;
import kr.wisead.mapper.primary.SurveyAnswerMapper;
import kr.wisead.mapper.primary.SurveyItemMapper;
import kr.wisead.mapper.primary.SurveyQuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 설문 답변 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SurveyAnswerService {

    private final SurveyAnswerMapper surveyAnswerMapper;
    private final SurveyQuestionMapper surveyQuestionMapper;
    private final SurveyItemMapper surveyItemMapper;

    /**
     * 이벤트별 답변 수 조회
     */
    @Transactional(readOnly = true)
    public int getAnswerCount(Integer eventSeq) {
        return surveyAnswerMapper.countByEventSeq(eventSeq);
    }

    /**
     * 이벤트별 전체 답변 목록 조회
     */
    @Transactional(readOnly = true)
    public List<AnswerResponse> getAnswersByEvent(Integer eventSeq) {
        List<SurveyAnswer> answers = surveyAnswerMapper.selectByEventSeq(eventSeq);
        decryptAnswers(eventSeq, answers);
        return answers.stream()
                .map(AnswerResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 사용자별 답변 목록 조회
     */
    @Transactional(readOnly = true)
    public List<AnswerResponse> getAnswersByUser(Integer eventSeq, Integer userSeq) {
        List<SurveyAnswer> answers = surveyAnswerMapper.selectByUserSeq(eventSeq, userSeq);
        decryptAnswers(eventSeq, answers);
        return answers.stream()
                .map(AnswerResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 문항별 답변 목록 조회
     */
    @Transactional(readOnly = true)
    public List<AnswerResponse> getAnswersByQuestion(Integer eventSeq, Integer questionSeq) {
        List<SurveyAnswer> answers = surveyAnswerMapper.selectByQuestionSeq(eventSeq, questionSeq);
        decryptAnswers(eventSeq, answers);
        return answers.stream()
                .map(AnswerResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 답변 복호화 후처리 — ANSWER(주관식 PII) + OTHER_TEXT(기타 PII) 모두 복원.
     */
    private void decryptAnswers(Integer eventSeq, List<SurveyAnswer> answers) {
        decryptAnswerPii(answers);
        decryptOtherTextPii(eventSeq, answers);
    }

    /** 주관식 ANSWER 컬럼의 PII 유형(NE/AD/CU/EM/SO) 복호화. detail은 답변 row 자체에 존재. */
    private void decryptAnswerPii(List<SurveyAnswer> answers) {
        if (answers == null || answers.isEmpty()) {
            return;
        }
        for (SurveyAnswer a : answers) {
            String raw = a.getAnswer();
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            a.setAnswer(OtherTextCrypto.decryptForDisplay(a.getQuestionTypeDetail(), raw));
        }
    }

    /**
     * 기타답변 OTHER_TEXT PII 유형별 복호화 후처리.
     *
     * <p>{@link SurveyService#resolveOtherTextForStorage}가 SO/NE/AD/CU/EM 기타답변을 암호화 저장하므로
     * (SA는 평문), API 응답 시 평문으로 복원해 노출한다. 한 문항당 isOther 항목은 1개 (spec R5)이므로
     * questionSeq → OtherType lookup으로 단순화. 복호화/접두사 매칭 실패 시 raw 보존 (기존 평문 데이터 + RSA fallback 호환).
     */
    private void decryptOtherTextPii(Integer eventSeq, List<SurveyAnswer> answers) {
        if (answers == null || answers.isEmpty()) {
            return;
        }
        Map<Integer, OtherType> otherTypeByQuestion =
                surveyItemMapper.selectByEventSeq(eventSeq).stream()
                        .filter(SurveyItem::isOther)
                        .collect(Collectors.toMap(
                                SurveyItem::getQuestionSeq,
                                i -> i.getOtherType() != null ? i.getOtherType() : OtherType.SA,
                                (a, b) -> a));
        for (SurveyAnswer a : answers) {
            String raw = a.getOtherText();
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            OtherType type = otherTypeByQuestion.get(a.getQuestionSeq());
            if (type == null) {
                continue;
            }
            a.setOtherText(OtherTextCrypto.decryptForDisplay(type.name(), raw));
        }
    }

    /**
     * 답변 등록
     */
    @Transactional
    public int insertAnswer(SurveyAnswer answer) {
        return surveyAnswerMapper.insert(answer);
    }

    /**
     * 답변 일괄 등록
     */
    @Transactional
    public int insertAnswerBatch(List<SurveyAnswer> answers) {
        if (answers == null || answers.isEmpty()) {
            return 0;
        }
        return surveyAnswerMapper.insertBatch(answers);
    }

    /**
     * 사용자의 답변 삭제 (재제출용)
     */
    @Transactional
    public int deleteUserAnswers(Integer eventSeq, Integer userSeq) {
        return surveyAnswerMapper.deleteByUserSeq(eventSeq, userSeq);
    }

    /**
     * 답변 존재 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean hasAnswer(Integer eventSeq, Integer userSeq, Integer questionSeq) {
        return surveyAnswerMapper.checkAnswerExists(eventSeq, userSeq, questionSeq) > 0;
    }

    /**
     * 문항별 응답 통계 조회
     */
    @Transactional(readOnly = true)
    public AnswerStatisticsResponse getQuestionStatistics(Integer eventSeq, Integer questionSeq) {
        // 문항 정보 조회
        SurveyQuestion question = surveyQuestionMapper.selectByQuestionSeq(questionSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "문항을 찾을 수 없습니다."));

        // 응답자 수
        int respondentCount = surveyAnswerMapper.countByQuestionSeq(eventSeq, questionSeq);

        // 문항 타입에 따른 통계 처리
        String questionType = question.getQuestionType();
        String questionTypeDetail = question.getQuestionTypeDetail();

        AnswerStatisticsResponse.AnswerStatisticsResponseBuilder builder = AnswerStatisticsResponse.builder()
                .eventSeq(eventSeq)
                .questionSeq(questionSeq)
                .questionType(questionType)
                .questionTypeDetail(questionTypeDetail)
                .respondentCount(respondentCount);

        // 객관식 문항인 경우 항목별 통계
        if ("MC".equals(questionType)) {
            List<SurveyItem> items = surveyItemMapper.selectByQuestionSeq(eventSeq, questionSeq);
            List<AnswerStatisticsResponse.ItemStatistics> itemStats = new ArrayList<>();

            for (SurveyItem item : items) {
                int selectCount;
                if ("MCM".equals(questionTypeDetail)) {
                    // 복수 선택인 경우
                    selectCount = surveyAnswerMapper.countByItemValueMCM(eventSeq, questionSeq, item.getItemValue());
                } else {
                    // 단일 선택인 경우
                    selectCount = surveyAnswerMapper.countByItemSeq(eventSeq, questionSeq, item.getItemSeq());
                }

                double percentage = respondentCount > 0 ? (selectCount * 100.0 / respondentCount) : 0;

                itemStats.add(AnswerStatisticsResponse.ItemStatistics.builder()
                        .itemSeq(item.getItemSeq())
                        .itemValue(item.getItemValue())
                        .itemName(item.getItem())
                        .selectCount(selectCount)
                        .percentage(Math.round(percentage * 10) / 10.0)
                        .build());
            }

            builder.itemStatistics(itemStats);
        } else {
            // 주관식인 경우 답변 목록 조회
            List<SurveyAnswer> answers = surveyAnswerMapper.selectByQuestionSeq(eventSeq, questionSeq);
            List<String> textAnswers = answers.stream()
                    .map(a -> OtherTextCrypto.decryptForDisplay(
                            a.getQuestionTypeDetail(), a.getAnswer()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            builder.textAnswers(textAnswers);
        }

        return builder.build();
    }

    /**
     * 이벤트 전체 문항 통계 조회
     */
    @Transactional(readOnly = true)
    public List<AnswerStatisticsResponse> getEventStatistics(Integer eventSeq) {
        List<SurveyQuestion> questions = surveyQuestionMapper.selectByEventSeq(eventSeq);
        List<AnswerStatisticsResponse> statistics = new ArrayList<>();

        for (SurveyQuestion question : questions) {
            try {
                AnswerStatisticsResponse stat = getQuestionStatistics(eventSeq, question.getQuestionSeq());
                statistics.add(stat);
            } catch (Exception e) {
                log.warn("문항 통계 조회 실패: eventSeq={}, questionSeq={}", eventSeq, question.getQuestionSeq(), e);
            }
        }

        return statistics;
    }
}
