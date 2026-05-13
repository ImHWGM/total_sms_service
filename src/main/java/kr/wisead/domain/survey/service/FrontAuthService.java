package kr.wisead.domain.survey.service;

import java.security.*;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Cipher;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CommonUtils;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.survey.dto.JuminEncryptRequest;
import kr.wisead.domain.survey.dto.JuminEncryptResponse;
import kr.wisead.domain.survey.dto.KeypadResponse;
import kr.wisead.domain.survey.dto.PhoneValidationRequest;
import kr.wisead.domain.survey.dto.SurveyUserResponse;
import kr.wisead.domain.survey.entity.QrVisitLog;
import kr.wisead.domain.survey.entity.SurveyMaster;
import kr.wisead.domain.survey.entity.SurveyUser;
import kr.wisead.mapper.primary.QrVisitLogMapper;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import kr.wisead.mapper.primary.SurveyUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 설문 참여자 인증 Service (프론트) */
@Slf4j
@Service
@RequiredArgsConstructor
public class FrontAuthService {

  private final SurveyMasterMapper surveyMasterMapper;
  private final SurveyUserMapper surveyUserMapper;
  private final QrVisitLogMapper qrVisitLogMapper;

  // RSA 키 저장소 (keypadId -> KeyPair)
  private final Map<String, KeyPairInfo> keyPairStore = new ConcurrentHashMap<>();

  // 키 만료 시간 (5분)
  private static final long KEY_EXPIRE_MILLIS = 5 * 60 * 1000;

  /** QR 코드 접근 시 사용자 자동 생성 (인증 없는 설문) */
  @Transactional
  public SurveyUserResponse createQrUser(String authCodeUrl) {
    // 1. 이벤트 조회
    SurveyMaster event =
        surveyMasterMapper
            .selectByAuthCodeUrl(authCodeUrl)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "설문을 찾을 수 없습니다."));

    // 2. 이벤트 상태 확인
    if (!event.isActive()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "현재 참여할 수 없는 설문입니다.");
    }

    // 3. 사용자 키 생성
    String userKey = CommonUtils.randomCode(20);

    // 4. 사용자 등록
    SurveyUser user =
        SurveyUser.builder()
            .eventSeq(event.getEventSeq())
            .userKey(userKey)
            .regId("QR_USER")
            .build();

    surveyUserMapper.insertQrUser(user);

    // 5. QR 방문 로그 기록 (이벤트 상태와 함께 저장)
    QrVisitLog visitLog = QrVisitLog.create(event.getEventSeq(), event.getStatus());
    qrVisitLogMapper.insert(visitLog);

    log.info("QR 사용자 생성 - eventSeq: {}, userKey: {}", event.getEventSeq(), userKey);

    // 조회용 필드 설정
    return SurveyUserResponse.builder()
        .userSeq(user.getSeq())
        .userKey(userKey)
        .eventSeq(event.getEventSeq())
        .eventCode(event.getEventCode())
        .eventType(event.getEventType())
        .eventName(event.getEventName())
        .build();
  }

  /** 이벤트 코드로 익명 사용자 생성 (NA 무인증 설문 - Access Link 접근) */
  @Transactional
  public SurveyUserResponse createEventUser(String eventCode) {
    // 1. 이벤트 조회
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventCode(eventCode)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트를 찾을 수 없습니다."));

    // 2. 이벤트 상태 확인
    if (!event.isActive()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "현재 참여할 수 없는 이벤트입니다.");
    }

    // 3. 사용자 키 생성
    String userKey = CommonUtils.randomCode(20);

    // 4. 익명 사용자 등록 (AUTH_USER_MAPPING 없이)
    SurveyUser user =
        SurveyUser.builder()
            .eventSeq(event.getEventSeq())
            .userKey(userKey)
            .regId("EVENT_USER")
            .build();

    surveyUserMapper.insertQrUser(user);

    log.info(
        "Event 사용자 생성 - eventSeq: {}, eventCode: {}, userKey: {}",
        event.getEventSeq(),
        eventCode,
        userKey);

    return SurveyUserResponse.builder()
        .userSeq(user.getSeq())
        .userKey(userKey)
        .eventSeq(event.getEventSeq())
        .eventCode(event.getEventCode())
        .eventType(event.getEventType())
        .eventName(event.getEventName())
        .build();
  }

  /** 휴대폰 번호로 사용자 검증 (재발송 시나리오) */
  @Transactional(readOnly = true)
  public SurveyUserResponse validatePhone(PhoneValidationRequest request) {
    String phone = normalizePhone(request.getPhone());
    String encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(phone));

    Optional<SurveyUser> userOpt;

    if (!CommonUtils.isNullOrEmpty(request.getEventCode())) {
      // 이벤트 코드로 검증
      userOpt =
          surveyUserMapper.selectByEventCodeAndResendPhone(request.getEventCode(), encryptedPhone);
    } else if (!CommonUtils.isNullOrEmpty(request.getAuthCodeUrl())) {
      // QR 코드 URL로 검증
      userOpt =
          surveyUserMapper.selectByAuthCodeUrlAndResendPhone(
              request.getAuthCodeUrl(), encryptedPhone);
    } else {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "이벤트 코드 또는 QR코드 URL이 필요합니다.");
    }

    SurveyUser user =
        userOpt.orElseThrow(
            () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "등록된 휴대폰 번호가 아닙니다."));

    if (user.isSubmitted()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 설문에 참여하셨습니다.");
    }

    return SurveyUserResponse.from(user);
  }

  /** 휴대폰 번호 존재 여부 확인 */
  @Transactional(readOnly = true)
  public boolean checkPhoneExists(String eventCode, String phone) {
    String normalizedPhone = normalizePhone(phone);
    String encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(normalizedPhone));
    return surveyUserMapper.existsByEventCodeAndResendPhone(eventCode, encryptedPhone);
  }

  /** 가상 키패드 데이터 생성 (RSA 키쌍) */
  public KeypadResponse generateKeypad() {
    try {
      // RSA 키쌍 생성 (2048비트)
      KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
      keyGen.initialize(2048, new SecureRandom());
      KeyPair keyPair = keyGen.generateKeyPair();

      // 키패드 ID 생성
      String keypadId = CommonUtils.randomCode(32);

      // 만료 시간 설정
      long expiresAt = System.currentTimeMillis() + KEY_EXPIRE_MILLIS;

      // 키 저장
      keyPairStore.put(keypadId, new KeyPairInfo(keyPair, expiresAt));

      // 공개키 Base64 인코딩
      String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

      // 만료된 키 정리
      cleanupExpiredKeys();

      log.debug("가상 키패드 생성 - keypadId: {}", keypadId);

      return KeypadResponse.builder()
          .publicKey(publicKeyBase64)
          .keypadId(keypadId)
          .expiresAt(expiresAt)
          .build();

    } catch (NoSuchAlgorithmException e) {
      log.error("RSA 키 생성 실패", e);
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "키패드 생성에 실패했습니다.");
    }
  }

  /** RSA 복호화 (가상 키패드 입력값) */
  public String decryptKeypadInput(String keypadId, String encryptedData) {
    KeyPairInfo keyPairInfo = keyPairStore.get(keypadId);

    if (keyPairInfo == null) {
      throw new BusinessException(ErrorCode.KEYPAD_INVALID, "유효하지 않은 키패드 세션입니다.");
    }

    if (System.currentTimeMillis() > keyPairInfo.expiresAt) {
      keyPairStore.remove(keypadId);
      throw new BusinessException(ErrorCode.KEYPAD_EXPIRED, "키패드 세션이 만료되었습니다.");
    }

    try {
      Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
      cipher.init(Cipher.DECRYPT_MODE, keyPairInfo.keyPair.getPrivate());

      byte[] decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedData));
      String decrypted = new String(decryptedBytes);

      // 사용 후 키 삭제 (1회용)
      keyPairStore.remove(keypadId);

      return decrypted;

    } catch (Exception e) {
      log.error("RSA 복호화 실패", e);
      throw new BusinessException(ErrorCode.INVALID_INPUT, "입력값 복호화에 실패했습니다.");
    }
  }

  /**
   * 가상 키패드 입력값을 즉시 서버측 AES256+Base64 ciphertext로 변환.
   *
   * <p>F+H 흐름의 핵심: FE가 jumin 키패드 "확인" 시점에 호출 → RSA 복호화 → 평문 jumin 조합 → AES 재암호화 → ciphertext 반환. 이후
   * keypad 세션은 소멸되고, FE는 ciphertext만 보관 후 설문 제출 시 그대로 전송한다. 따라서 설문 응답 시간이 길어져도 keypad TTL 만료의 영향을
   * 받지 않는다.
   *
   * <p>ciphertext는 {@code "ENC:" + base64(AES(plainJumin))} 형식이며, SurveyService 제출 흐름에서 해당 prefix를
   * 인식해 그대로 저장한다.
   */
  public JuminEncryptResponse encryptJumin(JuminEncryptRequest request) {
    // @Valid on controller ensures keypadId/front/backCipher are non-blank and front matches \d{6}.
    // RSA 복호화 (만료/무효 시 KEYPAD_EXPIRED / KEYPAD_INVALID ErrorCode 전파 → FE가 키패드 재발급 트리거)
    String back =
        decryptKeypadInput(request.getKeypadId(), request.getBackCipher()).replaceAll("\\D+", "");
    if (!back.matches("\\d{7}")) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "주민번호 뒷자리 형식이 올바르지 않습니다.");
    }

    String plainJumin = request.getFront() + "-" + back;
    String encrypted = CryptoUtils.encryptAES256(plainJumin);
    if (CommonUtils.isNullOrEmpty(encrypted)) {
      throw new BusinessException(ErrorCode.INTERNAL_ERROR, "주민번호 암호화에 실패했습니다.");
    }
    String ciphertext = "ENC:" + CryptoUtils.encodeBase64(encrypted);
    return JuminEncryptResponse.builder().ciphertext(ciphertext).build();
  }

  /** 전화번호 정규화 (하이픈 제거) */
  private String normalizePhone(String phone) {
    if (CommonUtils.isNullOrEmpty(phone)) {
      return phone;
    }
    return phone.replaceAll("-", "");
  }

  /** 만료된 키 정리 (내부 호출용) */
  private void cleanupExpiredKeys() {
    long now = System.currentTimeMillis();
    keyPairStore.entrySet().removeIf(entry -> entry.getValue().expiresAt < now);
  }

  /** 만료된 키 정리 (스케줄러) - 1분마다 실행 */
  @Scheduled(fixedRate = 60000)
  public void scheduledCleanupExpiredKeys() {
    int before = keyPairStore.size();
    cleanupExpiredKeys();
    int cleaned = before - keyPairStore.size();
    if (cleaned > 0) {
      log.debug("만료된 키패드 세션 정리 - 정리된 키 수: {}, 남은 키 수: {}", cleaned, keyPairStore.size());
    }
  }

  /** 키쌍 정보 내부 클래스 */
  private static class KeyPairInfo {
    final KeyPair keyPair;
    final long expiresAt;

    KeyPairInfo(KeyPair keyPair, long expiresAt) {
      this.keyPair = keyPair;
      this.expiresAt = expiresAt;
    }
  }
}
