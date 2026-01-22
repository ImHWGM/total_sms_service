package kr.wisead.domain.message.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.ars.service.BlockedNumberService;
import kr.wisead.domain.message.dto.AdMessageRequest;
import kr.wisead.domain.message.dto.AdMessageResponse;
import kr.wisead.domain.message.entity.MsgQueue;
import kr.wisead.domain.payment.dto.BalanceResponse;
import kr.wisead.domain.payment.service.BalanceService;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.UserMapper;
import kr.wisead.mapper.sms.MsgQueueMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 광고 문자 발송 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdMessageService {

  private final MsgQueueMapper msgQueueMapper;
  private final UserMapper userMapper;
  private final BalanceService balanceService;
  private final BlockedNumberService blockedNumberService;

  /** 야간 전송제한 시간 (20:00 ~ 09:00) */
  private static final LocalTime NIGHT_START = LocalTime.of(20, 0);

  private static final LocalTime NIGHT_END = LocalTime.of(9, 0);

  /**
   * 야간 전송제한 시간 체크
   *
   * @return true: 야간시간대 (전송 불가)
   */
  public boolean isNightTimeRestriction() {
    LocalTime now = LocalTime.now();
    return now.isAfter(NIGHT_START) || now.isBefore(NIGHT_END);
  }

  /** 광고 문자 발송 (직접 등록) */
  @Transactional
  public AdMessageResponse sendDirectMessage(AdMessageRequest request, String userId) {
    log.info(
        "광고문자 발송 시작 - userId: {}, type: {}, count: {}",
        userId,
        request.getMessageTypeIs(),
        request.getRecipients() != null ? request.getRecipients().size() : 0);

    // 1. 야간 전송제한 체크 (즉시 발송일 경우만)
    if (request.isImmediate() && isNightTimeRestriction()) {
      log.warn("야간 전송제한 시간 - userId: {}", userId);
      return AdMessageResponse.nightTimeRestricted();
    }

    if (request.getRecipients() == null || request.getRecipients().isEmpty()) {
      return AdMessageResponse.fail(-1, "발송 대상이 없습니다.");
    }

    // 2. 사용자의 상점코드 조회 (userId는 실제로 userSeq임 - JWT subject로 seq 사용)
    String storeCode = null;
    try {
      Integer userSeq = Integer.parseInt(userId);
      storeCode = userMapper.findBySeq(userSeq).map(User::getStoreCode).orElse(null);
    } catch (NumberFormatException e) {
      log.warn("잘못된 사용자 식별자 - userId: {}", userId);
    }
    if (storeCode == null || storeCode.isBlank()) {
      log.warn("상점코드 없음 - userId: {}", userId);
      storeCode = "DEFAULT";
    }

    // 3. 중복번호 제거
    List<AdMessageRequest.Recipient> recipients = request.getRecipients();
    int duplicateCount = 0;

    if (request.shouldDeleteDuplicate()) {
      Map<String, AdMessageRequest.Recipient> uniqueMap = new LinkedHashMap<>();
      for (AdMessageRequest.Recipient r : recipients) {
        String phone = r.getNormalizedPhone();
        if (uniqueMap.putIfAbsent(phone, r) != null) {
          duplicateCount++;
        }
      }
      recipients = new ArrayList<>(uniqueMap.values());
      log.info(
          "중복번호 제거 - 원본: {}, 제거: {}, 결과: {}",
          request.getRecipients().size(),
          duplicateCount,
          recipients.size());
    }

    // 4. 수신거부 번호 필터링
    List<String> phoneList =
        recipients.stream()
            .map(AdMessageRequest.Recipient::getNormalizedPhone)
            .collect(Collectors.toList());

    List<String> blockedPhones = blockedNumberService.filterBlockedNumbers(storeCode, phoneList);
    Set<String> blockedSet = new HashSet<>(blockedPhones);

    List<String> maskedBlockedNumbers =
        blockedPhones.stream().map(this::maskPhoneNumber).collect(Collectors.toList());

    // 수신거부 번호 제외
    List<AdMessageRequest.Recipient> filteredRecipients =
        recipients.stream()
            .filter(r -> !blockedSet.contains(r.getNormalizedPhone()))
            .collect(Collectors.toList());

    int blockedCount = recipients.size() - filteredRecipients.size();
    log.info("수신거부 필터링 - 제외: {}건", blockedCount);

    // 5. 모든 번호가 차단된 경우
    if (filteredRecipients.isEmpty()) {
      log.warn("모든 번호가 수신거부로 차단됨 - userId: {}", userId);
      return AdMessageResponse.allBlocked(blockedCount, maskedBlockedNumbers);
    }

    // 6. 잔액 확인
    BalanceResponse latestBalance = balanceService.getCurrentBalance(userId);
    if (latestBalance == null) {
      return AdMessageResponse.insufficientBalance("잔액 정보가 없습니다.\n요금 충전 후 서비스 이용이 가능합니다.");
    }

    BigDecimal unitPrice = getUnitPrice(latestBalance, request.getMsgTypeLabel());
    BigDecimal totalCharge = unitPrice.multiply(BigDecimal.valueOf(filteredRecipients.size()));

    if (!balanceService.hasEnoughBalance(userId, totalCharge)) {
      return AdMessageResponse.insufficientBalance(
          String.format(
              "충전 금액이 부족합니다.\n필요: %s원, 잔액: %s원",
              totalCharge.stripTrailingZeros().toPlainString(),
              latestBalance.getTotalBalance().stripTrailingZeros().toPlainString()));
    }

    // 7. 메시지 발송 등록 (txGroupId를 MsgQueue에 저장)
    String batchId = generateBatchId();
    String txGroupId = UUID.randomUUID().toString().replace("-", "");
    int successCount = 0;

    for (AdMessageRequest.Recipient recipient : filteredRecipients) {
      try {
        String content = recipient.getProcessedContent(request.getContTxt());

        MsgQueue msgQueue =
            createMsgQueue(
                request.getMsgTypeCode(),
                recipient.getNormalizedPhone(),
                request.getNormalizedCallback(),
                request.getSendTtl(),
                content,
                request.getFileCnt(),
                request.getFileloc1(),
                request.getFileloc2(),
                request.getFileloc3(),
                batchId,
                txGroupId,
                userId);

        // 예약 발송 시간 설정
        if (!request.isImmediate() && request.getReqDate() != null) {
          msgQueue = msgQueue.withRequestTime(request.getReqDate());
        }

        insertMsgQueue(request.getMsgTypeCode(), msgQueue);
        successCount++;

      } catch (Exception e) {
        log.error(
            "메시지 등록 실패 - phone: {}, error: {}",
            maskPhoneNumber(recipient.getNormalizedPhone()),
            e.getMessage());
      }
    }

    // 8. 잔액 차감 (동일한 txGroupId로 차감하여 취소 시 환불 추적 가능)
    if (successCount > 0) {
      BigDecimal actualCharge = unitPrice.multiply(BigDecimal.valueOf(successCount));
      String comment = String.format("광고문자발송 : %s %d건", request.getMsgTypeLabel(), successCount);
      if (blockedCount > 0) {
        comment += String.format(" (수신거부 %d건 제외)", blockedCount);
      }

      try {
        balanceService.deductWithTxGroupId(userId, actualCharge, comment, userId, txGroupId);
      } catch (Exception e) {
        log.error(
            "잔액 차감 실패 - userId: {}, charge: {}, txGroupId: {}, error: {}",
            userId,
            actualCharge,
            txGroupId,
            e.getMessage());
      }
    }

    log.info(
        "광고문자 발송 완료 - userId: {}, 성공: {}, 중복: {}, 수신거부: {}, txGroupId: {}",
        userId,
        successCount,
        duplicateCount,
        blockedCount,
        txGroupId);

    return AdMessageResponse.success(
        successCount, duplicateCount, blockedCount, maskedBlockedNumbers, batchId);
  }

  /** 메시지 타입별 단가 조회 (BalanceResponse 사용) */
  private BigDecimal getUnitPrice(BalanceResponse balance, String msgTypeLabel) {
    return switch (msgTypeLabel.toUpperCase()) {
      case "SMS" ->
          balance.getSmsPrice() != null ? balance.getSmsPrice() : balance.getSubtractUnitPrice();
      case "LMS" ->
          balance.getLmsPrice() != null ? balance.getLmsPrice() : balance.getSubtractUnitPrice();
      case "MMS" ->
          balance.getMmsPrice() != null ? balance.getMmsPrice() : balance.getSubtractUnitPrice();
      default -> balance.getSubtractUnitPrice();
    };
  }

  /**
   * MsgQueue 생성
   *
   * @param txGroupId 결제 거래 그룹 ID (환불 추적용)
   */
  private MsgQueue createMsgQueue(
      String msgType,
      String dstaddr,
      String callback,
      String subject,
      String text,
      Integer fileCnt,
      String fileloc1,
      String fileloc2,
      String fileloc3,
      String userKey,
      String txGroupId,
      String regId) {
    return switch (msgType) {
      case "S" -> MsgQueue.createSms(dstaddr, callback, subject, text, userKey, txGroupId, regId);
      case "L" -> MsgQueue.createLms(dstaddr, callback, subject, text, userKey, txGroupId, regId);
      case "M" ->
          MsgQueue.createMms(
              dstaddr,
              callback,
              subject,
              text,
              fileCnt != null ? fileCnt : 0,
              fileloc1,
              fileloc2,
              fileloc3,
              userKey,
              txGroupId,
              regId);
      default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 메시지 타입입니다.");
    };
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

  /** 배치ID 생성 (yyyyMMdd-HHmmssSSS) */
  private String generateBatchId() {
    LocalDateTime now = LocalDateTime.now();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS");
    return now.format(formatter);
  }

  /** 전화번호 마스킹 */
  private String maskPhoneNumber(String phone) {
    if (phone == null || phone.length() < 7) {
      return phone;
    }
    return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
  }
}
