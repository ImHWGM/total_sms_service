package kr.wisead.domain.message.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.message.dto.*;
import kr.wisead.domain.message.entity.MsgQueue;
import kr.wisead.domain.message.entity.MsgResult;
import kr.wisead.domain.survey.entity.SurveyMaster;
import kr.wisead.domain.survey.entity.SurveyUser;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import kr.wisead.mapper.primary.SurveyUserMapper;
import kr.wisead.mapper.sms.MsgQueueMapper;
import kr.wisead.mapper.sms.MsgResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 메시지 발송 Service (SMS DB)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSendService {

    private final MsgQueueMapper msgQueueMapper;
    private final MsgResultMapper msgResultMapper;
    private final SurveyUserMapper surveyUserMapper;
    private final SurveyMasterMapper surveyMasterMapper;

    @Value("${wisead.url:https://wisead.kr}")
    private String wiseadUrl;

    /**
     * 일반 문자 발송 (SMS/LMS/MMS)
     * MSG_QUEUE 테이블에 등록하면 외부 에이전트가 발송 처리
     */
    @Transactional("smsTransactionManager")
    public SmsSendResponse sendMessage(SmsSendRequest request, String regId) {
        // userKey(배치ID) 생성
        String userKey = MsgQueue.generateUserKey();
        String sendType = request.getSendType() != null ? request.getSendType() : "1";
        List<Integer> mseqList = new ArrayList<>();

        for (String receiver : request.getReceivers()) {
            String normalizedReceiver = normalizePhoneNumber(receiver);

            MsgQueue msgQueue;
            switch (request.getMsgType()) {
                case "S" -> msgQueue = MsgQueue.createSms(
                        normalizedReceiver,
                        request.getNormalizedCallback(),
                        request.getSubject(),
                        request.getText(),
                        userKey,
                        sendType,
                        regId
                );
                case "L" -> msgQueue = MsgQueue.createLms(
                        normalizedReceiver,
                        request.getNormalizedCallback(),
                        request.getSubject(),
                        request.getText(),
                        userKey,
                        sendType,
                        regId
                );
                case "M" -> msgQueue = MsgQueue.createMms(
                        normalizedReceiver,
                        request.getNormalizedCallback(),
                        request.getSubject(),
                        request.getText(),
                        request.getFileCnt() != null ? request.getFileCnt() : 0,
                        request.getFileloc1(),
                        request.getFileloc2(),
                        request.getFileloc3(),
                        userKey,
                        sendType,
                        regId
                );
                default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 메시지 타입입니다.");
            }

            // 예약 발송 시간 설정
            if (request.getRequestTime() != null && !request.isImmediate()) {
                msgQueue = msgQueue.withRequestTime(request.getRequestTime());
            }

            // MSG_QUEUE에 등록
            insertMsgQueue(request.getMsgType(), msgQueue);
            mseqList.add(msgQueue.getMseq());
        }

        log.info("문자 발송 등록 완료 - userKey: {}, count: {}, msgType: {}",
                userKey, mseqList.size(), request.getMsgType());

        return SmsSendResponse.success(mseqList, LocalDateTime.now(), request.isImmediate());
    }

    /**
     * 설문 문자 발송
     */
    @Transactional("smsTransactionManager")
    public int sendSurveyMessage(String msgType, String dstaddr, String callback,
                                  String subject, String text,
                                  Integer eventSeq, Integer userSeq,
                                  String sendType, String regId,
                                  LocalDateTime requestTime) {
        MsgQueue msgQueue = MsgQueue.createForSurvey(
                msgType, dstaddr, callback, subject, text,
                eventSeq, userSeq, sendType, regId
        );

        if (requestTime != null) {
            msgQueue = msgQueue.withRequestTime(requestTime);
        }

        msgQueueMapper.insertForSurvey(msgQueue);
        log.info("설문 문자 발송 등록 - eventSeq: {}, userSeq: {}, mseq: {}",
                eventSeq, userSeq, msgQueue.getMseq());

        return msgQueue.getMseq();
    }

    /**
     * 발송 이력 조회 (페이징)
     */
    @Transactional(value = "smsTransactionManager", readOnly = true)
    public PageResponse<MsgResultResponse> getSendHistory(SendHistorySearchRequest request) {
        List<String> tables = request.getTableNames();

        int total = msgResultMapper.countSendHistory(
                request.getRegId(),
                tables,
                request.getSrhDateStart(),
                request.getSrhDateEnd(),
                request.getType(),
                request.getKeyword(),
                request.getSendFailure()
        );

        List<MsgResult> results = msgResultMapper.selectSendHistory(
                request.getRegId(),
                tables,
                request.getSrhDateStart(),
                request.getSrhDateEnd(),
                request.getType(),
                request.getKeyword(),
                request.getSendFailure(),
                request.getSkip(),
                request.getAmount()
        );

        List<MsgResultResponse> content = results.stream()
                .map(MsgResultResponse::from)
                .collect(Collectors.toList());

        return PageResponse.of(content, request.getPageNum(), request.getAmount(), total);
    }

    /**
     * 예약 발송 취소 (소유자 검증 포함)
     */
    @Transactional("smsTransactionManager")
    public int cancelScheduledMessage(Integer mseq, String regId) {
        MsgQueue msgQueue = msgQueueMapper.findByMseq(mseq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "발송 정보를 찾을 수 없습니다."));

        // 소유자 검증 (extCol3 = regId)
        if (!regId.equals(msgQueue.getExtCol3())) {
            log.warn("발송 취소 권한 없음 - mseq: {}, 요청자: {}, 소유자: {}", mseq, regId, msgQueue.getExtCol3());
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "해당 발송을 취소할 권한이 없습니다.");
        }

        if (!msgQueue.isPending()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "대기 중인 발송만 취소할 수 있습니다.");
        }

        int deleted = msgQueueMapper.delete(mseq);
        log.info("예약 발송 취소 - mseq: {}, regId: {}", mseq, regId);
        return deleted;
    }

    /**
     * 배치 전체 예약 취소 (소유자 검증 포함)
     */
    @Transactional("smsTransactionManager")
    public int cancelScheduledBatch(String userKey, String regId) {
        // 배치의 첫 번째 메시지로 소유자 검증
        List<MsgQueue> messages = msgQueueMapper.findByUserKey(userKey);
        if (messages.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "발송 정보를 찾을 수 없습니다.");
        }

        // 소유자 검증
        MsgQueue firstMsg = messages.get(0);
        if (!regId.equals(firstMsg.getExtCol3())) {
            log.warn("배치 취소 권한 없음 - userKey: {}, 요청자: {}, 소유자: {}", userKey, regId, firstMsg.getExtCol3());
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "해당 발송을 취소할 권한이 없습니다.");
        }

        int deleted = msgQueueMapper.deleteByUserKey(userKey);
        log.info("배치 예약 발송 취소 - userKey: {}, count: {}, regId: {}", userKey, deleted, regId);
        return deleted;
    }

    /**
     * 대기 중인 발송 목록 조회
     */
    @Transactional(value = "smsTransactionManager", readOnly = true)
    public List<MsgQueue> getPendingMessages(String regId) {
        return msgQueueMapper.findPendingByRegId(regId);
    }

    /**
     * 대기 중인 발송 목록 조회 (페이징)
     */
    @Transactional(value = "smsTransactionManager", readOnly = true)
    public PageResponse<MsgQueueResponse> getPendingMessages(String regId, int page, int size) {
        long total = msgQueueMapper.countPendingByRegId(regId);

        int offset = (page - 1) * size;
        List<MsgQueue> results = msgQueueMapper.findPendingByRegIdPaging(regId, offset, size);

        List<MsgQueueResponse> content = results.stream()
                .map(MsgQueueResponse::from)
                .collect(Collectors.toList());

        return PageResponse.of(content, page, size, total);
    }

    /**
     * 메시지 타입별 INSERT 분기
     */
    private void insertMsgQueue(String msgType, MsgQueue msgQueue) {
        switch (msgType) {
            case "S" -> msgQueueMapper.insertSms(msgQueue);
            case "L" -> msgQueueMapper.insertLms(msgQueue);
            case "M" -> msgQueueMapper.insertMms(msgQueue);
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 메시지 타입입니다.");
        }
    }

    /**
     * 전화번호 정규화 (하이픈 제거)
     */
    private String normalizePhoneNumber(String phone) {
        return phone != null ? phone.replaceAll("-", "") : null;
    }

    /**
     * 설문 문자 재발송 (단건)
     *
     * @param userSeq 사용자 시퀀스
     * @param subject 제목 (useOriginal=false일 때 사용)
     * @param text 내용 (useOriginal=false일 때 사용, #유저키# 치환됨)
     * @param callback 발신번호
     * @param useOriginal true: 이전 발송 내용 그대로, false: 새 내용으로 발송
     * @param regId 등록자 ID
     * @return mseq
     */
    @Transactional("smsTransactionManager")
    public int resendSurveyMessage(Integer userSeq, String subject, String text,
                                    String callback, boolean useOriginal, String regId) {
        // SURVEY_USER에서 사용자 정보 조회
        SurveyUser surveyUser = surveyUserMapper.selectBySeq(userSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "설문 참여자 정보를 찾을 수 없습니다."));

        Integer eventSeq = surveyUser.getEventSeq();
        String userKey = surveyUser.getUserKey();
        String encryptedPhone = surveyUser.getResendUserPhone();

        if (encryptedPhone == null || encryptedPhone.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수신번호가 존재하지 않습니다.");
        }

        // 수신번호 복호화
        String dstaddr;
        try {
            dstaddr = CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(encryptedPhone));
        } catch (Exception e) {
            log.error("수신번호 복호화 실패 - userSeq: {}", userSeq, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "수신번호 복호화 실패");
        }

        String finalSubject;
        String finalText;
        String finalCallback;

        if (useOriginal) {
            // 이전 발송 내용 조회 (msg_result_yyyyMM 테이블에서)
            List<String> tables = getResultTableNames(eventSeq);
            MsgResult previous = msgResultMapper.selectPreviousSend(tables, eventSeq, userSeq);

            if (previous == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이전 발송 내역을 찾을 수 없습니다.");
            }

            finalSubject = previous.getSubject();
            finalText = previous.getText();
            finalCallback = callback != null ? callback : previous.getCallback();
            log.info("이전 발송 내용으로 재발송 - userSeq: {}, subject: {}", userSeq, finalSubject);
        } else {
            // 새 내용으로 발송 (설문 링크 생성)
            if (userKey == null || userKey.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "userKey가 존재하지 않습니다.");
            }

            // 이벤트 코드 조회
            SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트 정보를 찾을 수 없습니다."));

            // 설문 링크 생성: https://wisead.kr/auth/{eventCode}/{userKey}
            String surveyLink = generateSurveyLink(event.getEventCode(), userKey);

            finalSubject = subject;
            finalText = text.replace("#유저키#", surveyLink);
            finalCallback = callback;
            log.info("새 내용으로 재발송 - userSeq: {}, userKey: {}, link: {}", userSeq, userKey, surveyLink);
        }

        // MSG_QUEUE에 등록
        MsgQueue msgQueue = MsgQueue.createForSurvey(
                "L", // LMS로 발송
                normalizePhoneNumber(dstaddr),
                finalCallback,
                finalSubject,
                finalText,
                eventSeq,
                userSeq,
                "1", // 즉시 발송
                regId
        );

        msgQueueMapper.insertLms(msgQueue);
        log.info("설문 재발송 완료 - userSeq: {}, mseq: {}", userSeq, msgQueue.getMseq());

        return msgQueue.getMseq();
    }

    /**
     * 설문 문자 재발송 (다건)
     */
    @Transactional("smsTransactionManager")
    public ResendResponse resendSurveyMessageBatch(List<Integer> userSeqList, String subject,
                                                    String text, String callback,
                                                    boolean useOriginal, String regId) {
        int successCount = 0;
        int failCount = 0;
        List<String> failedUserSeqs = new ArrayList<>();

        for (Integer userSeq : userSeqList) {
            try {
                resendSurveyMessage(userSeq, subject, text, callback, useOriginal, regId);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.warn("다건 재발송 중 실패 - userSeq: {}, error: {}", userSeq, e.getMessage());
                failedUserSeqs.add(String.valueOf(userSeq));
            }
        }

        log.info("다건 재발송 완료 - 성공: {}/{}", successCount, userSeqList.size());

        if (failCount == 0) {
            return ResendResponse.success(successCount);
        } else {
            return ResendResponse.partial(successCount, failCount, failedUserSeqs);
        }
    }

    /**
     * 중복 번호 재발송 (설문)
     * 설문 발송 시 중복으로 실패한 번호들에게 재발송
     *
     * @param request 재발송 요청 (중복 수신자 목록 포함)
     * @param regId 등록자 ID
     * @return 재발송 결과
     */
    @Transactional("smsTransactionManager")
    public ResendResponse resendToDuplicates(ResendRequest request, String regId) {
        log.info("중복 번호 재발송 시작 - eventSeq: {}, regId: {}", request.getEventSeq(), regId);

        List<ResendRequest.DuplicateReceiver> receivers = request.getDuplicateReceivers();
        if (receivers == null || receivers.isEmpty()) {
            log.warn("중복 수신자 목록이 비어있음");
            return ResendResponse.fail("재발송할 수신자가 없습니다.");
        }

        // 이벤트 코드 조회
        String eventCode = request.getEventCode();
        if (eventCode == null || eventCode.isEmpty()) {
            eventCode = surveyMasterMapper.selectByEventSeq(request.getEventSeq())
                    .map(SurveyMaster::getEventCode)
                    .orElse(null);
        }

        if (eventCode == null || eventCode.isEmpty()) {
            log.error("이벤트 코드를 찾을 수 없음 - eventSeq: {}", request.getEventSeq());
            return ResendResponse.fail("이벤트 정보를 찾을 수 없습니다.");
        }

        int successCount = 0;
        int failCount = 0;
        List<String> failedList = new ArrayList<>();

        for (ResendRequest.DuplicateReceiver receiver : receivers) {
            try {
                String phone = normalizePhoneNumber(receiver.getPhone());
                String text = request.getText();

                // 대치문자 처리
                if (receiver.getRepChar01() != null && !receiver.getRepChar01().isEmpty()) {
                    text = text.replace("#대치문자1#", receiver.getRepChar01());
                }
                if (receiver.getRepChar02() != null && !receiver.getRepChar02().isEmpty()) {
                    text = text.replace("#대치문자2#", receiver.getRepChar02());
                }
                if (receiver.getRepChar03() != null && !receiver.getRepChar03().isEmpty()) {
                    text = text.replace("#대치문자3#", receiver.getRepChar03());
                }

                // 설문 링크 생성: https://wisead.kr/auth/{eventCode}/{userKey}
                String surveyLink = generateSurveyLink(eventCode, receiver.getUserKey());

                // #유저키#를 설문 링크로 치환
                text = text.replace("#유저키#", surveyLink);

                log.debug("설문 링크 생성 - eventCode: {}, userKey: {}, link: {}",
                        eventCode, receiver.getUserKey(), surveyLink);

                // MSG_QUEUE에 등록
                MsgQueue msgQueue = MsgQueue.createForSurvey(
                        "L", // LMS로 발송
                        phone,
                        request.getCallback(),
                        request.getSubject(),
                        text,
                        request.getEventSeq(),
                        receiver.getUserSeq(),
                        "1", // 즉시 발송
                        regId
                );

                msgQueueMapper.insertLms(msgQueue);
                successCount++;

            } catch (Exception e) {
                failCount++;
                failedList.add(receiver.getPhone());
                log.warn("중복 번호 재발송 실패 - phone: {}, error: {}", receiver.getPhone(), e.getMessage());
            }
        }

        log.info("중복 번호 재발송 완료 - 성공: {}, 실패: {}", successCount, failCount);

        if (failCount == 0) {
            return ResendResponse.success(successCount);
        } else {
            return ResendResponse.partial(successCount, failCount, failedList);
        }
    }

    /**
     * 설문 링크 생성
     * 형식: {wisead.url}/auth/{eventCode}/{userKey}
     */
    private String generateSurveyLink(String eventCode, String userKey) {
        return String.format("%s/auth/%s/%s", wiseadUrl, eventCode, userKey);
    }

    /**
     * 중복 번호에 새로운 내용으로 발송
     *
     * @param request 발송 요청 (새 내용 포함)
     * @param regId 등록자 ID
     * @return 발송 결과
     */
    @Transactional("smsTransactionManager")
    public ResendResponse sendNewToDuplicates(ResendRequest request, String regId) {
        log.info("중복 번호 신규 발송 시작 - eventSeq: {}, regId: {}", request.getEventSeq(), regId);

        // resendToDuplicates와 동일한 로직 사용
        // 차이점: useOriginalContent = false, 새로운 text 사용
        return resendToDuplicates(request, regId);
    }

    /**
     * 이벤트 시퀀스 기반으로 조회할 msg_result 테이블명 목록 생성
     * 현재 월부터 1개월 전까지
     */
    private List<String> getResultTableNames(Integer eventSeq) {
        List<String> tables = new ArrayList<>();
        java.time.LocalDate now = java.time.LocalDate.now();

        // 현재 월
        tables.add("msg_result_" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")));
        // 1개월 전
        tables.add("msg_result_" + now.minusMonths(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM")));

        return tables;
    }
}
