package kr.wisead.domain.survey.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.util.CommonUtils;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.common.util.QrCodeUtils;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.excel.service.ExcelService;
import kr.wisead.domain.file.service.FileStorageService;
import kr.wisead.domain.survey.dto.*;
import kr.wisead.domain.survey.entity.*;
import kr.wisead.mapper.primary.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.format.DateTimeFormatter;
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
    private final UserMapper userMapper;
    private final ExcelService excelService;
    private final AdminService adminService;
    private final FileStorageService fileStorageService;

    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    @Value("${qr.url:http://localhost:8100/files/qrcode}")
    private String qrUrl;

    @Value("${wisead.url:http://localhost:3100}")
    private String wiseadUrl;

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
     * 이벤트 생성 (이미지 파일 포함)
     * - 설문 데이터와 이미지를 한번에 처리 (레거시 방식)
     */
    @Transactional
    public EventResponse createEvent(String userId, EventRequest request,
                                      MultipartFile descImageFile, MultipartFile endImageFile,
                                      List<MultipartFile> questionImages, List<MultipartFile> itemImages) {
        // 사용자 조회하여 userSeq 획득
        Integer userSeq = userMapper.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND, "사용자를 찾을 수 없습니다."))
                .getSeq()
                .intValue();

        // 이벤트 코드 생성
        String eventCode = generateEventCode();

        // QR 간편인증 사용 시 authCodeUrl과 QR 이미지 생성
        String authCodeUrl = null;
        String qrCodeImgPath = null;
        if ("Y".equals(request.getQrCode())) {
            authCodeUrl = CommonUtils.randomCode(20);
            qrCodeImgPath = generateQrCodeImage(authCodeUrl);
        }

        SurveyMaster event = SurveyMaster.builder()
                .userSeq(userSeq)
                .eventCode(eventCode)
                .eventName(request.getEventName())
                .eventEmphasisYn(request.getEventEmphasisYn())
                .eventDesc(request.getEventDesc())
                .eventDescImg(request.getEventDescImg())
                .eventType(request.getEventType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(request.getStatus() != null ? request.getStatus() : "A")
                .privacyPolicyYn(request.getPrivacyPolicyYn())
                .privacyPolicyTtl(request.getPrivacyPolicyTtl())
                .privacyPolicyDesc(request.getPrivacyPolicyDesc())
                .auth(request.getAuth())
                .qrCode(request.getQrCode())
                .authCodeUrl(authCodeUrl)
                .qrCodeImgPath(qrCodeImgPath)
                .endMessage(request.getEndMessage())
                .eventEndImg(request.getEventEndImg())
                .regId(userId)
                .build();

        surveyMasterMapper.insert(event);
        log.info("이벤트 생성 완료 - eventSeq: {}, eventCode: {}, qrCode: {}", event.getEventSeq(), eventCode, request.getQrCode());

        Integer eventSeq = event.getEventSeq();
        String eventSeqStr = String.valueOf(eventSeq);

        // 설명 이미지 저장 (MultipartFile)
        if (descImageFile != null && !descImageFile.isEmpty()) {
            String descImgPath = fileStorageService.storeSurveyDescImg(descImageFile, eventSeqStr);
            surveyMasterMapper.updateDescImg(eventSeq, descImgPath);
            log.info("설명 이미지 저장 완료 - eventSeq: {}, path: {}", eventSeq, descImgPath);
        }

        // 종료 이미지 저장 (MultipartFile)
        if (endImageFile != null && !endImageFile.isEmpty()) {
            String endImgPath = fileStorageService.storeSurveyEndImg(endImageFile, eventSeqStr);
            surveyMasterMapper.updateEndImg(eventSeq, endImgPath);
            log.info("종료 이미지 저장 완료 - eventSeq: {}, path: {}", eventSeq, endImgPath);
        }

        // 문항 등록 및 이미지 저장
        if (request.getQuestions() != null && !request.getQuestions().isEmpty()) {
            saveQuestionsWithImages(eventSeq, request.getQuestions(), userId, questionImages, itemImages);
        }

        return getEventDetail(eventSeq);
    }

    /**
     * 이벤트 수정 (이미지 파일 포함)
     * - 설문 데이터와 이미지를 한번에 처리 (레거시 방식)
     */
    @Transactional
    public EventResponse updateEvent(Integer eventSeq, EventRequest request, String uptId,
                                       MultipartFile descImageFile, MultipartFile endImageFile,
                                       List<MultipartFile> questionImages, List<MultipartFile> itemImages) {
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        // 권한 체크: 이벤트 소유자 또는 A레벨만 수정 가능
        Integer userLevel = adminService.getUserLevel(uptId);
        adminService.validateModifyPermission(uptId, userLevel, event.getRegId());

        // QR 간편인증 사용으로 변경되었고, 기존에 authCodeUrl이 없으면 새로 생성
        if ("Y".equals(request.getQrCode()) &&
                (event.getAuthCodeUrl() == null || event.getAuthCodeUrl().isEmpty())) {
            String authCodeUrl = CommonUtils.randomCode(20);
            String qrCodeImgPath = generateQrCodeImage(authCodeUrl);
            event.setQrCodeInfo(qrCodeImgPath, authCodeUrl);
            log.info("QR 코드 생성 - eventSeq: {}, authCodeUrl: {}", eventSeq, authCodeUrl);
        }

        String eventSeqStr = String.valueOf(eventSeq);

        // 설명 이미지: 새 파일이 있으면 저장, 없으면 기존 경로 유지
        String eventDescImg = event.getEventDescImg();
        if (descImageFile != null && !descImageFile.isEmpty()) {
            eventDescImg = fileStorageService.storeSurveyDescImg(descImageFile, eventSeqStr);
            log.info("설명 이미지 저장 완료 - eventSeq: {}, path: {}", eventSeq, eventDescImg);
        }

        // 종료 이미지: 새 파일이 있으면 저장, 없으면 기존 경로 유지
        String eventEndImg = event.getEventEndImg();
        if (endImageFile != null && !endImageFile.isEmpty()) {
            eventEndImg = fileStorageService.storeSurveyEndImg(endImageFile, eventSeqStr);
            log.info("종료 이미지 저장 완료 - eventSeq: {}, path: {}", eventSeq, eventEndImg);
        }

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
                eventDescImg,
                eventEndImg,
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
            saveQuestionsWithImages(eventSeq, request.getQuestions(), uptId, questionImages, itemImages);
        }

        return getEventDetail(eventSeq);
    }

    /**
     * 이벤트 상태 변경
     */
    @Transactional
    public void updateEventStatus(Integer eventSeq, String status, String userId) {
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        // 권한 체크: 이벤트 소유자 또는 A레벨만 수정 가능
        Integer userLevel = adminService.getUserLevel(userId);
        adminService.validateModifyPermission(userId, userLevel, event.getRegId());

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
        // 이벤트 존재 확인 및 권한 체크
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        Integer userLevel = adminService.getUserLevel(regId);
        adminService.validateModifyPermission(regId, userLevel, event.getRegId());

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
    public void deleteAuthKey(Integer eventSeq, String userKey, String userId) {
        // 이벤트 존재 확인 및 권한 체크
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        Integer userLevel = adminService.getUserLevel(userId);
        adminService.validateModifyPermission(userId, userLevel, event.getRegId());

        // 답변 확인
        if (authUserMappingMapper.checkHasAnswer(eventSeq, userKey) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "응답이 있는 인증키는 삭제할 수 없습니다.");
        }

        authUserMappingMapper.delete(eventSeq, userKey);
        surveyUserMapper.delete(eventSeq, userKey);

        log.info("범용인증키 삭제 - eventSeq: {}, userKey: {}", eventSeq, userKey);
    }

    /**
     * 문항 저장 (이미지 포함)
     * - 파일명 규칙: questionImages는 "{questionOrder}.{ext}", itemImages는 "{questionOrder}_{itemOrder}.{ext}"
     */
    private void saveQuestionsWithImages(Integer eventSeq, List<QuestionRequest> questions, String regId,
                                          List<MultipartFile> questionImages, List<MultipartFile> itemImages) {
        String eventSeqStr = String.valueOf(eventSeq);

        // 문항 이미지 맵 구성 (파일명에서 순번 추출)
        Map<Integer, MultipartFile> questionImageMap = new HashMap<>();
        if (questionImages != null) {
            for (MultipartFile file : questionImages) {
                if (file != null && !file.isEmpty()) {
                    String fileName = file.getOriginalFilename();
                    if (fileName != null) {
                        try {
                            // 파일명에서 숫자 추출 (예: "1.png" → 1)
                            String numPart = fileName.replaceAll("[^0-9]", "");
                            if (!numPart.isEmpty()) {
                                int questionOrder = Integer.parseInt(numPart);
                                questionImageMap.put(questionOrder, file);
                            }
                        } catch (NumberFormatException e) {
                            log.warn("문항 이미지 파일명에서 순번 추출 실패: {}", fileName);
                        }
                    }
                }
            }
        }

        // 항목 이미지 맵 구성 (파일명: "{questionOrder}_{itemOrder}.{ext}")
        Map<String, MultipartFile> itemImageMap = new HashMap<>();
        if (itemImages != null) {
            for (MultipartFile file : itemImages) {
                if (file != null && !file.isEmpty()) {
                    String fileName = file.getOriginalFilename();
                    if (fileName != null) {
                        // 확장자 제거 후 파싱
                        String nameWithoutExt = fileName.contains(".") ?
                                fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
                        if (nameWithoutExt.contains("_")) {
                            itemImageMap.put(nameWithoutExt, file);  // "1_1" → file
                        }
                    }
                }
            }
        }

        int order = 1;
        for (QuestionRequest qReq : questions) {
            int questionOrder = qReq.getOrder() != null ? qReq.getOrder() : order;

            SurveyQuestion question = SurveyQuestion.create(
                    eventSeq,
                    qReq.getQuestionType(),
                    qReq.getQuestionTypeDetail(),
                    qReq.getQuestion(),
                    questionOrder,
                    regId
            );
            surveyQuestionMapper.insert(question);

            // 문항 이미지 저장
            MultipartFile questionImgFile = questionImageMap.get(questionOrder);
            if (questionImgFile != null) {
                String questionImgPath = fileStorageService.storeSurveyQuestionImg(questionImgFile, eventSeqStr, questionOrder);
                surveyQuestionMapper.updateQuestionImg(eventSeq, question.getQuestionSeq(), questionImgPath);
                log.info("문항 이미지 저장 완료 - eventSeq: {}, questionSeq: {}, path: {}", eventSeq, question.getQuestionSeq(), questionImgPath);
            }

            // 객관식 항목 저장
            if (qReq.getItems() != null && !qReq.getItems().isEmpty()) {
                int itemOrder = 1;
                for (ItemRequest iReq : qReq.getItems()) {
                    int currentItemOrder = iReq.getOrder() != null ? iReq.getOrder() : itemOrder;

                    SurveyItem item = SurveyItem.create(
                            eventSeq,
                            question.getQuestionSeq(),
                            iReq.getItem(),
                            iReq.getItemValue(),
                            currentItemOrder,
                            regId
                    );
                    if (iReq.getJumpQuestion() != null) {
                        item.setJumpQuestion(iReq.getJumpQuestion());
                    }
                    surveyItemMapper.insert(item);

                    // 항목 이미지 저장
                    String itemImageKey = questionOrder + "_" + currentItemOrder;
                    MultipartFile itemImgFile = itemImageMap.get(itemImageKey);
                    if (itemImgFile != null) {
                        String itemImgPath = fileStorageService.storeSurveyItemImg(itemImgFile, eventSeqStr, questionOrder, currentItemOrder);
                        surveyItemMapper.updateItemImg(eventSeq, question.getQuestionSeq(), item.getItemSeq(), itemImgPath);
                        log.info("항목 이미지 저장 완료 - eventSeq: {}, questionSeq: {}, itemSeq: {}, path: {}",
                                eventSeq, question.getQuestionSeq(), item.getItemSeq(), itemImgPath);
                    }

                    itemOrder++;
                }
            }
            order++;
        }
    }

    /**
     * 문항 저장 (이미지 없음)
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

    /**
     * 범용인증키 설명문구 조회
     */
    @Transactional(readOnly = true)
    public String getAuthKeyDesc(Integer eventSeq) {
        // 이벤트 존재 확인
        surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        return surveyMasterMapper.selectAuthKeyDesc(eventSeq);
    }

    /**
     * 범용인증키 설명문구 수정
     */
    @Transactional
    public void updateAuthKeyDesc(Integer eventSeq, String authKeyDesc, String userId) {
        // 이벤트 존재 확인 및 권한 체크
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        Integer userLevel = adminService.getUserLevel(userId);
        adminService.validateModifyPermission(userId, userLevel, event.getRegId());

        surveyMasterMapper.updateAuthKeyDesc(eventSeq, authKeyDesc);
        log.info("범용인증키 설명문구 수정 완료 - eventSeq: {}", eventSeq);
    }

    /**
     * 유저키 일괄 생성
     */
    @Transactional
    public List<String> generateUserKeys(Integer eventSeq, int count, String regId) {
        // 이벤트 존재 확인 및 권한 체크
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        Integer userLevel = adminService.getUserLevel(regId);
        adminService.validateModifyPermission(regId, userLevel, event.getRegId());

        List<String> generatedKeys = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String userKey = generateRandomCode(12); // 12자리 키 생성

            // SurveyUser 등록
            SurveyUser user = SurveyUser.createForAuth(eventSeq, userKey, regId);
            surveyUserMapper.insert(user);

            generatedKeys.add(userKey);
        }

        log.info("유저키 {} 개 생성 완료 - eventSeq: {}", count, eventSeq);
        return generatedKeys;
    }

    /**
     * 랜덤 코드 생성 (영문+숫자)
     */
    private String generateRandomCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder result = new StringBuilder();
        java.util.Random random = new java.util.Random();

        for (int i = 0; i < length; i++) {
            result.append(chars.charAt(random.nextInt(chars.length())));
        }

        return result.toString();
    }

    /**
     * 범용인증코드 엑셀 업로드
     * - 엑셀 파일에서 EVENT_SEQ(A), AUTH_CODE(B) 컬럼을 읽어서 등록
     */
    @Transactional
    public Map<String, Object> uploadAuthKeyExcel(MultipartFile file, String regId) {
        Map<String, Object> result = new HashMap<>();

        // 파일 확장자 검증
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null ||
                (!originalFilename.endsWith(".xlsx") && !originalFilename.endsWith(".xls"))) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "엑셀 파일만 업로드 가능합니다. (.xlsx, .xls)");
        }

        // 엑셀 파일 읽기 (2행부터 시작, A, B 컬럼)
        List<Map<String, String>> excelContent = excelService.readExcel(file, 2, "A", "B");

        if (excelContent.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일에 데이터가 없습니다.");
        }

        int successCount = 0;
        int failCount = 0;
        List<String> errors = new ArrayList<>();

        // 배치 등록용 리스트
        List<SurveyUser> usersToInsert = new ArrayList<>();
        List<AuthUserMapping> mappingsToInsert = new ArrayList<>();

        for (int i = 0; i < excelContent.size(); i++) {
            Map<String, String> row = excelContent.get(i);
            int rowNum = i + 2; // 실제 엑셀 행 번호

            String eventSeqStr = row.get("A");
            String authCode = row.get("B");

            // 데이터 검증
            if (eventSeqStr == null || eventSeqStr.isEmpty()) {
                errors.add("행 " + rowNum + ": 이벤트 번호가 비어있습니다.");
                failCount++;
                continue;
            }

            if (authCode == null || authCode.isEmpty()) {
                errors.add("행 " + rowNum + ": 범용인증코드가 비어있습니다.");
                failCount++;
                continue;
            }

            // 범용인증코드 길이 검증 (11~25자)
            if (authCode.length() < 11 || authCode.length() > 25) {
                errors.add("행 " + rowNum + ": 범용인증코드는 11~25자여야 합니다. (현재: " + authCode.length() + "자)");
                failCount++;
                continue;
            }

            Integer eventSeq;
            try {
                eventSeq = Integer.parseInt(eventSeqStr.trim());
            } catch (NumberFormatException e) {
                errors.add("행 " + rowNum + ": 이벤트 번호가 숫자가 아닙니다.");
                failCount++;
                continue;
            }

            // 이벤트 존재 확인
            Optional<SurveyMaster> eventOpt = surveyMasterMapper.selectByEventSeq(eventSeq);
            if (eventOpt.isEmpty()) {
                errors.add("행 " + rowNum + ": 이벤트가 존재하지 않습니다. (eventSeq: " + eventSeq + ")");
                failCount++;
                continue;
            }

            // 중복 인증코드 확인
            if (authUserMappingMapper.checkDuplicateAuthCode(eventSeq, authCode) > 0) {
                errors.add("행 " + rowNum + ": 이미 등록된 인증코드입니다. (" + authCode + ")");
                failCount++;
                continue;
            }

            // 사용자 키 생성
            String userKey = generateRandomCode(12);

            // SurveyUser 생성
            SurveyUser user = SurveyUser.createForAuth(eventSeq, userKey, regId);
            surveyUserMapper.insert(user);

            // AuthUserMapping 생성
            AuthUserMapping mapping = AuthUserMapping.create(
                    eventSeq, user.getSeq(), userKey, authCode, regId
            );
            authUserMappingMapper.insert(mapping);

            successCount++;
        }

        result.put("success", true);
        result.put("totalCount", excelContent.size());
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }

        log.info("범용인증코드 엑셀 업로드 완료 - 전체: {}, 성공: {}, 실패: {}",
                excelContent.size(), successCount, failCount);

        return result;
    }

    /**
     * 이벤트 결과 엑셀 다운로드 데이터 생성 (4개 시트)
     * - 시트1: 개요 (기본정보, 배포현황, 참여현황, 응답시간)
     * - 시트2: 전체_응답결과 (문항별 통계)
     * - 시트3: 개별전체(응답자) (응답자 행동분석 + 응답)
     * - 시트4: 개별전체(비응답자) (미응답자 목록)
     */
    @Transactional(readOnly = true)
    public byte[] generateEventResultExcel(Integer eventSeq) {
        // 이벤트 조회
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        // 문항 목록 조회
        List<SurveyQuestion> questions = surveyQuestionMapper.selectByEventSeq(eventSeq);

        // 항목 목록 조회
        List<SurveyItem> allItems = surveyItemMapper.selectByEventSeq(eventSeq);

        // 참여자 목록 조회 (설문 완료자)
        List<SurveyUser> participants = surveyUserMapper.selectCompletedByEventSeq(eventSeq);

        // 미응답자/접속자 목록 조회
        List<SurveyUser> absentees = surveyUserMapper.selectAbsenteesAndLurkers(eventSeq);

        // 모든 응답 조회
        List<SurveyAnswer> allAnswers = surveyAnswerMapper.selectByEventSeq(eventSeq);

        // 사용자별 응답 맵 구성
        Map<Integer, Map<Integer, String>> userAnswersMap = new HashMap<>();
        for (SurveyAnswer answer : allAnswers) {
            userAnswersMap
                    .computeIfAbsent(answer.getUserSeq(), k -> new HashMap<>())
                    .put(answer.getQuestionSeq(), answer.getAnswer());
        }

        // 통계 정보
        int totalParticipants = surveyUserMapper.countByEventSeq(eventSeq);
        int completedCount = surveyUserMapper.countCompletedByEventSeq(eventSeq);
        int absenteesCount = surveyUserMapper.countAbsenteesByEventSeq(eventSeq);
        int lurkersCount = surveyUserMapper.countLurkersByEventSeq(eventSeq);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook()) {
            // 시트1: 개요
            addSurveySummarySheet(workbook, event, questions.size(), totalParticipants, completedCount, absenteesCount, lurkersCount);

            // 시트2: 전체_응답결과
            addSurveyResponsesSheet(workbook, eventSeq, questions, allItems);

            // 시트3: 개별전체(응답자)
            addSurveyParticipantsSheet(workbook, event, questions, participants, userAnswersMap);

            // 시트4: 개별전체(비응답자)
            addSurveyAbsenteesSheet(workbook, absentees);

            return excelService.toByteArray(workbook);

        } catch (Exception e) {
            log.error("이벤트 결과 엑셀 생성 실패 - eventSeq: {}", eventSeq, e);
            throw new BusinessException(ErrorCode.FILE_WRITE_FAILED, "Excel 파일 생성에 실패했습니다.");
        }
    }

    /**
     * 시트1: 개요 (기본정보, 배포현황, 참여현황)
     */
    private void addSurveySummarySheet(SXSSFWorkbook workbook, SurveyMaster event,
                                        int questionCount, int totalParticipants, int completedCount,
                                        int absenteesCount, int lurkersCount) {
        Sheet sheet = workbook.createSheet("개요");
        sheet.setDefaultColumnWidth(22);

        // 스타일 생성
        CellStyle titleStyle = createExcelStyle(workbook, 17, true, 255, 255, 255);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);

        CellStyle headerStyle = createExcelStyle(workbook, 12, true, 255, 255, 255);
        CellStyle labelStyle = createExcelStyle(workbook, 10, false, 255, 255, 204); // 노란색 배경
        CellStyle valueStyle = createExcelStyle(workbook, 10, false, 255, 255, 255);

        setBorder(labelStyle);
        setBorder(valueStyle);

        int rowIdx = 1;

        // 제목
        Row titleRow = sheet.createRow(rowIdx++);
        Cell titleCell = titleRow.createCell(0);
        String eventName = event.getEventName() != null ? event.getEventName().replaceAll("[{}]", "") : "";
        titleCell.setCellValue(eventName);
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 3));

        rowIdx += 2;

        // ■ 기본정보
        Row basicInfoHeader = sheet.createRow(rowIdx++);
        Cell basicCell = basicInfoHeader.createCell(0);
        basicCell.setCellValue("■  기본정보");
        basicCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 3));

        // 제목
        Row row1 = sheet.createRow(rowIdx++);
        createLabelValueRow(row1, 0, "제목", eventName, labelStyle, valueStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 1, 3));

        // 기간
        Row row2 = sheet.createRow(rowIdx++);
        String period = (event.getStartDate() != null ? event.getStartDate() : "") + " ~ " +
                        (event.getEndDate() != null ? event.getEndDate() : "");
        createLabelValueRow(row2, 0, "기간", period, labelStyle, valueStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 1, 3));

        // 진행상태 / 개인정보제공동의
        Row row3 = sheet.createRow(rowIdx++);
        String status = getStatusText(event.getStatus());
        String privacyYn = "Y".equals(event.getPrivacyPolicyYn()) ? "사용" : "미사용";
        createLabelValueRow(row3, 0, "진행상태", status, labelStyle, valueStyle);
        createLabelValueRow(row3, 2, "개인정보제공동의", privacyYn, labelStyle, valueStyle);

        // 인증 / QR코드
        Row row4 = sheet.createRow(rowIdx++);
        String auth = getAuthText(event.getAuth());
        String qrCode = "Y".equals(event.getQrCode()) ? "사용" : "미사용";
        createLabelValueRow(row4, 0, "인증", auth, labelStyle, valueStyle);
        createLabelValueRow(row4, 2, "QR코드", qrCode, labelStyle, valueStyle);

        rowIdx++;

        // ■ 참여현황
        Row participationHeader = sheet.createRow(rowIdx++);
        Cell participationCell = participationHeader.createCell(0);
        participationCell.setCellValue("■  참여현황");
        participationCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 3));

        // 설문인원 / 응답자
        Row row5 = sheet.createRow(rowIdx++);
        String responseRate = formatPercentage(completedCount, totalParticipants);
        createLabelValueRow(row5, 0, "설문인원", totalParticipants + "명", labelStyle, valueStyle);
        createLabelValueRow(row5, 2, "응답자(응답률)", completedCount + "명(" + responseRate + ")", labelStyle, valueStyle);

        // 접속자 / 미응답자
        Row row6 = sheet.createRow(rowIdx++);
        String absenteeRate = formatPercentage(absenteesCount, totalParticipants);
        int totalLurkers = lurkersCount + completedCount; // 접속자 = 응답자 + 접속만 한 사람
        createLabelValueRow(row6, 0, "설문 접속자수", totalLurkers + "명", labelStyle, valueStyle);
        createLabelValueRow(row6, 2, "미응답자(미응답률)", absenteesCount + "명(" + absenteeRate + ")", labelStyle, valueStyle);

        rowIdx++;

        // ■ 응답시간
        Row timeHeader = sheet.createRow(rowIdx++);
        Cell timeCell = timeHeader.createCell(0);
        timeCell.setCellValue("■  응답시간");
        timeCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 3));

        // 문항수
        Row row7 = sheet.createRow(rowIdx++);
        createLabelValueRow(row7, 0, "문항수", questionCount + "개", labelStyle, valueStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 1, 3));
    }

    /**
     * 시트2: 전체_응답결과 (문항별 통계)
     */
    private void addSurveyResponsesSheet(SXSSFWorkbook workbook, Integer eventSeq,
                                          List<SurveyQuestion> questions, List<SurveyItem> allItems) {
        Sheet sheet = workbook.createSheet("전체_응답결과");
        sheet.setColumnWidth(0, 256 * 77);
        sheet.setColumnWidth(1, 256 * 15);
        sheet.setColumnWidth(2, 256 * 15);

        CellStyle headerStyle = createExcelStyle(workbook, 14, true, 255, 255, 255);
        CellStyle questionStyle = createExcelStyle(workbook, 10, true, 255, 255, 204); // 노란색
        CellStyle itemHeaderStyle = createExcelStyle(workbook, 10, true, 204, 255, 204); // 초록색
        CellStyle normalStyle = createExcelStyle(workbook, 10, false, 255, 255, 255);

        setBorder(questionStyle);
        setBorder(itemHeaderStyle);
        setBorder(normalStyle);

        int rowIdx = 1;

        // 제목
        Row titleRow = sheet.createRow(rowIdx++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("■ 문항 별 상세 결과");
        titleCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, 2));

        rowIdx += 2;

        // 문항별 통계
        for (SurveyQuestion question : questions) {
            // 문항 제목
            Row qRow = sheet.createRow(rowIdx++);
            String qTypeText = getQuestionTypeText(question.getQuestionType(), question.getQuestionTypeDetail());
            String qText = "Q" + question.getOrder() + qTypeText + question.getQuestion();

            Cell qCell = qRow.createCell(0);
            qCell.setCellValue(qText);
            qCell.setCellStyle(questionStyle);
            qRow.createCell(1).setCellStyle(questionStyle);
            qRow.createCell(2).setCellStyle(questionStyle);
            sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 2));

            int totalAnswers = surveyAnswerMapper.countByQuestionSeq(eventSeq, question.getQuestionSeq());

            if (question.isMultipleChoice()) {
                // 객관식: 보기별 통계
                Row headerRow = sheet.createRow(rowIdx++);
                createCell(headerRow, 0, "보기", itemHeaderStyle);
                createCell(headerRow, 1, "응답자수(명)", itemHeaderStyle);
                createCell(headerRow, 2, "응답률(%)", itemHeaderStyle);

                List<SurveyItem> items = allItems.stream()
                        .filter(item -> item.getQuestionSeq().equals(question.getQuestionSeq()))
                        .collect(Collectors.toList());

                for (SurveyItem item : items) {
                    int count;
                    if (question.isMultiSelect()) {
                        count = surveyAnswerMapper.countByItemValueMCM(eventSeq, question.getQuestionSeq(), item.getItemValue());
                    } else {
                        count = surveyAnswerMapper.countByItemSeq(eventSeq, question.getQuestionSeq(), item.getItemSeq());
                    }

                    Row itemRow = sheet.createRow(rowIdx++);
                    createCell(itemRow, 0, item.getItem(), normalStyle);
                    createCell(itemRow, 1, String.valueOf(count), normalStyle);
                    createCell(itemRow, 2, formatPercentage(count, totalAnswers), normalStyle);
                }
            } else {
                // 주관식: 응답자수만 표시
                Row headerRow = sheet.createRow(rowIdx++);
                createCell(headerRow, 0, "응답자수(명)", itemHeaderStyle);

                Cell countCell = headerRow.createCell(1);
                countCell.setCellValue(totalAnswers);
                countCell.setCellStyle(normalStyle);
                headerRow.createCell(2).setCellStyle(normalStyle);
                sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 1, 2));
            }

            rowIdx++; // 문항 사이 빈 줄
        }
    }

    /**
     * 시트3: 개별전체(응답자) - 응답자 행동분석 + 응답
     */
    private void addSurveyParticipantsSheet(SXSSFWorkbook workbook, SurveyMaster event,
                                             List<SurveyQuestion> questions, List<SurveyUser> participants,
                                             Map<Integer, Map<Integer, String>> userAnswersMap) {
        Sheet sheet = workbook.createSheet("개별전체(응답자)");
        sheet.setDefaultColumnWidth(15);

        CellStyle headerStyle = createExcelStyle(workbook, 12, true, 255, 255, 204); // 노란색
        CellStyle subHeaderStyle = createExcelStyle(workbook, 10, true, 204, 255, 204); // 초록색
        CellStyle normalStyle = createExcelStyle(workbook, 10, false, 255, 255, 255);

        setBorder(headerStyle);
        setBorder(subHeaderStyle);
        setBorder(normalStyle);

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        int rowIdx = 0;

        // 헤더 행 1 (카테고리)
        Row header1 = sheet.createRow(rowIdx++);
        createCell(header1, 0, "", headerStyle);

        // 응답자 행동 분석 (3칸)
        createCell(header1, 1, "응답자 행동 분석", headerStyle);
        createCell(header1, 2, "", headerStyle);
        createCell(header1, 3, "", headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 1, 3));

        // 응답 결과 분석 (문항 수만큼)
        int startCol = 4;
        if (!questions.isEmpty()) {
            createCell(header1, startCol, "응답 결과 분석", headerStyle);
            for (int i = 1; i < questions.size(); i++) {
                createCell(header1, startCol + i, "", headerStyle);
            }
            if (questions.size() > 1) {
                sheet.addMergedRegion(new CellRangeAddress(0, 0, startCol, startCol + questions.size() - 1));
            }
        }

        // 헤더 행 2 (세부 항목)
        Row header2 = sheet.createRow(rowIdx++);
        createCell(header2, 0, "응답자", subHeaderStyle);
        createCell(header2, 1, "문자 수신일시", subHeaderStyle);
        createCell(header2, 2, "접속일시", subHeaderStyle);
        createCell(header2, 3, "응답일시", subHeaderStyle);

        for (int i = 0; i < questions.size(); i++) {
            createCell(header2, 4 + i, "Q" + (i + 1), subHeaderStyle);
        }

        // 데이터 행
        for (SurveyUser participant : participants) {
            Row dataRow = sheet.createRow(rowIdx++);

            // 응답자 (전화번호 또는 userKey)
            String identifier = decryptDataSafe(participant.getResendUserPhone());
            if (identifier == null || identifier.isEmpty()) {
                identifier = participant.getUserKey();
            }
            createCell(dataRow, 0, identifier != null ? identifier : "", normalStyle);

            // 문자 수신일시 (현재 없으면 -)
            createCell(dataRow, 1, "-", normalStyle);

            // 접속일시
            String surveyStartTime = participant.getSurveyStartTime() != null
                    ? participant.getSurveyStartTime().format(dateTimeFormatter) : "-";
            createCell(dataRow, 2, surveyStartTime, normalStyle);

            // 응답일시
            String submissionDate = participant.getSubmissionDate() != null
                    ? participant.getSubmissionDate().format(dateTimeFormatter) : "-";
            createCell(dataRow, 3, submissionDate, normalStyle);

            // 문항별 응답
            Map<Integer, String> userAnswers = userAnswersMap.getOrDefault(participant.getSeq(), new HashMap<>());
            for (int i = 0; i < questions.size(); i++) {
                SurveyQuestion question = questions.get(i);
                String answer = userAnswers.get(question.getQuestionSeq());
                String displayAnswer = answer != null ? answer.replaceAll("##", " ") : "";
                createCell(dataRow, 4 + i, displayAnswer, normalStyle);
            }
        }
    }

    /**
     * 시트4: 개별전체(비응답자) - 미응답자 목록
     */
    private void addSurveyAbsenteesSheet(SXSSFWorkbook workbook, List<SurveyUser> absentees) {
        Sheet sheet = workbook.createSheet("개별전체(비응답자)");
        sheet.setDefaultColumnWidth(15);

        CellStyle headerStyle = createExcelStyle(workbook, 10, true, 204, 255, 204); // 초록색
        CellStyle normalStyle = createExcelStyle(workbook, 10, false, 255, 255, 255);

        setBorder(headerStyle);
        setBorder(normalStyle);

        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        int rowIdx = 0;

        // 헤더 행 1 (카테고리)
        Row header1 = sheet.createRow(rowIdx++);
        createCell(header1, 0, "미응답자", headerStyle);
        createCell(header1, 1, "진행 일시", headerStyle);
        createCell(header1, 2, "", headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 1, 2));

        // 헤더 행 2 (세부 항목)
        Row header2 = sheet.createRow(rowIdx++);
        createCell(header2, 0, "개인정보", headerStyle);
        createCell(header2, 1, "문자 수신일시", headerStyle);
        createCell(header2, 2, "접속일시", headerStyle);

        // 데이터 행
        for (SurveyUser absentee : absentees) {
            Row dataRow = sheet.createRow(rowIdx++);

            // 개인정보 (전화번호 또는 userKey)
            String identifier = decryptDataSafe(absentee.getResendUserPhone());
            if (identifier == null || identifier.isEmpty()) {
                identifier = absentee.getUserKey();
            }
            createCell(dataRow, 0, identifier != null ? identifier : "", normalStyle);

            // 문자 수신일시 (현재 없으면 -)
            createCell(dataRow, 1, "-", normalStyle);

            // 접속일시
            String surveyStartTime = absentee.getSurveyStartTime() != null
                    ? absentee.getSurveyStartTime().format(dateTimeFormatter) : "-";
            createCell(dataRow, 2, surveyStartTime, normalStyle);
        }
    }

    // ==================== Excel Helper Methods ====================

    /**
     * Excel 셀 스타일 생성
     */
    private CellStyle createExcelStyle(SXSSFWorkbook workbook, int fontSize, boolean bold, int r, int g, int b) {
        CellStyle style = workbook.createCellStyle();
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        if (style instanceof XSSFCellStyle) {
            XSSFCellStyle xssfStyle = (XSSFCellStyle) style;
            XSSFColor color = new XSSFColor(new java.awt.Color(r, g, b), new DefaultIndexedColorMap());
            xssfStyle.setFillForegroundColor(color);
        }

        Font font = workbook.createFont();
        font.setBold(bold);
        font.setFontHeightInPoints((short) fontSize);
        style.setFont(font);

        style.setVerticalAlignment(VerticalAlignment.CENTER);

        return style;
    }

    /**
     * 셀에 테두리 설정
     */
    private void setBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    /**
     * 라벨-값 행 생성
     */
    private void createLabelValueRow(Row row, int startCol, String label, String value,
                                      CellStyle labelStyle, CellStyle valueStyle) {
        Cell labelCell = row.createCell(startCol);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(labelStyle);

        Cell valueCell = row.createCell(startCol + 1);
        valueCell.setCellValue(value);
        valueCell.setCellStyle(valueStyle);
    }

    /**
     * 셀 생성
     */
    private void createCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    /**
     * 상태 텍스트 변환
     */
    private String getStatusText(String status) {
        if (status == null) return "정보 없음";
        switch (status) {
            case "A": return "대기";
            case "P": return "진행중";
            case "S": return "중지";
            case "F": return "종료";
            default: return "정보 없음";
        }
    }

    /**
     * 인증 방식 텍스트 변환
     */
    private String getAuthText(String auth) {
        if (auth == null) return "정보 없음";
        switch (auth) {
            case "UA": return "실명인증";
            case "PA": return "휴대폰인증";
            case "GA": return "범용인증";
            case "NA": return "없음";
            default: return "정보 없음";
        }
    }

    /**
     * 문항 타입 텍스트 변환
     */
    private String getQuestionTypeText(String questionType, String questionTypeDetail) {
        if ("MC".equals(questionType)) {
            if ("MCM".equals(questionTypeDetail)) {
                return ". (객관식/복수선택) ";
            }
            return ". (객관식) ";
        }
        return ". (주관식) ";
    }

    /**
     * 비율 포맷팅
     */
    private String formatPercentage(int numerator, int denominator) {
        if (denominator <= 0) {
            return "0.0%";
        }
        double rate = numerator * 100.0 / denominator;
        return String.format("%.1f%%", rate);
    }

    /**
     * 데이터 복호화 (예외 발생 시 null 반환)
     */
    private String decryptDataSafe(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return null;
        }
        try {
            return CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(encryptedData));
        } catch (Exception e) {
            log.debug("데이터 복호화 실패: {}", e.getMessage());
            return encryptedData;
        }
    }

    /**
     * QR 코드 이미지 생성
     * @param authCodeUrl QR 코드가 가리킬 인증 URL 코드
     * @return QR 코드 이미지 접근 URL
     */
    private String generateQrCodeImage(String authCodeUrl) {
        try {
            QrCodeUtils qrCodeUtils = new QrCodeUtils();

            // QR 코드에 담길 URL (프론트엔드 설문 접근 URL)
            String qrContents = wiseadUrl + "/auth/qrcode/" + authCodeUrl;

            // QR 코드 이미지 저장 경로
            String savePath = uploadDir + "/qrcode/";

            // QR 코드 생성 및 파일명 반환
            String fileName = qrCodeUtils.createQrCode(qrContents, savePath);

            if (fileName == null || fileName.isEmpty()) {
                log.error("QR 코드 생성 실패 - authCodeUrl: {}", authCodeUrl);
                return null;
            }

            // 접근 가능한 URL 반환
            return qrUrl + "/" + fileName;

        } catch (Exception e) {
            log.error("QR 코드 이미지 생성 중 오류 발생: ", e);
            return null;
        }
    }
}
