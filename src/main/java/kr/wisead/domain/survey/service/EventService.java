package kr.wisead.domain.survey.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.survey.dto.*;
import kr.wisead.domain.survey.entity.*;
import kr.wisead.mapper.primary.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 이벤트/설문 관리 Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final SurveyMasterMapper surveyMasterMapper;
    private final SurveyQuestionMapper surveyQuestionMapper;
    private final SurveyItemMapper surveyItemMapper;
    private final SurveyUserMapper surveyUserMapper;
    private final SurveyAnswerMapper surveyAnswerMapper;
    private final AuthUserMappingMapper authUserMappingMapper;

    /**
     * 이벤트 목록 조회 (페이징)
     */
    @Transactional(readOnly = true)
    public PageResponse<EventResponse> getEventList(EventSearchRequest request) {
        int total = surveyMasterMapper.selectCount(request);
        List<SurveyMaster> events = surveyMasterMapper.selectList(request);

        List<EventResponse> content = events.stream()
                .map(EventResponse::from)
                .collect(Collectors.toList());

        return PageResponse.of(content, request.getPageNum(), request.getAmount(), total);
    }

    /**
     * 이벤트 상세 조회
     */
    @Transactional(readOnly = true)
    public EventResponse getEventDetail(Integer eventSeq) {
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        EventResponse response = EventResponse.from(event);

        // 문항 목록 조회
        List<SurveyQuestion> questions = surveyQuestionMapper.selectByEventSeq(eventSeq);
        List<SurveyItem> allItems = surveyItemMapper.selectByEventSeq(eventSeq);

        // 문항 응답 변환
        List<QuestionResponse> questionResponses = questions.stream()
                .map(q -> {
                    QuestionResponse qr = QuestionResponse.from(q);
                    // 객관식인 경우 보기 추가
                    if (q.isMultipleChoice()) {
                        List<ItemResponse> items = allItems.stream()
                                .filter(item -> item.getQuestionSeq().equals(q.getQuestionSeq()))
                                .map(ItemResponse::from)
                                .collect(Collectors.toList());
                        qr = qr.withItems(items);
                    }
                    return qr;
                })
                .collect(Collectors.toList());

        return response.withQuestions(questionResponses);
    }

    /**
     * 이벤트 생성
     */
    @Transactional
    public EventResponse createEvent(Integer userSeq, EventRequest request, String regId) {
        // 이벤트 코드 생성
        String eventCode = generateEventCode();

        SurveyMaster event = SurveyMaster.builder()
                .userSeq(userSeq)
                .eventCode(eventCode)
                .eventName(request.getEventName())
                .eventEmphasisYn(request.getEventEmphasisYn())
                .eventDesc(request.getEventDesc())
                .eventType(request.getEventType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(request.getStatus() != null ? request.getStatus() : "A")
                .privacyPolicyYn(request.getPrivacyPolicyYn())
                .privacyPolicyTtl(request.getPrivacyPolicyTtl())
                .privacyPolicyDesc(request.getPrivacyPolicyDesc())
                .auth(request.getAuth())
                .qrCode(request.getQrCode())
                .endMessage(request.getEndMessage())
                .qrCodeVisits(0)
                .regId(regId)
                .build();

        surveyMasterMapper.insert(event);
        log.info("이벤트 생성 완료 - eventSeq: {}, eventCode: {}", event.getEventSeq(), eventCode);

        // 문항 등록
        if (request.getQuestions() != null && !request.getQuestions().isEmpty()) {
            saveQuestions(event.getEventSeq(), request.getQuestions(), regId);
        }

        return getEventDetail(event.getEventSeq());
    }

    /**
     * 이벤트 수정
     */
    @Transactional
    public EventResponse updateEvent(Integer eventSeq, EventRequest request, String uptId) {
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        event.update(
                request.getEventName(),
                request.getEventEmphasisYn(),
                request.getEventType(),
                request.getEventDesc(),
                request.getStartDate(),
                request.getEndDate(),
                request.getStatus(),
                request.getPrivacyPolicyYn(),
                request.getPrivacyPolicyTtl(),
                request.getPrivacyPolicyDesc(),
                request.getAuth(),
                request.getQrCode(),
                request.getEndMessage(),
                uptId
        );

        surveyMasterMapper.update(event);
        log.info("이벤트 수정 완료 - eventSeq: {}", eventSeq);

        // 문항 갱신 (기존 삭제 후 재등록)
        if (request.getQuestions() != null) {
            // 답변이 있는지 확인
            int answerCount = surveyAnswerMapper.countByEventSeq(eventSeq);
            if (answerCount > 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "응답이 있는 설문은 문항을 수정할 수 없습니다.");
            }

            surveyItemMapper.deleteByEventSeq(eventSeq);
            surveyQuestionMapper.deleteByEventSeq(eventSeq);
            saveQuestions(eventSeq, request.getQuestions(), uptId);
        }

        return getEventDetail(eventSeq);
    }

    /**
     * 이벤트 상태 변경
     */
    @Transactional
    public void updateEventStatus(Integer eventSeq, String status) {
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        surveyMasterMapper.updateStatus(eventSeq, status);
        log.info("이벤트 상태 변경 - eventSeq: {}, status: {}", eventSeq, status);
    }

    /**
     * 설문 통계 조회
     */
    @Transactional(readOnly = true)
    public SurveyStatisticsResponse getStatistics(Integer eventSeq) {
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        // 참여자 통계
        int totalParticipants = surveyUserMapper.countByEventSeq(eventSeq);
        int completedParticipants = surveyUserMapper.countCompletedByEventSeq(eventSeq);
        int absentees = surveyUserMapper.countAbsenteesByEventSeq(eventSeq);
        int lurkers = surveyUserMapper.countLurkersByEventSeq(eventSeq);

        Double responseRate = totalParticipants > 0
                ? (double) completedParticipants / totalParticipants * 100 : 0.0;

        // 문항별 통계
        List<SurveyQuestion> questions = surveyQuestionMapper.selectByEventSeq(eventSeq);
        List<SurveyItem> allItems = surveyItemMapper.selectByEventSeq(eventSeq);

        List<SurveyStatisticsResponse.QuestionStatistics> questionStats = questions.stream()
                .map(q -> {
                    int totalAnswers = surveyAnswerMapper.countByQuestionSeq(eventSeq, q.getQuestionSeq());

                    List<SurveyStatisticsResponse.ItemStatistics> itemStats = null;
                    if (q.isMultipleChoice()) {
                        itemStats = allItems.stream()
                                .filter(item -> item.getQuestionSeq().equals(q.getQuestionSeq()))
                                .map(item -> {
                                    int count;
                                    if (q.isMultiSelect()) {
                                        count = surveyAnswerMapper.countByItemValueMCM(
                                                eventSeq, q.getQuestionSeq(), item.getItemValue());
                                    } else {
                                        count = surveyAnswerMapper.countByItemSeq(
                                                eventSeq, q.getQuestionSeq(), item.getItemSeq());
                                    }
                                    double percentage = totalAnswers > 0
                                            ? (double) count / totalAnswers * 100 : 0.0;

                                    return SurveyStatisticsResponse.ItemStatistics.builder()
                                            .itemSeq(item.getItemSeq())
                                            .item(item.getItem())
                                            .itemValue(item.getItemValue())
                                            .count(count)
                                            .percentage(percentage)
                                            .build();
                                })
                                .collect(Collectors.toList());
                    }

                    return SurveyStatisticsResponse.QuestionStatistics.builder()
                            .questionSeq(q.getQuestionSeq())
                            .question(q.getQuestion())
                            .questionType(q.getQuestionType())
                            .totalAnswers(totalAnswers)
                            .itemStatistics(itemStats)
                            .build();
                })
                .collect(Collectors.toList());

        return SurveyStatisticsResponse.builder()
                .eventSeq(eventSeq)
                .eventName(event.getEventName())
                .totalParticipants(totalParticipants)
                .completedParticipants(completedParticipants)
                .absentees(absentees)
                .lurkers(lurkers)
                .responseRate(responseRate)
                .questionStatistics(questionStats)
                .build();
    }

    /**
     * 이벤트명 검색 (자동완성)
     */
    @Transactional(readOnly = true)
    public List<String> searchEventNames(EventSearchRequest request) {
        return surveyMasterMapper.searchEventNames(request);
    }

    /**
     * 범용인증키 목록 조회
     */
    @Transactional(readOnly = true)
    public PageResponse<Map<String, Object>> getAuthKeyList(Integer eventSeq, int page, int size) {
        int offset = (page - 1) * size;
        int total = authUserMappingMapper.countByEventSeq(eventSeq);
        List<Map<String, Object>> list = authUserMappingMapper.selectByEventSeq(eventSeq, offset, size);
        return PageResponse.of(list, page, size, total);
    }

    /**
     * 범용인증키 추가
     */
    @Transactional
    public void addAuthKey(Integer eventSeq, String authCode, String regId) {
        // 중복 확인
        if (authUserMappingMapper.checkDuplicateAuthCode(eventSeq, authCode) > 0) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "이미 등록된 인증코드입니다.");
        }

        // 사용자 키 생성
        String userKey = UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        // SurveyUser 등록
        SurveyUser user = SurveyUser.createForAuth(eventSeq, userKey, regId);
        surveyUserMapper.insert(user);

        // AuthUserMapping 등록
        AuthUserMapping mapping = AuthUserMapping.create(eventSeq, user.getSeq(), userKey, authCode, regId);
        authUserMappingMapper.insert(mapping);

        log.info("범용인증키 추가 - eventSeq: {}, authCode: {}", eventSeq, authCode);
    }

    /**
     * 범용인증키 삭제
     */
    @Transactional
    public void deleteAuthKey(Integer eventSeq, String userKey) {
        // 답변 확인
        if (authUserMappingMapper.checkHasAnswer(eventSeq, userKey) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "응답이 있는 인증키는 삭제할 수 없습니다.");
        }

        authUserMappingMapper.delete(eventSeq, userKey);
        surveyUserMapper.delete(eventSeq, userKey);

        log.info("범용인증키 삭제 - eventSeq: {}, userKey: {}", eventSeq, userKey);
    }

    /**
     * 문항 저장
     */
    private void saveQuestions(Integer eventSeq, List<QuestionRequest> questions, String regId) {
        int order = 1;
        for (QuestionRequest qReq : questions) {
            SurveyQuestion question = SurveyQuestion.create(
                    eventSeq,
                    qReq.getQuestionType(),
                    qReq.getQuestionTypeDetail(),
                    qReq.getQuestion(),
                    qReq.getOrder() != null ? qReq.getOrder() : order,
                    regId
            );
            if (qReq.getQuestionImg() != null) {
                question.setQuestionImg(qReq.getQuestionImg());
            }
            surveyQuestionMapper.insert(question);

            // 객관식 항목 저장
            if (qReq.getItems() != null && !qReq.getItems().isEmpty()) {
                int itemOrder = 1;
                for (ItemRequest iReq : qReq.getItems()) {
                    SurveyItem item = SurveyItem.create(
                            eventSeq,
                            question.getQuestionSeq(),
                            iReq.getItem(),
                            iReq.getItemValue(),
                            iReq.getOrder() != null ? iReq.getOrder() : itemOrder,
                            regId
                    );
                    if (iReq.getItemImg() != null) {
                        item.setItemImg(iReq.getItemImg());
                    }
                    if (iReq.getJumpQuestion() != null) {
                        item.setJumpQuestion(iReq.getJumpQuestion());
                    }
                    surveyItemMapper.insert(item);
                    itemOrder++;
                }
            }
            order++;
        }
    }

    /**
     * 이벤트 코드 생성
     */
    private String generateEventCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    /**
     * 만료된 이벤트 상태 업데이트 (배치용)
     */
    @Transactional
    public int updateExpiredEventsStatus() {
        return surveyMasterMapper.updateExpiredEventsStatus();
    }
}
