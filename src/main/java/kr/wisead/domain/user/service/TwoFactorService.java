package kr.wisead.domain.user.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CommonUtils;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.sms.service.SmsAuthService;
import kr.wisead.domain.user.dto.TwoFactorSettingsResponse;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지 2FA 채널 설정 서비스 — SMS 활성화/비활성화/기본 채널 변경/조회.
 *
 * <p>스펙 인수기준:
 *
 * <ul>
 *   <li>C2: SMS 활성화 시도 → 입력 휴대폰 번호로 6자리 숫자 OTP 발송
 *   <li>C3: 5분 내 5회 이내 정확 입력 → login_phone 저장 + default_two_factor_method=SMS
 *   <li>C4: 실패/만료 → login_phone 및 default_two_factor_method 미변경 ({@code @Transactional} rollback)
 *   <li>C10: SMS 채널 선택 시 login_phone 존재 필수
 * </ul>
 *
 * <p>플랜: plan v5 §4 Phase E.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorService {

  private final UserMapper userMapper;
  private final SmsAuthService smsAuthService;

  /**
   * 마이페이지 SMS 등록을 위한 OTP 발송 (C2).
   *
   * <p>SmsAuthService 와 동일 저장소(key=userId)를 사용한다. 로그인 흐름의 OTP 와 충돌 시 동일 사용자가 둘 중 한 흐름만 사용하도록 한다.
   */
  @Transactional
  public void sendSmsRegisterCode(String username, String phoneNumber) {
    User user = findUserByUserId(username);
    validatePhoneNumber(phoneNumber);

    smsAuthService.sendVerificationCode(user.getSeq(), phoneNumber);
    log.info("마이페이지 SMS 등록 OTP 발송: userId={}, seq={}", username, user.getSeq());
  }

  /**
   * SMS 활성화 OTP 검증 + login_phone 저장 + default_two_factor_method=SMS (C3/C4).
   *
   * <p>검증 실패/만료 시 {@link BusinessException} → {@code @Transactional} rollback → 컬럼 미변경 (C4).
   */
  @Transactional
  public void verifySmsRegisterCode(String username, String code) {
    User user = findUserByUserId(username);

    // 검증 + 발송 시점 phoneNumber 회수. 실패 시 BusinessException → rollback.
    String verifiedPhone = smsAuthService.verifyCodeAndGetPhone(user.getSeq(), code);
    if (verifiedPhone == null || verifiedPhone.isBlank()) {
      // SmsAuthService 가 phoneNumber 를 보존하지 못한 비정상 케이스 — rollback 유도.
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "휴대폰 정보가 유실되었습니다. 다시 시도해주세요.");
    }

    // AES256 + Base64 (기존 User.phone 패턴과 동일).
    String encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(verifiedPhone));

    userMapper.updateLoginPhone(user.getSeq(), encryptedPhone);
    userMapper.updateDefaultTwoFactorMethod(user.getSeq(), "SMS");
    log.info("마이페이지 SMS 채널 활성화 완료: userId={}, seq={}", username, user.getSeq());
  }

  /** SMS 채널 비활성화: login_phone 삭제 + default_two_factor_method=EMAIL. */
  @Transactional
  public void deactivateSms(String username) {
    User user = findUserByUserId(username);

    userMapper.updateLoginPhone(user.getSeq(), null);
    userMapper.updateDefaultTwoFactorMethod(user.getSeq(), "EMAIL");
    log.info("마이페이지 SMS 채널 비활성화: userId={}, seq={}", username, user.getSeq());
  }

  /** 기본 채널 변경: EMAIL ↔ SMS (login_phone 존재 시만 SMS 가능 — C10). */
  @Transactional
  public void setDefaultChannel(String username, String channel) {
    if (!"EMAIL".equals(channel) && !"SMS".equals(channel)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "지원하지 않는 채널입니다.");
    }
    User user = findUserByUserId(username);

    if ("SMS".equals(channel) && (user.getLoginPhone() == null || user.getLoginPhone().isBlank())) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT_VALUE, "SMS 채널을 선택하려면 먼저 휴대폰 번호를 등록해야 합니다.");
    }

    userMapper.updateDefaultTwoFactorMethod(user.getSeq(), channel);
    log.info("마이페이지 기본 채널 변경: userId={}, channel={}", username, channel);
  }

  /** 현재 2FA 설정 조회 (C10 — login_phone 등록 여부 + 기본 채널 + 마스킹). */
  @Transactional(readOnly = true)
  public TwoFactorSettingsResponse getSettings(String username) {
    User user = findUserByUserId(username);

    boolean smsRegistered = user.getLoginPhone() != null && !user.getLoginPhone().isBlank();
    String maskedPhone =
        smsRegistered ? CommonUtils.maskingPhone(decryptField(user.getLoginPhone())) : null;
    String maskedEmail =
        user.getEmail() != null ? CommonUtils.maskingEmail(decryptField(user.getEmail())) : null;

    return TwoFactorSettingsResponse.builder()
        .defaultChannel(user.getDefaultTwoFactorMethod())
        .smsRegistered(smsRegistered)
        .maskedPhone(maskedPhone)
        .maskedEmail(maskedEmail)
        .build();
  }

  // ==================== Private Helpers ====================

  private User findUserByUserId(String username) {
    return userMapper
        .findByUserId(username)
        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
  }

  /** 휴대폰 번호 형식 검증 (010-XXXX-XXXX 또는 01012345678). */
  private void validatePhoneNumber(String phone) {
    if (phone == null || !phone.matches("^010-?\\d{4}-?\\d{4}$")) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "올바른 휴대폰 번호 형식이 아닙니다.");
    }
  }

  /** AES256 + Base64 복호화 (실패 시 원본 반환). */
  private String decryptField(String encrypted) {
    if (encrypted == null || encrypted.isEmpty()) {
      return encrypted;
    }
    try {
      return CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(encrypted));
    } catch (Exception e) {
      log.debug("필드 복호화 실패, 원본 반환: {}", e.getMessage());
      return encrypted;
    }
  }
}
