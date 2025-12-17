package kr.wisead.domain.survey.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.excel.service.ExcelService;
import kr.wisead.domain.survey.dto.*;
import kr.wisead.domain.survey.entity.*;
import kr.wisead.mapper.primary.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
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
    private final ExcelService excelService;

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
    public void updateAuthKeyDesc(Integer eventSeq, String authKeyDesc) {
        // 이벤트 존재 확인
        surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        surveyMasterMapper.updateAuthKeyDesc(eventSeq, authKeyDesc);
        log.info("범용인증키 설명문구 수정 완료 - eventSeq: {}", eventSeq);
    }

    /**
     * 유저키 일괄 생성
     */
    @Transactional
    public List<String> generateUserKeys(Integer eventSeq, int count, String regId) {
        // 이벤트 존재 확인
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

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
     * 이벤트 결과 엑셀 다운로드 데이터 생성
     * - 참여자 정보 + 설문 응답 포함
     */
    @Transactional(readOnly = true)
    public byte[] generateEventResultExcel(Integer eventSeq) {
        // 이벤트 조회
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        // 문항 목록 조회
        List<SurveyQuestion> questions = surveyQuestionMapper.selectByEventSeq(eventSeq);

        // 참여자 목록 조회 (설문 완료자)
        List<SurveyUser> users = surveyUserMapper.selectCompletedByEventSeq(eventSeq);

        // 모든 응답 조회
        List<SurveyAnswer> allAnswers = surveyAnswerMapper.selectByEventSeq(eventSeq);

        // 사용자별 응답 맵 구성
        Map<Integer, Map<Integer, String>> userAnswersMap = new HashMap<>();
        for (SurveyAnswer answer : allAnswers) {
            userAnswersMap
                    .computeIfAbsent(answer.getUserSeq(), k -> new HashMap<>())
                    .put(answer.getQuestionSeq(), answer.getAnswer());
        }

        try (SXSSFWorkbook workbook = excelService.createWorkbook()) {
            Sheet sheet = excelService.createSheet(workbook, "이벤트 참여자 정보");

            // 헤더 스타일
            CellStyle headerStyle = excelService.createHeaderStyle(workbook, 11, true, 173, 216, 230);

            // 헤더 생성
            List<String> headers = new ArrayList<>(Arrays.asList(
                    "NO", "이벤트명", "이벤트설명", "이벤트종류",
                    "발송번호", "이름", "주민번호", "연락처", "이메일",
                    "설문완료일", "시작일", "종료일", "등록일"
            ));

            // 문항 헤더 추가 (최대 15개)
            for (int i = 0; i < Math.min(questions.size(), 15); i++) {
                headers.add("Q" + (i + 1));
            }

            excelService.createHeaderRow(sheet, 0, headers, headerStyle);

            // 데이터 행 생성
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            int rowNum = 1;
            for (SurveyUser user : users) {
                List<Object> rowData = new ArrayList<>();

                rowData.add(rowNum);  // NO
                rowData.add(event.getEventName() != null ? event.getEventName().replaceAll("[{}]", "") : "");  // 이벤트명
                rowData.add(event.getEventDesc() != null ? event.getEventDesc() : "");  // 이벤트설명
                rowData.add("P".equals(event.getEventType()) ? "제세공과금" : "설문조사");  // 이벤트종류

                // 발송번호 복호화
                String resendPhone = decryptDataSafe(user.getResendUserPhone());
                rowData.add(resendPhone != null ? resendPhone : "");

                // 이름 복호화
                String userName = decryptDataSafe(user.getUserName());
                rowData.add(userName != null ? userName : "");

                // 주민번호 복호화
                String juminNum = decryptDataSafe(user.getJuminNum());
                rowData.add(juminNum != null ? juminNum : "");

                // 연락처 복호화
                String phone = decryptDataSafe(user.getUserPhone());
                rowData.add(phone != null ? phone : "");

                rowData.add(user.getUserEmail() != null ? user.getUserEmail() : "");  // 이메일

                // 설문완료일
                rowData.add(user.getSubmissionDate() != null ?
                        user.getSubmissionDate().format(dateTimeFormatter) : "");

                // 시작일, 종료일
                rowData.add(event.getStartDate() != null ? event.getStartDate() : "");
                rowData.add(event.getEndDate() != null ? event.getEndDate() : "");

                // 등록일
                rowData.add(user.getRegDate() != null ?
                        user.getRegDate().format(dateFormatter) : "");

                // 문항별 응답 추가
                Map<Integer, String> userAnswers = userAnswersMap.getOrDefault(user.getSeq(), new HashMap<>());
                for (int i = 0; i < Math.min(questions.size(), 15); i++) {
                    SurveyQuestion question = questions.get(i);
                    String answer = userAnswers.get(question.getQuestionSeq());
                    // ##를 공백으로 치환 (복수 응답 구분자)
                    rowData.add(answer != null ? answer.replaceAll("##", " ") : "");
                }

                excelService.createDataRow(sheet, rowNum++, rowData, null);
            }

            return excelService.toByteArray(workbook);

        } catch (Exception e) {
            log.error("이벤트 결과 엑셀 생성 실패 - eventSeq: {}", eventSeq, e);
            throw new BusinessException(ErrorCode.FILE_WRITE_FAILED, "Excel 파일 생성에 실패했습니다.");
        }
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
}
