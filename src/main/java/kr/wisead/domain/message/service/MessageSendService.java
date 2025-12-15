package kr.wisead.domain.message.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.message.dto.*;
import kr.wisead.domain.message.entity.MsgQueue;
import kr.wisead.domain.message.entity.MsgResult;
import kr.wisead.mapper.sms.MsgQueueMapper;
import kr.wisead.mapper.sms.MsgResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
     * 예약 발송 취소
     */
    @Transactional("smsTransactionManager")
    public int cancelScheduledMessage(Integer mseq) {
        MsgQueue msgQueue = msgQueueMapper.findByMseq(mseq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "발송 정보를 찾을 수 없습니다."));

        if (!msgQueue.isPending()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "대기 중인 발송만 취소할 수 있습니다.");
        }

        int deleted = msgQueueMapper.delete(mseq);
        log.info("예약 발송 취소 - mseq: {}", mseq);
        return deleted;
    }

    /**
     * 배치 전체 예약 취소
     */
    @Transactional("smsTransactionManager")
    public int cancelScheduledBatch(String userKey) {
        int deleted = msgQueueMapper.deleteByUserKey(userKey);
        log.info("배치 예약 발송 취소 - userKey: {}, count: {}", userKey, deleted);
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
}
