package kr.wisead.domain.event.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.common.util.UserIdResolver;
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
  private final UserIdResolver userIdResolver;

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

  /** 스태프 QR 스캔으로 체크인 처리 (로그인 필요) */
  @Transactional
  public EventCheckResponse staffCheckIn(
      Integer eventSeq, String checkCode, String staffUserSeq, String deviceInfo) {
    // 1. 권한 검증: 행사 소유자 또는 스태프 참가자
    var event =
        surveyMasterMapper
            .selectByEventSeq(eventSeq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "행사 정보를 찾을 수 없습니다."));

    String staffUserId = userIdResolver.resolveUserId(staffUserSeq);
    if (!staffUserId.equals(event.getRegId()) && !isStaffParticipant(eventSeq, staffUserId)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED, "해당 행사의 체크인 권한이 없습니다.");
    }

    // 2. 참가자 조회
    EventParticipant participant =
        participantMapper
            .selectDetailByEventSeqAndCheckCode(eventSeq, checkCode)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

    log.info(
        "스태프 체크인 요청: eventSeq={}, checkCode={}, staffUserId={}", eventSeq, checkCode, staffUserId);

    return processCheckIn(participant, deviceInfo);
  }

  /** 로그인한 사용자가 해당 행사의 스태프 참가자인지 확인 (전화번호 매칭) */
  private boolean isStaffParticipant(Integer eventSeq, String userId) {
    // USER.PHONE은 이미 암호화된 값, SURVEY_USER.USER_PHONE도 동일 암호화
    return userMapper
        .findByUserId(userId)
        .filter(u -> u.getPhone() != null && !u.getPhone().isBlank())
        .flatMap(u -> participantMapper.selectByEventSeqAndPhone(eventSeq, u.getPhone()))
        .filter(p -> "스태프".equals(p.getParticipantType()))
        .isPresent();
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

    if (alreadyCheckedIn) {
      return EventCheckResponse.alreadyCheckedIn(participantResponse, event.getBadgePrintType());
    }

    // 4. 체크인 로그 등록
    EventActionLog actionLog =
        EventActionLog.create(participant.getSeq(), checkInType.getSeq(), deviceInfo, null);
    actionLogMapper.insert(actionLog);

    // 5. attendTime 업데이트 (체크인 시점 = 참석시간)
    String attendTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    participantMapper.updateAttendTime(participant.getSeq(), attendTime);

    // 6. 명찰 URL 생성
    String nametagUrl =
        wiseadUrl + "/api/events/" + participant.getEventSeq() + "/nametag/" + participant.getSeq();

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
