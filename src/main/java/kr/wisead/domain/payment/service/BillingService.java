package kr.wisead.domain.payment.service;

import java.math.BigDecimal;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.payment.entity.Transaction;
import kr.wisead.mapper.primary.QrVisitLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 통합 과금 서비스 (리팩토링 버전) - QR 코드 방문 과금 - 설문 참여 과금 - 내부적으로 WalletService 사용 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

  private final WalletService walletService;
  private final QrVisitLogMapper qrVisitLogMapper;
  private final UserIdResolver userIdResolver;

  private static final long QR_FREE_VISITS = 3000L;
  private static final long QR_OVERAGE_UNIT = 3000L;
  private static final String SERVICE_QR = "qr_code";
  private static final String SERVICE_SURVEY = "survey";

  /**
   * QR 코드 신청 비용 차감 (최초 신청 시) - 최초 3000건 기본 제공
   *
   * @param userSeq 사용자 시퀀스
   * @param comment 차감 사유
   */
  @Transactional
  public void deductInitialQrFee(Integer userSeq, String comment) {
    // QR 단가 조회
    BigDecimal qrFee = walletService.getAppliedRate(userSeq, SERVICE_QR);

    // 잔액 확인
    if (!walletService.hasEnoughBalance(userSeq, qrFee)) {
      throw new BusinessException(
          ErrorCode.INSUFFICIENT_BALANCE, "QR 코드 신청 비용이 부족합니다. 필요 금액: " + qrFee + "원");
    }

    // 우선순위 차감 (BONUS → POINT → CASH)
    String txGroupId =
        walletService.deductWithPriority(
            userSeq,
            SERVICE_QR,
            BigDecimal.ONE, // 수량 1
            comment != null ? comment : "QR 코드 신청",
            Transaction.REG_ID_SYSTEM);

    log.info("QR 신청 비용 차감 완료: userSeq={}, fee={}, txGroupId={}", userSeq, qrFee, txGroupId);
  }

  /**
   * QR 코드 추가 방문 과금 (3000건 초과 시) - 3000건 단위로 추가 과금
   *
   * @param userSeq 사용자 시퀀스
   * @param comment 차감 사유
   * @param authCodeUrl QR 코드 URL (방문 수 조회용)
   * @return 과금 발생 여부
   */
  @Transactional
  public boolean deductQrOverageCharge(Integer userSeq, String comment, String authCodeUrl) {
    // 현재 활성 방문 수 조회 (진행 중 상태의 방문만 카운트)
    long currentVisits = qrVisitLogMapper.countActiveVisitsByAuthCodeUrl(authCodeUrl);

    // 무료 제공 건수(3000건) 이하면 과금 없음
    if (currentVisits <= QR_FREE_VISITS) {
      return false;
    }

    // 정확히 3001, 6001, 9001 등에서만 과금
    long overageCount = currentVisits - QR_FREE_VISITS;
    if (overageCount <= 0 || (overageCount - 1) % QR_OVERAGE_UNIT != 0) {
      return false;
    }

    // QR 단가 조회
    BigDecimal overageFee = walletService.getAppliedRate(userSeq, SERVICE_QR);

    // 잔액 확인
    if (!walletService.hasEnoughBalance(userSeq, overageFee)) {
      log.warn(
          "QR 추가 과금 실패 - 잔액 부족: userSeq={}, visits={}, required={}",
          userSeq,
          currentVisits,
          overageFee);
      // 과금 실패 시 마지막 방문 로그 삭제
      qrVisitLogMapper.deleteLastActiveVisitByAuthCodeUrl(authCodeUrl);
      // 참가자에게는 일반적인 메시지 표시 (과금 상세 노출 X)
      throw new BusinessException(
          ErrorCode.SERVICE_UNAVAILABLE, "현재 설문에 참여할 수 없습니다. 잠시 후 다시 시도해주세요.");
    }

    // 우선순위 차감
    String txGroupId =
        walletService.deductWithPriority(
            userSeq,
            SERVICE_QR,
            BigDecimal.ONE,
            comment != null ? comment : "QR 코드 추가 과금 (" + currentVisits + "건)",
            Transaction.REG_ID_SYSTEM);

    log.info(
        "QR 추가 과금 완료: userSeq={}, visits={}, fee={}, txGroupId={}",
        userSeq,
        currentVisits,
        overageFee,
        txGroupId);

    return true;
  }

  /**
   * 설문 참여 비용 차감
   *
   * @param userSeq 사용자 시퀀스
   * @param count 참여 건수
   * @param comment 차감 사유
   */
  @Transactional
  public void deductSurveyCharge(Integer userSeq, int count, String comment) {
    BigDecimal quantity = BigDecimal.valueOf(count);

    // 설문 단가 조회
    BigDecimal unitPrice = walletService.getAppliedRate(userSeq, SERVICE_SURVEY);
    BigDecimal totalCharge = unitPrice.multiply(quantity);

    // 잔액 확인
    if (!walletService.hasEnoughBalance(userSeq, totalCharge)) {
      throw new BusinessException(
          ErrorCode.INSUFFICIENT_BALANCE, "설문 참여 비용이 부족합니다. 필요 금액: " + totalCharge + "원");
    }

    // 우선순위 차감
    String txGroupId =
        walletService.deductWithPriority(
            userSeq,
            SERVICE_SURVEY,
            quantity,
            comment != null ? comment : "설문 참여 비용",
            Transaction.REG_ID_SYSTEM);

    log.info(
        "설문 참여 비용 차감 완료: userSeq={}, count={}, charge={}, txGroupId={}",
        userSeq,
        count,
        totalCharge,
        txGroupId);
  }

  /**
   * QR 코드 과금 가능 여부 확인
   *
   * @param userSeq 사용자 시퀀스
   * @return 과금 가능 여부
   */
  public boolean canChargeQr(Integer userSeq) {
    try {
      BigDecimal qrFee = walletService.getAppliedRate(userSeq, SERVICE_QR);
      return walletService.hasEnoughBalance(userSeq, qrFee);
    } catch (BusinessException e) {
      log.warn("QR 단가 조회 실패: userSeq={}, error={}", userSeq, e.getMessage());
      return false;
    }
  }

  /**
   * 설문 과금 가능 여부 확인
   *
   * @param userSeq 사용자 시퀀스
   * @param count 참여 건수
   * @return 과금 가능 여부
   */
  public boolean canChargeSurvey(Integer userSeq, int count) {
    try {
      BigDecimal unitPrice = walletService.getAppliedRate(userSeq, SERVICE_SURVEY);
      BigDecimal totalCharge = unitPrice.multiply(BigDecimal.valueOf(count));
      return walletService.hasEnoughBalance(userSeq, totalCharge);
    } catch (BusinessException e) {
      log.warn("설문 단가 조회 실패: userSeq={}, error={}", userSeq, e.getMessage());
      return false;
    }
  }

  // ========== userId 기반 오버로드 메서드 (API 호환용) ==========

  /** QR 코드 신청 비용 차감 (userId 기반) */
  @Transactional
  public void deductInitialQrFee(String userId, String comment) {
    deductInitialQrFee(userIdResolver.toUserSeq(userId), comment);
  }

  /** QR 코드 과금 가능 여부 확인 (userId 기반) */
  public boolean canChargeQr(String userId) {
    return canChargeQr(userIdResolver.toUserSeq(userId));
  }
}
