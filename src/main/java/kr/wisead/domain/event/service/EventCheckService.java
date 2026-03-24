package kr.wisead.domain.event.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.event.dto.*;
import kr.wisead.domain.event.entity.*;
import kr.wisead.mapper.primary.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 행사 체크인/액션 처리 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventCheckService {

  private final EventParticipantMapper participantMapper;
  private final EventActionTypeMapper actionTypeMapper;
  private final EventActionLogMapper actionLogMapper;
  private final EventNametagLogMapper nametagLogMapper;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final SurveyMasterMapper surveyMasterMapper;

  private static final long STAFF_COOKIE_MAX_AGE_MS = 86400_000L; // 24시간

  @Value("${wisead.url:http://localhost:8080}")
  private String wiseadUrl;

  /** QR 스캔으로 체크인 처리 (참가자용) */
  @Transactional
  public EventCheckResponse checkIn(Integer eventSeq, String checkCode, String deviceInfo) {
    EventParticipant participant =
        participantMapper
            .selectDetailByEventSeqAndCheckCode(eventSeq, checkCode)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

    return processCheckIn(participant, deviceInfo);
  }

  /** 스태프 QR 스캔으로 체크인 처리 (쿠키 인증) */
  @Transactional
  public EventCheckResponse staffCheckIn(Integer eventSeq, String checkCode, String deviceInfo) {
    EventParticipant participant =
        participantMapper
            .selectDetailByEventSeqAndCheckCode(eventSeq, checkCode)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

    log.info("스태프 체크인 요청: eventSeq={}, checkCode={}", eventSeq, checkCode);

    return processCheckIn(participant, deviceInfo);
  }

  /** 인증코드 검증 + HMAC 서명 쿠키 값 생성 */
  public String verifyAndGenerateCookie(Integer eventSeq, String authCode) {
    var event =
        surveyMasterMapper
            .selectByEventSeq(eventSeq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "행사 정보를 찾을 수 없습니다."));

    if (event.getStaffAuthCode() == null || !event.getStaffAuthCode().equals(authCode)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "인증코드가 일치하지 않습니다.");
    }

    long timestamp = System.currentTimeMillis();
    String data = eventSeq + ":" + timestamp;
    String signature = CryptoUtils.asHex(CryptoUtils.hmacSha256(data, authCode));
    return eventSeq + ":" + timestamp + ":" + signature;
  }

  /** 쿠키 값 검증 (eventSeq 일치 + 24시간 만료 + HMAC 서명 검증) */
  public void validateStaffCookie(Integer eventSeq, String cookieValue) {
    if (cookieValue == null || cookieValue.isBlank()) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "스태프 인증이 필요합니다.");
    }

    String[] parts = cookieValue.split(":");
    if (parts.length != 3) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "잘못된 인증 정보입니다.");
    }

    // 1. eventSeq 일치 확인
    if (!String.valueOf(eventSeq).equals(parts[0])) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "다른 행사의 인증 정보입니다.");
    }

    // 2. 24시간 만료 확인
    long timestamp;
    try {
      timestamp = Long.parseLong(parts[1]);
    } catch (NumberFormatException e) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "잘못된 인증 정보입니다.");
    }
    if (System.currentTimeMillis() - timestamp > STAFF_COOKIE_MAX_AGE_MS) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "인증이 만료되었습니다. 다시 인증해주세요.");
    }

    // 3. HMAC 서명 검증 (DB에서 authCode 조회)
    var event =
        surveyMasterMapper
            .selectByEventSeq(eventSeq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "행사 정보를 찾을 수 없습니다."));

    if (event.getStaffAuthCode() == null) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "이 행사에는 스태프 인증코드가 설정되지 않았습니다.");
    }

    String expectedSignature =
        CryptoUtils.asHex(
            CryptoUtils.hmacSha256(parts[0] + ":" + parts[1], event.getStaffAuthCode()));
    if (!java.security.MessageDigest.isEqual(expectedSignature.getBytes(), parts[2].getBytes())) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "인증 정보가 유효하지 않습니다.");
    }
  }

  /** 전화번호로 체크인 처리 (키오스크용) */
  @Transactional
  public EventCheckResponse checkInByPhone(Integer eventSeq, String phone, String deviceInfo) {
    String cleanPhone = phone.replace("-", "");
    String encryptedPhone = CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(cleanPhone));

    EventParticipant participant =
        participantMapper
            .selectDetailByEventSeqAndPhone(eventSeq, encryptedPhone)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "등록되지 않은 전화번호입니다."));

    return processCheckIn(participant, deviceInfo);
  }

  /** 체크인 공통 처리 로직 */
  private EventCheckResponse processCheckIn(EventParticipant participant, String deviceInfo) {
    // 1. 행사 상태 검증
    var event =
        surveyMasterMapper
            .selectByEventSeq(participant.getEventSeq())
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "행사 정보를 찾을 수 없습니다."));

    if (!"P".equals(event.getStatus())) {
      String message =
          switch (event.getStatus()) {
            case "A" -> "아직 행사가 시작되지 않았습니다. 잠시만 기다려 주세요.";
            case "S" -> "행사가 일시 중지되었습니다. 행사 운영팀에 문의해 주세요.";
            case "F" -> "행사가 종료되어 체크인이 마감되었습니다. 참여해 주셔서 감사합니다.";
            default -> "현재 행사 상태에서는 체크인할 수 없습니다.";
          };
      throw new BusinessException(ErrorCode.INVALID_INPUT, message);
    }

    // 2. CHECK_IN 액션 유형 조회
    EventActionType checkInType =
        actionTypeMapper
            .selectByEventSeqAndActionCode(participant.getEventSeq(), "CHECK_IN")
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "체크인 설정이 되어있지 않습니다."));

    // 3. 이미 체크인했는지 확인
    boolean alreadyCheckedIn =
        actionLogMapper.existsByParticipantSeqAndActionTypeSeq(
            participant.getSeq(), checkInType.getSeq());

    EventParticipantResponse participantResponse = EventParticipantResponse.from(participant);
    String nametagUrl =
        wiseadUrl + "/api/events/" + participant.getEventSeq() + "/nametag/" + participant.getSeq();

    if (alreadyCheckedIn) {
      return EventCheckResponse.alreadyCheckedIn(
          participantResponse, nametagUrl, event.getBadgePrintType());
    }

    // 4. 체크인 로그 등록
    EventActionLog actionLog =
        EventActionLog.create(participant.getSeq(), checkInType.getSeq(), deviceInfo, null);
    actionLogMapper.insert(actionLog);

    // 5. attendTime 업데이트 (체크인 시점 = 참석시간)
    String attendTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    participantMapper.updateAttendTime(participant.getSeq(), attendTime);

    log.info(
        "참가자 체크인 완료: eventSeq={}, participantSeq={}, name={}",
        participant.getEventSeq(),
        participant.getSeq(),
        participant.getUserName());

    return EventCheckResponse.checkIn(participantResponse, nametagUrl, event.getBadgePrintType());
  }

  /** 액션 처리 (관리자용) */
  @Transactional
  public EventCheckResponse processAction(
      Integer eventSeq, EventCheckRequest request, String adminId) {
    // 1. 참가자 조회
    EventParticipant participant =
        participantMapper
            .selectDetailBySeq(request.getParticipantSeq())
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

    // 이벤트 일치 확인
    if (!participant.getEventSeq().equals(eventSeq)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "해당 행사의 참가자가 아닙니다.");
    }

    // 2. 액션 유형 조회
    EventActionType actionType =
        actionTypeMapper
            .selectByEventSeqAndActionCode(eventSeq, request.getActionCode())
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "해당 액션 유형을 찾을 수 없습니다."));

    // 3. 관리자 인증 필요 여부 확인
    String confirmedBy = null;
    if (actionType.isAdminAuthRequired()) {
      if (request.getAdminPassword() == null || request.getAdminPassword().isEmpty()) {
        throw new BusinessException(ErrorCode.INVALID_INPUT, "관리자 비밀번호가 필요합니다.");
      }

      // 관리자 비밀번호 검증
      var admin =
          userMapper
              .findByUserId(adminId)
              .orElseThrow(
                  () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "관리자 정보를 찾을 수 없습니다."));

      if (!passwordEncoder.matches(request.getAdminPassword(), admin.getUserPass())) {
        throw new BusinessException(ErrorCode.INVALID_INPUT, "관리자 비밀번호가 일치하지 않습니다.");
      }

      confirmedBy = adminId;
    }

    // 4. 중복 처리 확인 (중복 허용이 아닌 경우)
    if (!actionType.isMultipleAllowed()) {
      boolean alreadyDone =
          actionLogMapper.existsByParticipantSeqAndActionTypeSeq(
              participant.getSeq(), actionType.getSeq());
      if (alreadyDone) {
        throw new BusinessException(
            ErrorCode.INVALID_INPUT, "이미 " + actionType.getActionName() + " 처리된 참가자입니다.");
      }
    }

    // 5. 액션 로그 등록
    EventActionLog actionLog;
    if (confirmedBy != null) {
      actionLog =
          EventActionLog.createWithAdminAuth(
              participant.getSeq(),
              actionType.getSeq(),
              request.getDeviceInfo(),
              confirmedBy,
              request.getMemo());
    } else {
      actionLog =
          EventActionLog.create(
              participant.getSeq(),
              actionType.getSeq(),
              request.getDeviceInfo(),
              request.getMemo());
    }
    actionLogMapper.insert(actionLog);

    log.info(
        "액션 처리 완료: eventSeq={}, participantSeq={}, actionCode={}, confirmedBy={}",
        eventSeq,
        participant.getSeq(),
        request.getActionCode(),
        confirmedBy);

    EventParticipantResponse participantResponse = EventParticipantResponse.from(participant);
    return EventCheckResponse.action(
        actionType.getActionCode(), actionType.getActionName(), participantResponse);
  }

  /** 체크인 여부 확인 */
  @Transactional(readOnly = true)
  public boolean isCheckedIn(Long participantSeq, Integer eventSeq) {
    EventActionType checkInType =
        actionTypeMapper.selectByEventSeqAndActionCode(eventSeq, "CHECK_IN").orElse(null);

    if (checkInType == null) {
      return false;
    }

    return actionLogMapper.existsByParticipantSeqAndActionTypeSeq(
        participantSeq, checkInType.getSeq());
  }
}
