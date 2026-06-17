package kr.wisead.domain.event.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.ratelimit.FailureRateLimiter;
import kr.wisead.common.ratelimit.RateLimitExceededException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.event.dto.NametagPrintRequest;
import kr.wisead.domain.event.entity.*;
import kr.wisead.domain.survey.entity.SurveyMaster;
import kr.wisead.mapper.primary.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 명찰 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NametagService {

  private static final DateTimeFormatter TIME_HH_MM = DateTimeFormatter.ofPattern("HH:mm");

  private final EventParticipantMapper participantMapper;
  private final EventNametagLogMapper nametagLogMapper;
  private final SurveyMasterMapper surveyMasterMapper;
  private final AdminService adminService;
  private final ObjectMapper objectMapper;
  private final FailureRateLimiter failureRateLimiter;

  /** 명찰 데이터 조회 (출력/미리보기용) */
  @Transactional(readOnly = true)
  public Map<String, Object> getNametagData(Integer eventSeq, Long participantSeq, String userId) {
    validateEventReadAccess(eventSeq, userId);

    EventParticipant participant =
        participantMapper
            .selectDetailBySeq(participantSeq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

    validateParticipantEvent(eventSeq, participant);

    Map<String, Object> nametagData = new HashMap<>();
    nametagData.put("participantSeq", participant.getSeq());
    nametagData.put("eventName", participant.getEventName());
    nametagData.put("name", decryptField(participant.getUserName()));
    nametagData.put("department", participant.getDepartment());
    nametagData.put("position", participant.getPosition());
    nametagData.put("participantType", participant.getParticipantType());
    nametagData.put("checkCode", participant.getCheckCode());
    nametagData.put("contact", decryptField(participant.getUserPhone()));

    return nametagData;
  }

  /** 명찰 출력 로그 기록 */
  @Transactional
  public void recordPrint(Integer eventSeq, NametagPrintRequest request, String printBy) {
    validateEventReadAccess(eventSeq, printBy);

    EventParticipant participant =
        participantMapper
            .selectBySeq(request.getParticipantSeq())
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

    validateParticipantEvent(eventSeq, participant);

    // 명찰 출력 로그 등록
    EventNametagLog nametagLog =
        EventNametagLog.create(participant.getSeq(), request.getTemplateType(), printBy);
    nametagLogMapper.insert(nametagLog);

    // 참가자 명찰 출력 여부 업데이트
    participantMapper.updateNametagPrinted(participant.getSeq(), "Y");

    // 수동 명찰 출력 시 참석시간 반영 (기존 체크인이 없는 경우만)
    if (participant.getAttendTime() == null || participant.getAttendTime().isBlank()) {
      String attendTime = LocalDateTime.now().format(TIME_HH_MM) + " (수동)";
      participantMapper.updateAttendTime(participant.getSeq(), attendTime);
      log.info(
          "수동 명찰 출력으로 참석시간 반영: participantSeq={}, attendTime={}", participant.getSeq(), attendTime);
    }

    // 미등록 상태인 경우 사전등록으로 변경
    if (EventParticipant.DEFAULT_REGIST_TYPE.equals(participant.getRegistType())) {
      participantMapper.updateRegistType(participant.getSeq(), "사전등록");
    }

    log.info(
        "명찰 출력 완료: participantSeq={}, templateType={}, printBy={}",
        participant.getSeq(),
        request.getTemplateType(),
        printBy);
  }

  /** 명찰 출력 여부 확인 */
  @Transactional(readOnly = true)
  public boolean isNametagPrinted(Long participantSeq) {
    return nametagLogMapper.existsByParticipantSeq(participantSeq);
  }

  /** 명찰 데이터 조회 - checkCode 기반 (QR 스캔용) */
  @Transactional(readOnly = true)
  public kr.wisead.domain.event.dto.NametagResponse getNametagDataByCheckCode(
      Integer eventSeq, String checkCode, String clientIp) {
    EventParticipant participant =
        participantMapper.selectDetailByEventSeqAndCheckCode(eventSeq, checkCode).orElse(null);

    if (participant == null) {
      recordCheckCodeFailure(eventSeq, clientIp);
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다.");
    }

    // DB에서 가져온 값은 AES256 + Base64로 암호화되어 있음
    // 명찰에는 사람이 읽을 수 있는 값이 필요하므로 복호화
    String decryptedName = decryptField(participant.getUserName());
    String decryptedPhone =
        shouldExposeContact(participant.getNametagConfig())
            ? decryptField(participant.getUserPhone())
            : null;

    return kr.wisead.domain.event.dto.NametagResponse.from(
        participant, decryptedName, decryptedPhone);
  }

  /** 명찰 출력 로그 기록 - checkCode 기반 (QR 스캔용) */
  @Transactional
  public void recordPrintByCheckCode(
      Integer eventSeq,
      String checkCode,
      NametagPrintRequest request,
      String deviceInfo,
      String clientIp) {
    EventParticipant participant =
        participantMapper.selectByEventSeqAndCheckCode(eventSeq, checkCode).orElse(null);

    if (participant == null) {
      recordCheckCodeFailure(eventSeq, clientIp);
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다.");
    }

    // 출력자 정보 (deviceInfo 또는 SYSTEM)
    String printBy = (deviceInfo != null && !deviceInfo.isBlank()) ? deviceInfo : "QR_SCAN";

    // 명찰 출력 로그 등록
    EventNametagLog nametagLog =
        EventNametagLog.create(participant.getSeq(), request.getTemplateType(), printBy);
    nametagLogMapper.insert(nametagLog);

    // 참가자 명찰 출력 여부 업데이트
    participantMapper.updateNametagPrinted(participant.getSeq(), "Y");

    log.info(
        "명찰 출력 완료 (QR): checkCode={}, templateType={}, printBy={}",
        checkCode,
        request.getTemplateType(),
        printBy);
  }

  /** 암호화된 필드 복호화 (AES256 + Base64) */
  private String decryptField(String encryptedValue) {
    if (encryptedValue == null || encryptedValue.isEmpty()) {
      return null;
    }
    try {
      return CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(encryptedValue));
    } catch (Exception e) {
      log.warn("필드 복호화 실패: {}", e.getMessage());
      return encryptedValue; // 복호화 실패 시 원본 반환 (서비스 중단 방지)
    }
  }

  private void validateEventReadAccess(Integer eventSeq, String userId) {
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventSeq(eventSeq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트 정보를 찾을 수 없습니다."));
    Integer userLevel = adminService.getUserLevel(userId);
    adminService.validateModifyPermission(userId, userLevel, event.getRegId());
  }

  private void validateParticipantEvent(Integer eventSeq, EventParticipant participant) {
    if (!java.util.Objects.equals(participant.getEventSeq(), eventSeq)) {
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다.");
    }
  }

  /**
   * 무인증 checkCode 조회 실패 시 IP+eventSeq 기준 실패 카운터 누적. status 경로와 동일 키(:check)를 공유해 status/nametag
   * 교차 brute-force 도 합산 차단한다. 한도 초과 시 RateLimitExceededException.
   */
  private void recordCheckCodeFailure(Integer eventSeq, String clientIp) {
    if (clientIp == null || clientIp.isBlank()) {
      return;
    }
    String failureKey = clientIp + ":" + eventSeq + ":check";
    if (!failureRateLimiter.recordFailureAndCheckAllowed(failureKey)) {
      throw new RateLimitExceededException("요청이 너무 빈번합니다. 잠시 후 다시 시도해 주세요.");
    }
  }

  private boolean shouldExposeContact(String nametagConfig) {
    if (nametagConfig == null || nametagConfig.isBlank()) {
      return false;
    }
    try {
      JsonNode fields = objectMapper.readTree(nametagConfig).path("fields");
      if (!fields.isArray()) {
        return false;
      }
      for (JsonNode field : fields) {
        if ("contact".equals(field.path("key").asText(null))) {
          return field.path("enabled").isBoolean() && field.path("enabled").asBoolean();
        }
      }
      return false;
    } catch (Exception e) {
      log.warn("명찰 설정 파싱 실패: {}", e.getMessage());
      return false;
    }
  }
}
