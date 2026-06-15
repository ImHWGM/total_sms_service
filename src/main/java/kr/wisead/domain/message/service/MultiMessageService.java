package kr.wisead.domain.message.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CommonUtils;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.file.service.FileStorageService;
import kr.wisead.domain.message.dto.MultiMessageRequest;
import kr.wisead.domain.message.dto.MultiMessageResponse;
import kr.wisead.domain.message.entity.MsgQueue;
import kr.wisead.domain.payment.service.BalanceService;
import kr.wisead.domain.profanity.service.ProfanityFilterService;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.BlockedSenderMapper;
import kr.wisead.mapper.primary.UserMapper;
import kr.wisead.mapper.sms.MsgQueueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 일반 문자(Multi Message) 발송 서비스 SMS/LMS/MMS 각각 다른 단가 적용 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultiMessageService {

  private final MsgQueueMapper msgQueueMapper;
  private final BalanceService balanceService;
  private final BlockedSenderMapper blockedSenderMapper;
  private final UserMapper userMapper;
  private final FileStorageService fileStorageService;
  private final UserIdResolver userIdResolver;
  private final ProfanityFilterService profanityFilterService;

  // 야간 전송제한 시간 (20:00 ~ 09:00)
  private static final LocalTime NIGHT_START = LocalTime.of(20, 0);
  private static final LocalTime NIGHT_END = LocalTime.of(9, 0);

  /** 일반 문자 발송 (직접 입력 방식) */
  @Transactional(value = "smsTransactionManager", rollbackFor = Exception.class)
  public MultiMessageResponse sendDirectMessage(MultiMessageRequest request, String regId) {
    log.info("Multi 메시지 발송 시작 - regId: {}, type: {}", regId, request.getMessageType());

    // 0. 야간 전송제한 체크 (즉시 발송인 경우)
    if (request.isImmediate() && isNightTime()) {
      log.warn("야간 전송제한 시간입니다 (20:00 ~ 09:00) - regId: {}", regId);
      return MultiMessageResponse.error("야간 전송제한 시간입니다. (20:00 ~ 09:00)\n해당 시간에는 예약발송만 가능합니다.");
    }

    // 0-1. regId 는 user_id(alpha)
    Integer userSeq = userIdResolver.toUserSeq(regId);
    if (userSeq == null) {
      log.warn("잘못된 사용자 식별자 - regId: {}", regId);
      return MultiMessageResponse.error("사용자 정보를 찾을 수 없습니다.");
    }

    // 0-2. 금칙어 검사 (SEND: 일반 문자 발송)
    profanityFilterService.validateForSend(
        request.getText(),
        userSeq,
        null,
        null,
        null,
        request.getReceivers() != null ? request.getReceivers().size() : null);

    // 1. 잔액 조회
    var balanceResponse = balanceService.getCurrentBalance(userSeq);
    if (balanceResponse == null) {
      log.warn("잔액 정보 없음 - regId: {}", regId);
      return MultiMessageResponse.insufficientBalance();
    }

    // 2. 수신자 목록 준비 (중복 제거 처리)
    List<MultiMessageRequest.ReceiverInfo> receivers = request.getReceivers();
    int duplicateCount = 0;

    if (request.isRemoveDuplicate() && receivers != null) {
      Map<String, MultiMessageRequest.ReceiverInfo> uniqueMap = new LinkedHashMap<>();
      for (MultiMessageRequest.ReceiverInfo receiver : receivers) {
        String phone = normalizePhoneNumber(receiver.getPhone());
        if (uniqueMap.putIfAbsent(phone, receiver) != null) {
          duplicateCount++;
        }
      }
      receivers = new ArrayList<>(uniqueMap.values());
    }

    if (receivers == null || receivers.isEmpty()) {
      return MultiMessageResponse.error("수신자 목록이 비어있습니다.");
    }

    // 2-1. 수신거부 번호 필터링
    int blockedCount = 0;
    String storeCode = getStoreCode(regId);
    if (storeCode != null && !storeCode.isEmpty()) {
      List<String> phoneNumbers =
          receivers.stream()
              .map(r -> normalizePhoneNumber(r.getPhone()))
              .collect(Collectors.toList());
      List<String> blockedNumbers =
          blockedSenderMapper.selectBlockedNumbers(storeCode, phoneNumbers);

      if (blockedNumbers != null && !blockedNumbers.isEmpty()) {
        Set<String> blockedSet = new HashSet<>(blockedNumbers);
        List<MultiMessageRequest.ReceiverInfo> filteredReceivers = new ArrayList<>();

        for (MultiMessageRequest.ReceiverInfo receiver : receivers) {
          String phone = normalizePhoneNumber(receiver.getPhone());
          if (!blockedSet.contains(phone)) {
            filteredReceivers.add(receiver);
          } else {
            blockedCount++;
          }
        }
        receivers = filteredReceivers;
        log.info("수신거부 번호 {} 건 필터링 완료 - regId: {}", blockedCount, regId);
      }
    }

    if (receivers.isEmpty()) {
      return MultiMessageResponse.error("모든 수신자가 수신거부 처리되어 발송할 대상이 없습니다.");
    }

    // 3. 요금 계산 및 잔액 확인
    BigDecimal unitPrice = getUnitPrice(balanceResponse, request.getMessageType());
    BigDecimal totalCharge = unitPrice.multiply(BigDecimal.valueOf(receivers.size()));

    if (!balanceService.hasEnoughBalance(userSeq, totalCharge)) {
      log.warn("잔액 부족 - 필요: {}, 보유: {}", totalCharge, balanceResponse.getTotalBalance());
      return MultiMessageResponse.insufficientBalance();
    }

    // 4. 배치 ID 및 txGroupId 생성
    String batchId = generateBatchId();
    String txGroupId = UUID.randomUUID().toString().replace("-", "");
    int successCount = 0;

    // 5. 메시지 발송 등록 (txGroupId를 MsgQueue에 저장)
    for (MultiMessageRequest.ReceiverInfo receiver : receivers) {
      try {
        String phone = normalizePhoneNumber(receiver.getPhone());
        String text = applyReplaceChars(request.getText(), receiver);

        MsgQueue msgQueue = createMsgQueue(request, phone, text, batchId, txGroupId, regId);

        // 예약 발송 설정
        if (!request.isImmediate() && request.getReqDate() != null) {
          LocalDateTime requestTime = parseRequestTime(request.getReqDate());
          msgQueue = msgQueue.withRequestTime(requestTime);
        }

        // 메시지 타입별 INSERT
        insertMsgQueue(request.getMsgTypeCode(), msgQueue);
        successCount++;
      } catch (Exception e) {
        log.error(
            "메시지 등록 실패 - phone: {}, error: {}",
            CommonUtils.maskingPhone(receiver.getPhone()),
            e.getMessage());
      }
    }

    // 6. 잔액 차감 (동일한 txGroupId로 차감하여 취소 시 환불 추적 가능)
    if (successCount > 0) {
      BigDecimal chargedAmount = unitPrice.multiply(BigDecimal.valueOf(successCount));
      String msgType =
          request.getMessageType() != null ? request.getMessageType().toUpperCase() : "SMS";
      String comment = "문자발송 : " + msgType + "  " + successCount + "건";

      balanceService.deductMessageChargeWithTxGroupId(
          userSeq, successCount, msgType, comment, txGroupId, regId);

      log.info(
          "Multi 메시지 발송 완료 - 성공: {}, 중복: {}, 수신거부: {}, 차감: {}, txGroupId: {}",
          successCount,
          duplicateCount,
          blockedCount,
          chargedAmount,
          txGroupId);

      return MultiMessageResponse.success(
          successCount, duplicateCount, blockedCount, batchId, chargedAmount);
    }

    return MultiMessageResponse.error("발송 등록에 실패하였습니다.");
  }

  /** 야간 전송제한 시간 체크 (20:00 ~ 09:00) - AdMessageService와 동일한 로직 */
  private boolean isNightTime() {
    LocalTime now = LocalTime.now();
    // 20:00 이후이거나 09:00 이전이면 야간
    return now.isAfter(NIGHT_START) || now.isBefore(NIGHT_END);
  }

  /** 사용자의 storeCode 조회 (regId 는 user_id) */
  private String getStoreCode(String regId) {
    return userMapper.findByUserId(regId).map(User::getStoreCode).orElse(null);
  }

  /** 메시지 타입별 단가 조회 (BalanceResponse 사용) */
  private BigDecimal getUnitPrice(
      kr.wisead.domain.payment.dto.BalanceResponse balance, String messageType) {
    if (messageType == null) return balance.getSubtractUnitPrice();

    return switch (messageType.toUpperCase()) {
      case "SMS" ->
          balance.getSmsPrice() != null ? balance.getSmsPrice() : balance.getSubtractUnitPrice();
      case "LMS" ->
          balance.getLmsPrice() != null ? balance.getLmsPrice() : balance.getSubtractUnitPrice();
      case "MMS" ->
          balance.getMmsPrice() != null ? balance.getMmsPrice() : balance.getSubtractUnitPrice();
      default -> balance.getSubtractUnitPrice();
    };
  }

  /** 대치문자 적용 */
  private String applyReplaceChars(String text, MultiMessageRequest.ReceiverInfo receiver) {
    if (text == null) return null;

    String result = text;
    if (receiver.getRepChar01() != null && !receiver.getRepChar01().isEmpty()) {
      result = result.replace("#대치문자1#", receiver.getRepChar01());
    }
    if (receiver.getRepChar02() != null && !receiver.getRepChar02().isEmpty()) {
      result = result.replace("#대치문자2#", receiver.getRepChar02());
    }
    if (receiver.getRepChar03() != null && !receiver.getRepChar03().isEmpty()) {
      result = result.replace("#대치문자3#", receiver.getRepChar03());
    }
    return result;
  }

  /**
   * MsgQueue 생성
   *
   * @param txGroupId 결제 거래 그룹 ID (환불 추적용)
   */
  private MsgQueue createMsgQueue(
      MultiMessageRequest request,
      String phone,
      String text,
      String batchId,
      String txGroupId,
      String regId) {
    return switch (request.getMsgTypeCode()) {
      case "L" ->
          MsgQueue.createLms(
              phone,
              request.getNormalizedCallback(),
              request.getSubject(),
              text,
              batchId,
              txGroupId,
              regId);
      case "M" ->
          MsgQueue.createMms(
              phone,
              request.getNormalizedCallback(),
              request.getSubject(),
              text,
              request.getFileCnt() != null ? request.getFileCnt() : 0,
              fileStorageService.normalizeMmsFileUrl(request.getFileLoc1()),
              fileStorageService.normalizeMmsFileUrl(request.getFileLoc2()),
              fileStorageService.normalizeMmsFileUrl(request.getFileLoc3()),
              batchId,
              txGroupId,
              regId);
      default ->
          MsgQueue.createSms(
              phone,
              request.getNormalizedCallback(),
              request.getSubject(),
              text,
              batchId,
              txGroupId,
              regId);
    };
  }

  /** 메시지 타입별 INSERT */
  private void insertMsgQueue(String msgType, MsgQueue msgQueue) {
    switch (msgType) {
      case "S" -> msgQueueMapper.insertSms(msgQueue);
      case "L" -> msgQueueMapper.insertLms(msgQueue);
      case "M" -> msgQueueMapper.insertMms(msgQueue);
      default -> msgQueueMapper.insertSms(msgQueue);
    }
  }

  /** 배치 ID 생성 */
  private String generateBatchId() {
    LocalDateTime now = LocalDateTime.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS");
    return now.format(formatter);
  }

  /** 전화번호 정규화 (하이픈 제거) */
  private String normalizePhoneNumber(String phone) {
    return phone != null ? phone.replaceAll("-", "") : null;
  }

  /** 예약 시간 파싱 - 파싱 실패 시 예외 발생 (예약이 즉시 발송으로 변환되는 것을 방지) */
  private LocalDateTime parseRequestTime(String reqDate) {
    try {
      return LocalDateTime.parse(reqDate, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    } catch (Exception e0) {
      try {
        return LocalDateTime.parse(reqDate, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
      } catch (Exception e1) {
        try {
          return LocalDateTime.parse(reqDate, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        } catch (Exception e2) {
          log.error("예약 시간 파싱 실패: {}", reqDate);
          throw new BusinessException(ErrorCode.INVALID_INPUT, "예약 시간 형식이 올바르지 않습니다: " + reqDate);
        }
      }
    }
  }
}
