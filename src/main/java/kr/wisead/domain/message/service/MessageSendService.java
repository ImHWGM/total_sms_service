package kr.wisead.domain.message.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.common.util.ShortUrlUtils;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.event.dto.ParticipantForMessageResponse;
import kr.wisead.domain.event.service.EventParticipantService;
import kr.wisead.domain.message.dto.*;
import kr.wisead.domain.message.dto.EventMessageRequest.Receiver;
import kr.wisead.domain.message.entity.MsgQueue;
import kr.wisead.domain.message.entity.MsgResult;
import kr.wisead.domain.message.entity.SmsSend;
import kr.wisead.domain.payment.service.WalletService;
import kr.wisead.domain.survey.entity.SurveyMaster;
import kr.wisead.domain.survey.entity.SurveyUser;
import kr.wisead.mapper.primary.SmsSendMapper;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import kr.wisead.mapper.primary.SurveyUserMapper;
import kr.wisead.mapper.sms.MsgQueueMapper;
import kr.wisead.mapper.sms.MsgResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 메시지 발송 Service (SMS DB) */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSendService {

  private final MsgQueueMapper msgQueueMapper;
  private final MsgResultMapper msgResultMapper;
  private final SurveyUserMapper surveyUserMapper;
  private final SurveyMasterMapper surveyMasterMapper;
  private final SmsSendMapper smsSendMapper;
  private final WalletService walletService;
  private final UserIdResolver userIdResolver;
  private final EventParticipantService eventParticipantService;

  @Value("${wisead.url:https://wisead.kr}")
  private String wiseadUrl;

  @Value("${api.base.url:}")
  private String apiBaseUrl;

  /**
   * 일반 문자 발송 (SMS/LMS/MMS) MSG_QUEUE 테이블에 등록하면 외부 에이전트가 발송 처리
   *
   * @param request 발송 요청
   * @param regId 등록자 ID (userId)
   */
  @Transactional("smsTransactionManager")
  public SmsSendResponse sendMessage(SmsSendRequest request, String regId) {
    int messageCount = request.getReceivers().size();
    String serviceId = getServiceIdFromMsgType(request.getMsgType());

    // 1. 잔액 확인 및 차감
    String txGroupId = deductForMessage(regId, serviceId, messageCount, request.getMsgType());

    // 2. 메시지 발송 등록
    String userKey = MsgQueue.generateUserKey();
    List<Integer> mseqList = new ArrayList<>();
    String realUserId = userIdResolver.resolveUserId(regId);

    for (String receiver : request.getReceivers()) {
      String normalizedReceiver = normalizePhoneNumber(receiver);

      MsgQueue msgQueue;
      switch (request.getMsgType()) {
        case "S" ->
            msgQueue =
                MsgQueue.createSms(
                    normalizedReceiver,
                    request.getNormalizedCallback(),
                    request.getSubject(),
                    request.getText(),
                    userKey,
                    txGroupId,
                    realUserId);
        case "L" ->
            msgQueue =
                MsgQueue.createLms(
                    normalizedReceiver,
                    request.getNormalizedCallback(),
                    request.getSubject(),
                    request.getText(),
                    userKey,
                    txGroupId,
                    realUserId);
        case "M" ->
            msgQueue =
                MsgQueue.createMms(
                    normalizedReceiver,
                    request.getNormalizedCallback(),
                    request.getSubject(),
                    request.getText(),
                    request.getFileCnt() != null ? request.getFileCnt() : 0,
                    request.getFileloc1(),
                    request.getFileloc2(),
                    request.getFileloc3(),
                    userKey,
                    txGroupId,
                    realUserId);
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

    log.info(
        "문자 발송 등록 완료 - userKey: {}, count: {}, msgType: {}, txGroupId: {}",
        userKey,
        mseqList.size(),
        request.getMsgType(),
        txGroupId);

    return SmsSendResponse.success(mseqList, LocalDateTime.now(), request.isImmediate(), txGroupId);
  }

  /**
   * 메시지 발송을 위한 잔액 차감
   *
   * @param userId 사용자 ID
   * @param serviceId 서비스 ID (msg_sms, msg_lms, msg_mms)
   * @param quantity 발송 건수
   * @param msgType 메시지 타입 (로깅용)
   * @return txGroupId (환불 시 사용)
   */
  private String deductForMessage(String userId, String serviceId, int quantity, String msgType) {
    // userId는 실제로 userSeq임 (JWT subject로 seq 사용)
    Integer userSeq;
    try {
      userSeq = Integer.parseInt(userId);
    } catch (NumberFormatException e) {
      log.error("잘못된 사용자 식별자 - userId: {}", userId);
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자 정보를 찾을 수 없습니다.");
    }

    BigDecimal qty = BigDecimal.valueOf(quantity);
    BigDecimal unitPrice = walletService.getAppliedRate(userSeq, serviceId);
    BigDecimal totalAmount = unitPrice.multiply(qty);

    // 잔액 확인
    if (!walletService.hasEnoughBalance(userSeq, totalAmount)) {
      log.warn(
          "잔액 부족 - userId: {}, userSeq: {}, 필요금액: {}, msgType: {}",
          userId,
          userSeq,
          totalAmount,
          msgType);
      throw new BusinessException(
          ErrorCode.INSUFFICIENT_BALANCE,
          String.format("잔액이 부족합니다. 필요 금액: %s원", totalAmount.setScale(0)));
    }

    // 잔액 차감
    String txGroupId =
        walletService.deductWithPriority(
            userSeq, serviceId, qty, String.format("%s 발송 %d건", getMsgTypeName(msgType), quantity));

    log.info(
        "메시지 발송 비용 차감 - userId: {}, userSeq: {}, serviceId: {}, quantity: {}, totalAmount: {},"
            + " txGroupId: {}",
        userId,
        userSeq,
        serviceId,
        quantity,
        totalAmount,
        txGroupId);

    return txGroupId;
  }

  /** 메시지 타입을 서비스 ID로 변환 */
  private String getServiceIdFromMsgType(String msgType) {
    return switch (msgType) {
      case "S" -> "msg_sms";
      case "L" -> "msg_lms";
      case "M" -> "msg_mms";
      default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 메시지 타입입니다.");
    };
  }

  /** 메시지 타입명 반환 */
  private String getMsgTypeName(String msgType) {
    return switch (msgType) {
      case "S" -> "SMS";
      case "L" -> "LMS";
      case "M" -> "MMS";
      default -> "문자";
    };
  }

  /**
   * 설문 문자 발송
   *
   * @param txGroupId 결제 거래 그룹 ID (환불 추적용, 결제 없으면 null)
   */
  @Transactional("smsTransactionManager")
  public int sendSurveyMessage(
      String msgType,
      String dstaddr,
      String callback,
      String subject,
      String text,
      Integer eventSeq,
      Integer userSeq,
      String txGroupId,
      String regId,
      LocalDateTime requestTime) {
    String realUserId = userIdResolver.resolveUserId(regId);
    MsgQueue msgQueue =
        MsgQueue.createForSurvey(
            msgType, dstaddr, callback, subject, text, eventSeq, userSeq, txGroupId, realUserId);

    if (requestTime != null) {
      msgQueue = msgQueue.withRequestTime(requestTime);
    }

    msgQueueMapper.insertForSurvey(msgQueue);
    recordSmsSend(eventSeq, userSeq, subject, text, "1", dstaddr, callback, regId);
    log.info(
        "설문 문자 발송 등록 - eventSeq: {}, userSeq: {}, mseq: {}", eventSeq, userSeq, msgQueue.getMseq());

    return msgQueue.getMseq();
  }

  /** 발송 이력 조회 (페이징) */
  @Transactional(value = "smsTransactionManager", readOnly = true)
  public PageResponse<MsgResultResponse> getSendHistory(SendHistorySearchRequest request) {
    List<String> tables = request.getTableNames();
    String regId = request.getRegId();
    var startDate = request.getSrhDateStart();
    var endDate = request.getSrhDateEnd();
    String type = request.getType();
    String keyword = request.getKeyword();
    String sendFailure = request.getSendFailure();

    int total =
        msgResultMapper.countSendHistory(
            regId, tables, startDate, endDate, type, keyword, sendFailure);

    List<MsgResult> results =
        msgResultMapper.selectSendHistory(
            regId,
            tables,
            startDate,
            endDate,
            type,
            keyword,
            sendFailure,
            request.getSkip(),
            request.getAmount());

    List<MsgResultResponse> content = results.stream().map(MsgResultResponse::from).toList();

    if (apiBaseUrl != null && !apiBaseUrl.isEmpty()) {
      content.forEach(r -> r.withFullImageUrls(apiBaseUrl));
    }

    return PageResponse.of(content, request.getPageNum(), request.getAmount(), total);
  }

  /** 예약 발송 취소 (소유자 검증 + 환불 포함) - 단건 취소: 부분 환불 (취소 건수 × 단가) */
  public int cancelScheduledMessage(Integer mseq, String regId) {
    // 1. 메시지 조회 및 검증 (SMS DB)
    MsgQueue msgQueue =
        msgQueueMapper
            .findByMseq(mseq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "발송 정보를 찾을 수 없습니다."));

    // 소유자 검증 (extCol3 = regId)
    if (!regId.equals(msgQueue.getExtCol3())) {
      log.warn("발송 취소 권한 없음 - mseq: {}, 요청자: {}, 소유자: {}", mseq, regId, msgQueue.getExtCol3());
      throw new BusinessException(ErrorCode.ACCESS_DENIED, "해당 발송을 취소할 권한이 없습니다.");
    }

    if (!msgQueue.isPending()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "대기 중인 발송만 취소할 수 있습니다.");
    }

    String msgType = msgQueue.getMsgType();
    String txGroupId = msgQueue.getTxGroupId();

    // 설문 메시지인 경우 eventSeq, userSeq 미리 저장
    Integer eventSeq = msgQueue.getExtCol0();
    String extCol1 = msgQueue.getExtCol1();

    // 2. 메시지 삭제 (SMS DB)
    int deleted = deleteMsgQueue(mseq);

    // 3. 환불 처리 (Primary DB) - 단건은 부분 환불
    if (deleted > 0 && txGroupId != null) {
      refundPartial(regId, msgType, 1, txGroupId);
    }

    // 4. 설문 메시지인 경우 survey_user soft delete + sms_send 삭제
    if (deleted > 0 && eventSeq != null && extCol1 != null) {
      cleanupSurveyDataOnCancel(eventSeq, extCol1, regId);
    }

    log.info(
        "예약 발송 취소 완료 - mseq: {}, regId: {}, msgType: {}, txGroupId: {}",
        mseq,
        regId,
        msgType,
        txGroupId);
    return deleted;
  }

  /** 배치 전체 예약 취소 (소유자 검증 + 환불 포함) - 전체 취소: txGroupId 기반 전체 환불 */
  public int cancelScheduledBatch(String userKey, String regId) {
    // 1. 배치 조회 및 검증 (SMS DB)
    List<MsgQueue> messages = msgQueueMapper.findByUserKey(userKey);
    if (messages.isEmpty()) {
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "발송 정보를 찾을 수 없습니다.");
    }

    // 소유자 검증
    MsgQueue firstMsg = messages.get(0);
    if (!regId.equals(firstMsg.getExtCol3())) {
      log.warn(
          "배치 취소 권한 없음 - userKey: {}, 요청자: {}, 소유자: {}", userKey, regId, firstMsg.getExtCol3());
      throw new BusinessException(ErrorCode.ACCESS_DENIED, "해당 발송을 취소할 권한이 없습니다.");
    }

    // 대기 중인 메시지만 필터링
    List<MsgQueue> pendingMessages = messages.stream().filter(MsgQueue::isPending).toList();

    if (pendingMessages.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "취소 가능한 대기 중인 발송이 없습니다.");
    }

    // 설문 메시지 정리를 위해 삭제 전 extCol 정보 수집
    List<MsgQueue> surveyMessages =
        pendingMessages.stream()
            .filter(m -> m.getExtCol0() != null && m.getExtCol1() != null)
            .toList();

    String msgType = firstMsg.getMsgType();
    String txGroupId = firstMsg.getTxGroupId();
    int totalCount = messages.size();

    // 2. 메시지 삭제 (SMS DB)
    int deleted = deleteMsgQueueByUserKey(userKey);

    // 3. 환불 처리 (Primary DB)
    if (deleted > 0 && txGroupId != null) {
      if (deleted == totalCount) {
        // 전체 취소: txGroupId 기반 전체 환불
        refundByTxGroupId(txGroupId);
      } else {
        // 부분 취소: 취소 건수만큼 부분 환불
        refundPartial(regId, msgType, deleted, txGroupId);
      }
    }

    // 4. 설문 메시지인 경우 survey_user soft delete + sms_send 삭제
    if (deleted > 0) {
      for (MsgQueue msg : surveyMessages) {
        cleanupSurveyDataOnCancel(msg.getExtCol0(), msg.getExtCol1(), regId);
      }
    }

    log.info(
        "배치 예약 발송 취소 완료 - userKey: {}, count: {}, regId: {}, msgType: {}, txGroupId: {}",
        userKey,
        deleted,
        regId,
        msgType,
        txGroupId);
    return deleted;
  }

  /** 메시지 삭제 (SMS DB 트랜잭션) */
  @Transactional("smsTransactionManager")
  public int deleteMsgQueue(Integer mseq) {
    return msgQueueMapper.delete(mseq);
  }

  /** 메시지 배치 삭제 (SMS DB 트랜잭션) */
  @Transactional("smsTransactionManager")
  public int deleteMsgQueueByUserKey(String userKey) {
    return msgQueueMapper.deleteByUserKey(userKey);
  }

  /** txGroupId 기반 전체 환불 (원래 결제 화폐로 환불) */
  private void refundByTxGroupId(String txGroupId) {
    try {
      var result = walletService.refundByGroup(txGroupId);
      log.info(
          "txGroupId 기반 환불 완료 - txGroupId: {}, refundedAmount: {}, expiredAmount: {}",
          txGroupId,
          result.getRefundedAmount(),
          result.getExpiredAmount());
    } catch (Exception e) {
      log.error("txGroupId 기반 환불 실패 - txGroupId: {}, error: {}", txGroupId, e.getMessage(), e);
    }
  }

  /** 부분 환불 (취소 건수 × 단가로 CASH 환불) - 원래 화폐 추적이 어려우므로 CASH로 환불 */
  private void refundPartial(String userId, String msgType, int count, String txGroupId) {
    // userId는 실제로 userSeq임 (JWT subject로 seq 사용)
    Integer userSeq;
    try {
      userSeq = Integer.parseInt(userId);
    } catch (NumberFormatException e) {
      log.error("환불 처리 실패 - 잘못된 사용자 식별자: userId={}", userId);
      return;
    }

    try {
      String serviceId = getServiceIdFromMsgType(msgType);
      BigDecimal unitPrice = walletService.getAppliedRate(userSeq, serviceId);
      BigDecimal refundAmount = unitPrice.multiply(BigDecimal.valueOf(count));

      String comment =
          String.format(
              "%s 발송 취소 환불 %d건 (txGroupId: %s)", getMsgTypeName(msgType), count, txGroupId);
      walletService.refundToCash(userSeq, refundAmount, comment);

      log.info(
          "부분 환불 완료 - userId: {}, userSeq: {}, msgType: {}, count: {}, refundAmount: {}, txGroupId:"
              + " {}",
          userId,
          userSeq,
          msgType,
          count,
          refundAmount,
          txGroupId);
    } catch (Exception e) {
      log.error(
          "부분 환불 실패 - userId: {}, msgType: {}, count: {}, txGroupId: {}, error: {}",
          userId,
          msgType,
          count,
          txGroupId,
          e.getMessage(),
          e);
    }
  }

  /** 대기 중인 발송 목록 조회 */
  @Transactional(value = "smsTransactionManager", readOnly = true)
  public List<MsgQueue> getPendingMessages(String regId) {
    return msgQueueMapper.findPendingByRegId(regId);
  }

  /** 대기 중인 발송 목록 조회 (페이징) */
  @Transactional(value = "smsTransactionManager", readOnly = true)
  public PageResponse<MsgQueueResponse> getPendingMessages(String regId, int page, int size) {
    long total = msgQueueMapper.countPendingByRegId(regId);

    int offset = (page - 1) * size;
    List<MsgQueue> results = msgQueueMapper.findPendingByRegIdPaging(regId, offset, size);

    List<MsgQueueResponse> content = results.stream().map(MsgQueueResponse::from).toList();

    if (apiBaseUrl != null && !apiBaseUrl.isEmpty()) {
      content.forEach(r -> r.withFullImageUrls(apiBaseUrl));
    }

    return PageResponse.of(content, page, size, total);
  }

  /** 메시지 타입별 INSERT 분기 */
  private void insertMsgQueue(String msgType, MsgQueue msgQueue) {
    switch (msgType) {
      case "S" -> msgQueueMapper.insertSms(msgQueue);
      case "L" -> msgQueueMapper.insertLms(msgQueue);
      case "M" -> msgQueueMapper.insertMms(msgQueue);
      default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 메시지 타입입니다.");
    }
  }

  /** 전화번호 정규화 (하이픈 제거) */
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
   * @param reqType 발송 타입 ("0": 즉시, "1": 예약)
   * @param reqDate 예약 발송일시 (yyyyMMddHHmmss 또는 yyyy-MM-dd HH:mm:ss)
   * @param useUrlYn 이전 메시지 URL 추출 사용 여부 ("Y": 추출, "N": 처리 없음, null: 기존 동작)
   * @param regId 등록자 ID
   * @return mseq
   */
  @Transactional("smsTransactionManager")
  public int resendSurveyMessage(
      Integer userSeq,
      String subject,
      String text,
      String callback,
      boolean useOriginal,
      String reqType,
      String reqDate,
      String useUrlYn,
      String regId) {
    return resendSurveyMessage(
        userSeq,
        subject,
        text,
        callback,
        useOriginal,
        reqType,
        reqDate,
        useUrlYn,
        regId,
        null,
        null,
        null);
  }

  /** 설문 문자 재발송 (단건) - 대치문자 지원 */
  public int resendSurveyMessage(
      Integer userSeq,
      String subject,
      String text,
      String callback,
      boolean useOriginal,
      String reqType,
      String reqDate,
      String useUrlYn,
      String regId,
      String repChar01,
      String repChar02,
      String repChar03) {
    // SURVEY_USER에서 사용자 정보 조회
    SurveyUser surveyUser =
        surveyUserMapper
            .selectBySeq(userSeq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "설문 참여자 정보를 찾을 수 없습니다."));

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
      // 새 내용으로 발송
      if ("Y".equalsIgnoreCase(useUrlYn)) {
        // useUrlYn=Y: 이전 메시지에서 URL 추출하여 #유저키# 위치에 삽입
        List<String> tables = getResultTableNames(eventSeq);
        MsgResult previous = msgResultMapper.selectPreviousSend(tables, eventSeq, userSeq);

        if (previous == null) {
          throw new BusinessException(
              ErrorCode.RESOURCE_NOT_FOUND, "이전 발송 내역을 찾을 수 없습니다. (URL 추출 불가)");
        }

        String previousUrl = extractEpopkonUrl(previous.getText());
        if (previousUrl == null) {
          throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이전 발송 메시지에서 URL을 찾을 수 없습니다.");
        }

        finalText = text.replace("#유저키#", previousUrl).replace("#userKey#", previousUrl);
        log.info("이전 URL 추출 재발송 - userSeq: {}, url: {}", userSeq, previousUrl);
      } else if ("N".equalsIgnoreCase(useUrlYn)) {
        // useUrlYn=N: URL 처리 없이 텍스트 그대로 발송
        finalText = text;
        log.info("URL 미사용 재발송 - userSeq: {}", userSeq);
      } else {
        // useUrlYn 미지정 (기존 동작): #유저키# → userKey 치환 + URL 단축
        if (userKey == null || userKey.isEmpty()) {
          throw new BusinessException(ErrorCode.INVALID_INPUT, "userKey가 존재하지 않습니다.");
        }

        finalText = text.replace("#유저키#", userKey).replace("#userKey#", userKey);
        finalText = ShortUrlUtils.shortenUrlsInText(finalText, wiseadUrl);
        log.info("새 내용으로 재발송 - userSeq: {}, userKey: {}", userSeq, userKey);
      }

      finalSubject = subject;
      finalCallback = callback;
    }

    // 대치문자 처리 (#대치문자1#, #대치문자2#, #대치문자3# 치환)
    finalText = applyReplaceChars(finalText, repChar01, repChar02, repChar03);

    // 설문 요금 차감
    String txGroupId = deductForMessage(regId, "survey", 1, "L");

    // MSG_QUEUE에 등록
    String realUserId = userIdResolver.resolveUserId(regId);
    MsgQueue msgQueue =
        MsgQueue.createForSurvey(
            "L", // LMS로 발송
            normalizePhoneNumber(dstaddr),
            finalCallback,
            finalSubject,
            finalText,
            eventSeq,
            userSeq,
            txGroupId,
            realUserId);

    // 예약 발송 처리
    if ("1".equals(reqType) || "reserve".equalsIgnoreCase(reqType)) {
      if (reqDate == null || reqDate.isBlank()) {
        throw new BusinessException(ErrorCode.INVALID_INPUT, "예약 발송 시 reqDate는 필수입니다.");
      }
      LocalDateTime requestTime = parseResendRequestTime(reqDate);
      msgQueue = msgQueue.withRequestTime(requestTime);
      log.info("예약 재발송 설정 - userSeq: {}, requestTime: {}", userSeq, requestTime);
    }

    msgQueueMapper.insertLms(msgQueue);
    recordSmsSend(
        eventSeq,
        userSeq,
        finalSubject,
        finalText,
        "1",
        normalizePhoneNumber(dstaddr),
        finalCallback,
        regId);
    log.info("설문 재발송 완료 - userSeq: {}, mseq: {}", userSeq, msgQueue.getMseq());

    return msgQueue.getMseq();
  }

  /** 설문 문자 재발송 (다건) */
  @Transactional("smsTransactionManager")
  public ResendResponse resendSurveyMessageBatch(
      List<Integer> userSeqList,
      String subject,
      String text,
      String callback,
      boolean useOriginal,
      String reqType,
      String reqDate,
      String useUrlYn,
      String regId) {
    int successCount = 0;
    int failCount = 0;
    List<String> failedUserSeqs = new ArrayList<>();

    for (Integer userSeq : userSeqList) {
      try {
        resendSurveyMessage(
            userSeq, subject, text, callback, useOriginal, reqType, reqDate, useUrlYn, regId);
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
   * 중복 번호 재발송 (설문) 설문 발송 시 중복으로 실패한 번호들에게 재발송
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
      eventCode =
          surveyMasterMapper
              .selectByEventSeq(request.getEventSeq())
              .map(SurveyMaster::getEventCode)
              .orElse(null);
    }

    if (eventCode == null || eventCode.isEmpty()) {
      log.error("이벤트 코드를 찾을 수 없음 - eventSeq: {}", request.getEventSeq());
      return ResendResponse.fail("이벤트 정보를 찾을 수 없습니다.");
    }

    // 설문 요금 차감
    String txGroupId = deductForMessage(regId, "survey", receivers.size(), "L");

    int successCount = 0;
    int failCount = 0;
    List<String> failedList = new ArrayList<>();
    String realUserId = userIdResolver.resolveUserId(regId);

    for (ResendRequest.DuplicateReceiver receiver : receivers) {
      try {
        String phone = normalizePhoneNumber(receiver.getPhone());
        String text = request.getText();

        // 대치문자 및 유저키 처리
        text =
            applyReplaceChars(
                text, receiver.getRepChar01(), receiver.getRepChar02(), receiver.getRepChar03());
        text = applyUserKey(text, receiver.getUserKey());

        // URL 패턴을 찾아서 단축 URL로 변환
        text = ShortUrlUtils.shortenUrlsInText(text, wiseadUrl);

        log.debug("설문 문자 준비 완료 - eventCode: {}, userKey: {}", eventCode, receiver.getUserKey());

        // MSG_QUEUE에 등록
        MsgQueue msgQueue =
            MsgQueue.createForSurvey(
                "L", // LMS로 발송
                phone,
                request.getCallback(),
                request.getSubject(),
                text,
                request.getEventSeq(),
                receiver.getUserSeq(),
                txGroupId,
                realUserId);

        msgQueueMapper.insertLms(msgQueue);
        recordSmsSend(
            request.getEventSeq(),
            receiver.getUserSeq(),
            request.getSubject(),
            text,
            "1",
            phone,
            request.getCallback(),
            regId);
        successCount++;

      } catch (Exception e) {
        failCount++;
        failedList.add(receiver.getPhone());
        log.warn(
            "중복 번호 재발송 실패 - phone: {}, error: {}", maskPhone(receiver.getPhone()), e.getMessage());
      }
    }

    log.info("중복 번호 재발송 완료 - 성공: {}, 실패: {}", successCount, failCount);

    if (failCount == 0) {
      return ResendResponse.success(successCount);
    } else {
      return ResendResponse.partial(successCount, failCount, failedList);
    }
  }

  /** 설문 링크 생성 형식: {wisead.url}/auth/{eventCode}/{userKey} */
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

    List<ResendRequest.DuplicateReceiver> receivers = request.getDuplicateReceivers();
    if (receivers == null || receivers.isEmpty()) {
      log.warn("중복 수신자 목록이 비어있음");
      return ResendResponse.fail("발송할 수신자가 없습니다.");
    }

    // 이벤트 코드 조회
    String eventCode = request.getEventCode();
    if (eventCode == null || eventCode.isEmpty()) {
      eventCode =
          surveyMasterMapper
              .selectByEventSeq(request.getEventSeq())
              .map(SurveyMaster::getEventCode)
              .orElse(null);
    }

    if (eventCode == null || eventCode.isEmpty()) {
      log.error("이벤트 코드를 찾을 수 없음 - eventSeq: {}", request.getEventSeq());
      return ResendResponse.fail("이벤트 정보를 찾을 수 없습니다.");
    }

    // 설문 요금 차감
    String txGroupId = deductForMessage(regId, "survey", receivers.size(), "L");

    int successCount = 0;
    int failCount = 0;
    List<String> failedList = new ArrayList<>();
    String realUserId = userIdResolver.resolveUserId(regId);

    for (ResendRequest.DuplicateReceiver receiver : receivers) {
      try {
        String phone = normalizePhoneNumber(receiver.getPhone());

        // 새 SURVEY_USER 생성 (새 userKey로 새 설문 링크 발급)
        String encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(phone));
        String newUserKey = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        SurveyUser surveyUser =
            SurveyUser.builder()
                .eventSeq(request.getEventSeq())
                .userKey(newUserKey)
                .userPhone(encryptedPhone)
                .resendUserPhone(encryptedPhone)
                .delYn("N")
                .regId(regId)
                .build();
        surveyUserMapper.insert(surveyUser);
        Integer newUserSeq = surveyUser.getSeq();

        log.info(
            "중복 번호 신규 설문 대상자 등록 - eventSeq: {}, phone: {}, newUserSeq: {}, newUserKey: {}",
            request.getEventSeq(),
            maskPhone(phone),
            newUserSeq,
            newUserKey);

        String text = request.getText();

        // 대치문자 및 새 유저키 처리
        text =
            applyReplaceChars(
                text, receiver.getRepChar01(), receiver.getRepChar02(), receiver.getRepChar03());
        text = applyUserKey(text, newUserKey);

        // URL 패턴을 찾아서 단축 URL로 변환
        text = ShortUrlUtils.shortenUrlsInText(text, wiseadUrl);

        // MSG_QUEUE에 등록
        MsgQueue msgQueue =
            MsgQueue.createForSurvey(
                "L", // LMS로 발송
                phone,
                request.getCallback(),
                request.getSubject(),
                text,
                request.getEventSeq(),
                newUserSeq,
                txGroupId,
                realUserId);

        try {
          msgQueueMapper.insertLms(msgQueue);
        } catch (Exception e) {
          // 발송 실패 시 신규 등록된 설문유저 보상 삭제 (다른 DB 트랜잭션이라 자동 롤백 안됨)
          try {
            surveyUserMapper.softDelete(newUserSeq, regId);
            log.info("발송 실패로 설문 대상자 보상 삭제 - userSeq: {}", newUserSeq);
          } catch (Exception deleteEx) {
            log.warn("설문 대상자 보상 삭제 실패 - userSeq: {}, error: {}", newUserSeq, deleteEx.getMessage());
          }
          throw e;
        }
        recordSmsSend(
            request.getEventSeq(),
            newUserSeq,
            request.getSubject(),
            text,
            "1",
            phone,
            request.getCallback(),
            regId);
        successCount++;

      } catch (Exception e) {
        failCount++;
        failedList.add(receiver.getPhone());
        log.warn(
            "중복 번호 신규 발송 실패 - phone: {}, error: {}",
            maskPhone(receiver.getPhone()),
            e.getMessage());
      }
    }

    log.info("중복 번호 신규 발송 완료 - 성공: {}, 실패: {}", successCount, failCount);

    if (failCount == 0) {
      return ResendResponse.success(successCount);
    } else {
      return ResendResponse.partial(successCount, failCount, failedList);
    }
  }

  /** 이벤트 시퀀스 기반으로 조회할 msg_result 테이블명 목록 생성 설문 시작일 -1개월부터 현재 월까지, 실제 존재하는 테이블만 반환 */
  private List<String> getResultTableNames(Integer eventSeq) {
    DateTimeFormatter TABLE_MONTH_FMT = DateTimeFormatter.ofPattern("yyyyMM");
    LocalDate now = LocalDate.now();
    LocalDate startMonth = now.minusMonths(1); // 기본: 1개월 전

    // 설문 시작일 조회하여 검색 시작월 결정
    if (eventSeq != null) {
      try {
        SurveyMaster survey = surveyMasterMapper.selectByEventSeq(eventSeq).orElse(null);
        if (survey != null && survey.getStartDate() != null && !survey.getStartDate().isEmpty()) {
          LocalDate surveyStart = LocalDate.parse(survey.getStartDate().substring(0, 10));
          startMonth = surveyStart.minusMonths(1);
        }
      } catch (Exception e) {
        log.warn("설문 시작일 조회 실패 - eventSeq: {}, 기본 범위 사용", eventSeq);
      }
    }

    // startMonth ~ 현재월까지 테이블명 생성
    List<String> candidates = new ArrayList<>();
    LocalDate month = YearMonth.from(startMonth).atDay(1);
    LocalDate endMonth = YearMonth.from(now).atDay(1);
    while (!month.isAfter(endMonth)) {
      candidates.add("msg_result_" + month.format(TABLE_MONTH_FMT));
      month = month.plusMonths(1);
    }

    // 실제 존재하는 테이블만 필터링
    return candidates.stream()
        .filter(
            table -> {
              try {
                return msgResultMapper.tableExists(table) > 0;
              } catch (Exception e) {
                return false;
              }
            })
        .toList();
  }

  /** 재발송 예약 시간 파싱 - 파싱 실패 시 예외 발생 (예약이 즉시 발송으로 변환되는 것을 방지) */
  private LocalDateTime parseResendRequestTime(String reqDate) {
    try {
      return LocalDateTime.parse(reqDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    } catch (Exception e0) {
      try {
        return LocalDateTime.parse(reqDate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
      } catch (Exception e1) {
        try {
          return LocalDateTime.parse(reqDate, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        } catch (Exception e2) {
          log.error("재발송 예약 시간 파싱 실패: {}", reqDate);
          throw new BusinessException(ErrorCode.INVALID_INPUT, "예약 시간 형식이 올바르지 않습니다: " + reqDate);
        }
      }
    }
  }

  /** 텍스트에서 epopkon.com 도메인 URL 추출 (단축 URL 포함) */
  private String extractEpopkonUrl(String text) {
    if (text == null || text.isEmpty()) {
      return null;
    }
    Pattern pattern = Pattern.compile("https?://[\\w.-]*epopkon\\.com[^\\s]*");
    Matcher matcher = pattern.matcher(text);
    if (matcher.find()) {
      return matcher.group();
    }
    return null;
  }

  /**
   * 설문 문자 발송 (다건) - 단축 URL 적용
   *
   * @param request 발송 요청
   * @param regId 등록자 ID
   * @return 발송 결과
   */
  @Transactional("smsTransactionManager")
  public SurveyMessageResponse sendSurveyMessages(SurveyMessageRequest request, String regId) {
    log.info(
        "설문 문자 발송 시작 - eventSeq: {}, regId: {}, count: {}",
        request.getEventSeq(),
        regId,
        request.getReceivers() != null ? request.getReceivers().size() : 0);

    if (request.getReceivers() == null || request.getReceivers().isEmpty()) {
      return SurveyMessageResponse.fail("발송 대상이 없습니다.");
    }

    List<SurveyMessageRequest.Receiver> receivers = request.getReceivers();
    int duplicateCount = 0;

    // 중복 번호 제거
    if (request.isDelDuplicateNum()) {
      Map<String, SurveyMessageRequest.Receiver> uniqueMap = new LinkedHashMap<>();
      for (SurveyMessageRequest.Receiver r : receivers) {
        String phone = r.getNormalizedPhone();
        if (uniqueMap.putIfAbsent(phone, r) != null) {
          duplicateCount++;
        }
      }
      receivers = new ArrayList<>(uniqueMap.values());
      log.info(
          "중복번호 제거 - 원본: {}, 제거: {}, 결과: {}",
          request.getReceivers().size(),
          duplicateCount,
          receivers.size());
    }

    // 잔액 확인 및 차감 (설문 요금 적용)
    String txGroupId = deductForMessage(regId, "survey", receivers.size(), "L");
    int successCount = 0;
    int failCount = 0;
    List<String> failedPhones = new ArrayList<>();
    List<Integer> mseqList = new ArrayList<>();
    String realUserId = userIdResolver.resolveUserId(regId);

    for (SurveyMessageRequest.Receiver receiver : receivers) {
      try {
        String phone = receiver.getNormalizedPhone();
        Integer userSeq = receiver.getUserSeq();
        String userKey = receiver.getUserKey();
        boolean newlyCreatedUser = false;

        // SURVEY_USER 자동 등록 (userSeq가 없는 경우)
        if (userSeq == null && phone != null) {
          try {
            String encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(phone));
            // 기존 SURVEY_USER 조회 (같은 이벤트 + 같은 전화번호)
            Optional<SurveyUser> existing =
                surveyUserMapper.selectByResendUserPhone(request.getEventSeq(), encryptedPhone);
            if (existing.isPresent()) {
              userSeq = existing.get().getSeq();
              if (userKey == null || userKey.isEmpty()) {
                userKey = existing.get().getUserKey();
              }
            } else {
              // 신규 SURVEY_USER 생성
              String newUserKey = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
              SurveyUser surveyUser =
                  SurveyUser.builder()
                      .eventSeq(request.getEventSeq())
                      .userKey(newUserKey)
                      .userPhone(encryptedPhone)
                      .resendUserPhone(encryptedPhone)
                      .delYn("N")
                      .regId(regId)
                      .build();
              surveyUserMapper.insert(surveyUser);
              userSeq = surveyUser.getSeq();
              userKey = newUserKey;
              newlyCreatedUser = true;
              log.info(
                  "설문 대상자 자동 등록 - eventSeq: {}, phone: {}, userSeq: {}",
                  request.getEventSeq(),
                  maskPhone(phone),
                  userSeq);
            }
          } catch (Exception e) {
            log.warn("설문 대상자 자동 등록 실패 - phone: {}, error: {}", maskPhone(phone), e.getMessage());
          }
        }

        String text = request.getText();

        // 대치문자 및 유저키 처리
        text =
            applyReplaceChars(
                text, receiver.getRepChar01(), receiver.getRepChar02(), receiver.getRepChar03());
        text = applyUserKey(text, userKey);

        // URL 패턴을 찾아서 단축 URL로 변환
        text = ShortUrlUtils.shortenUrlsInText(text, wiseadUrl);

        // MSG_QUEUE에 등록 (설문 문자는 LMS 전용)
        MsgQueue msgQueue =
            MsgQueue.createForSurvey(
                "L", // LMS 고정
                phone,
                request.getNormalizedCallback(),
                request.getSubject(),
                text,
                request.getEventSeq(),
                userSeq,
                txGroupId,
                realUserId);

        // 예약 발송 시간 설정
        if (!request.isImmediate() && request.getRequestTime() != null) {
          msgQueue = msgQueue.withRequestTime(request.getRequestTime());
        }

        try {
          // LMS로 발송
          msgQueueMapper.insertLms(msgQueue);
        } catch (Exception e) {
          // 발송 실패 시 신규 등록된 설문유저 보상 삭제 (다른 DB 트랜잭션이라 자동 롤백 안됨)
          if (newlyCreatedUser && userSeq != null) {
            try {
              surveyUserMapper.softDelete(userSeq, regId);
              log.info("발송 실패로 설문 대상자 보상 삭제 - userSeq: {}", userSeq);
            } catch (Exception deleteEx) {
              log.warn("설문 대상자 보상 삭제 실패 - userSeq: {}, error: {}", userSeq, deleteEx.getMessage());
            }
          }
          throw e;
        }
        recordSmsSend(
            request.getEventSeq(),
            userSeq,
            request.getSubject(),
            text,
            "1",
            phone,
            request.getNormalizedCallback(),
            regId);

        mseqList.add(msgQueue.getMseq());
        successCount++;

      } catch (Exception e) {
        failCount++;
        failedPhones.add(receiver.getPhone());
        log.warn(
            "설문 문자 발송 실패 - phone: {}, error: {}", maskPhone(receiver.getPhone()), e.getMessage());
      }
    }

    log.info(
        "설문 문자 발송 완료 - 성공: {}, 실패: {}, 중복: {}, txGroupId: {}",
        successCount,
        failCount,
        duplicateCount,
        txGroupId);

    if (failCount == 0 && duplicateCount == 0) {
      return SurveyMessageResponse.success(successCount, mseqList, txGroupId, LocalDateTime.now());
    } else {
      return SurveyMessageResponse.partial(
          successCount, failCount, duplicateCount, failedPhones, mseqList, txGroupId);
    }
  }

  /**
   * 행사참여자 문자 발송 (다건) - 단축 URL 적용
   *
   * @param request 발송 요청
   * @param regId 등록자 ID
   * @return 발송 결과
   */
  @Transactional("smsTransactionManager")
  public EventMessageResponse sendEventMessages(EventMessageRequest request, String regId) {
    log.info(
        "행사참여자 문자 발송 시작 - eventSeq: {}, regId: {}, sendType: {}, count: {}",
        request.getEventSeq(),
        regId,
        request.getSendType(),
        request.getReceivers() != null ? request.getReceivers().size() : 0);

    if (request.getReceivers() == null || request.getReceivers().isEmpty()) {
      return EventMessageResponse.fail("발송 대상이 없습니다.");
    }

    List<EventMessageRequest.Receiver> receivers = request.getReceivers();
    int duplicateCount = 0;

    // 중복 번호 제거
    if (request.isDelDuplicateNum()) {
      Map<String, Receiver> uniqueMap = new LinkedHashMap<>();
      for (EventMessageRequest.Receiver r : receivers) {
        String phone = r.getNormalizedPhone();
        if (uniqueMap.putIfAbsent(phone, r) != null) {
          duplicateCount++;
        }
      }
      receivers = new ArrayList<>(uniqueMap.values());
      log.info(
          "중복번호 제거 - 원본: {}, 제거: {}, 결과: {}",
          request.getReceivers().size(),
          duplicateCount,
          receivers.size());
    }

    // 잔액 확인 및 차감 (행사참여자 문자 전용 요금)
    String txGroupId = deductForMessage(regId, "event_attendance", receivers.size(), "L");
    int successCount = 0;
    int failCount = 0;
    List<String> failedPhones = new ArrayList<>();
    List<Integer> mseqList = new ArrayList<>();
    String realUserId = userIdResolver.resolveUserId(regId);

    for (EventMessageRequest.Receiver receiver : receivers) {
      try {
        String phone = receiver.getNormalizedPhone();

        // 비참여자 자동 등록
        Long participantSeq = receiver.getParticipantSeq();
        Integer surveyUserSeq = receiver.getSurveyUserSeq();

        String checkCode = receiver.getCheckCode();

        if (participantSeq == null && phone != null) {
          try {
            String receiverName = receiver.getName() != null ? receiver.getName() : "";
            ParticipantForMessageResponse registered =
                eventParticipantService.registerParticipantForMessage(
                    request.getEventSeq(), receiverName, phone, regId);
            participantSeq = registered.getParticipantSeq();
            surveyUserSeq = registered.getSurveyUserSeq();
            checkCode = registered.getCheckCode();
            log.info(
                "비참여자 자동 등록 - phone: {}, participantSeq: {}", maskPhone(phone), participantSeq);
          } catch (Exception e) {
            log.warn("비참여자 자동 등록 실패 - phone: {}, error: {}", maskPhone(phone), e.getMessage());
          }
        }

        String text = request.getText();

        // 이벤트 정보 치환
        if (request.getEventName() != null) {
          text = text.replace("#이벤트명#", request.getEventName());
        }
        if (request.getEventPeriod() != null) {
          text = text.replace("#이벤트기간#", request.getEventPeriod());
        }
        if (request.getEventLocation() != null) {
          text = text.replace("#이벤트장소#", request.getEventLocation());
        }

        // 참가자 정보 치환
        if (receiver.getName() != null) {
          text = text.replace("#이름#", receiver.getName());
        }

        // 대치문자 처리
        if (receiver.getRepChar01() != null && !receiver.getRepChar01().isEmpty()) {
          text = text.replace("#대치문자1#", receiver.getRepChar01());
        }
        if (receiver.getRepChar02() != null && !receiver.getRepChar02().isEmpty()) {
          text = text.replace("#대치문자2#", receiver.getRepChar02());
        }

        // 행사관리 전용 대치번호 치환
        if (receiver.getRepChar01() != null && !receiver.getRepChar01().isEmpty()) {
          text = text.replace("#대치번호1#", receiver.getRepChar01());
        }
        if (receiver.getRepChar02() != null && !receiver.getRepChar02().isEmpty()) {
          text = text.replace("#대치번호2#", receiver.getRepChar02());
        }

        // QR링크 치환 (단축 URL 적용)
        if (receiver.getQrLink() != null && !receiver.getQrLink().isEmpty()) {
          String shortenedQrLink = ShortUrlUtils.shortenUrl(receiver.getQrLink());
          text = text.replace("#QR링크#", shortenedQrLink);
        }

        // 접속링크 치환 (단축 URL 적용)
        if (receiver.getAccessLink() != null && !receiver.getAccessLink().isEmpty()) {
          String shortenedAccessLink = ShortUrlUtils.shortenUrl(receiver.getAccessLink());
          text = text.replace("#접속링크#", shortenedAccessLink);
        }

        // 체크코드 치환 (#check# → 참가자 checkCode)
        if (checkCode != null && !checkCode.isEmpty()) {
          text = text.replace("#check#", checkCode);
        }

        // /qrcode/ 패턴 URL도 단축 처리
        text = ShortUrlUtils.shortenUrlsInText(text, wiseadUrl);

        // MSG_QUEUE에 등록 (LMS 전용)
        MsgQueue msgQueue =
            MsgQueue.createForEvent(
                phone,
                request.getNormalizedCallback(),
                request.getSubject(),
                text,
                request.getEventSeq(),
                participantSeq,
                surveyUserSeq,
                txGroupId,
                realUserId);

        // 예약 발송 시간 설정
        if (!request.isImmediate() && request.getRequestTime() != null) {
          msgQueue = msgQueue.withRequestTime(request.getRequestTime());
        }

        // LMS로 발송
        msgQueueMapper.insertLms(msgQueue);
        recordSmsSend(
            request.getEventSeq(),
            surveyUserSeq,
            request.getSubject(),
            text,
            "1",
            phone,
            request.getNormalizedCallback(),
            regId);

        mseqList.add(msgQueue.getMseq());
        successCount++;

      } catch (Exception e) {
        failCount++;
        failedPhones.add(receiver.getPhone());
        log.warn(
            "행사참여자 문자 발송 실패 - phone: {}, error: {}",
            maskPhone(receiver.getPhone()),
            e.getMessage());
      }
    }

    log.info(
        "행사참여자 문자 발송 완료 - 성공: {}, 실패: {}, 중복: {}, txGroupId: {}",
        successCount,
        failCount,
        duplicateCount,
        txGroupId);

    if (failCount == 0 && duplicateCount == 0) {
      return EventMessageResponse.success(successCount, mseqList, txGroupId, LocalDateTime.now());
    } else {
      return EventMessageResponse.partial(
          successCount, failCount, duplicateCount, failedPhones, mseqList, txGroupId);
    }
  }

  // ==================== Private Helper Methods ====================

  /** 예약 발송 취소 시 설문 관련 데이터 정리 (survey_user soft delete + sms_send 삭제) */
  private void cleanupSurveyDataOnCancel(Integer eventSeq, String extCol1, String regId) {
    if (eventSeq == null || extCol1 == null) {
      return;
    }
    try {
      Integer userSeq = Integer.parseInt(extCol1);
      surveyUserMapper.softDelete(userSeq, regId);
      smsSendMapper.deleteByEventSeqAndUserSeq(eventSeq, userSeq);
      log.info("설문 발송 취소 데이터 정리 완료 - eventSeq: {}, userSeq: {}", eventSeq, userSeq);
    } catch (NumberFormatException e) {
      log.warn("설문 발송 취소 데이터 정리 실패 - extCol1이 숫자가 아닙니다: {}", extCol1);
    } catch (Exception e) {
      log.warn(
          "설문 발송 취소 데이터 정리 실패 - eventSeq: {}, extCol1: {}, error: {}",
          eventSeq,
          extCol1,
          e.getMessage());
    }
  }

  private void recordSmsSend(
      Integer eventSeq,
      Integer userSeq,
      String subject,
      String content,
      String sendType,
      String receivedNum,
      String callback,
      String regId) {
    if (eventSeq == null || userSeq == null) {
      return;
    }
    try {
      SmsSend smsSend =
          SmsSend.create(
              eventSeq, userSeq, subject, content, sendType, receivedNum, callback, regId);
      smsSendMapper.insert(smsSend);
    } catch (Exception e) {
      log.warn(
          "sms_send 발송 이력 기록 실패 - eventSeq: {}, userSeq: {}, error: {}",
          eventSeq,
          userSeq,
          e.getMessage());
    }
  }

  /** 대치문자 처리 (#대치문자1#, #대치문자2#, #대치문자3# 치환) */
  private String applyReplaceChars(
      String text, String repChar01, String repChar02, String repChar03) {
    if (text == null) {
      return null;
    }
    if (repChar01 != null && !repChar01.isEmpty()) {
      text = text.replace("#대치문자1#", repChar01);
    }
    if (repChar02 != null && !repChar02.isEmpty()) {
      text = text.replace("#대치문자2#", repChar02);
    }
    if (repChar03 != null && !repChar03.isEmpty()) {
      text = text.replace("#대치문자3#", repChar03);
    }
    return text;
  }

  /** 유저키 치환 (#유저키#, #userKey# -> userKey) */
  private String applyUserKey(String text, String userKey) {
    if (text == null || userKey == null || userKey.isEmpty()) {
      return text;
    }
    return text.replace("#유저키#", userKey).replace("#userKey#", userKey);
  }

  /** 전화번호 마스킹 (로그용) */
  private String maskPhone(String phone) {
    return kr.wisead.common.util.CommonUtils.maskingPhone(phone);
  }
}
