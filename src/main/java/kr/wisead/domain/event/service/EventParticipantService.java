package kr.wisead.domain.event.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.ratelimit.RateLimitExceededException;
import kr.wisead.common.ratelimit.SimpleRateLimiter;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.event.dto.*;
import kr.wisead.domain.event.entity.*;
import kr.wisead.domain.event.security.RsvpNonceStore;
import kr.wisead.domain.event.util.RegistTypeMapper;
import kr.wisead.domain.excel.service.ExcelService;
import kr.wisead.domain.survey.entity.SurveyMaster;
import kr.wisead.domain.survey.entity.SurveyUser;
import kr.wisead.mapper.primary.*;
import kr.wisead.mapper.sms.MsgQueueMapper;
import kr.wisead.mapper.sms.SendHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 행사 참가자 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventParticipantService {

  private final EventParticipantMapper participantMapper;
  private final EventActionTypeMapper actionTypeMapper;
  private final EventActionLogMapper actionLogMapper;
  private final EventNametagLogMapper nametagLogMapper;
  private final SurveyUserMapper surveyUserMapper;
  private final SurveyMasterMapper surveyMasterMapper;
  private final SmsSendMapper smsSendMapper;
  private final MsgQueueMapper msgQueueMapper;
  private final SendHistoryMapper sendHistoryMapper;
  private final AdminService adminService;
  private final ExcelService excelService;
  private final RsvpNonceStore rsvpNonceStore;
  private final SimpleRateLimiter simpleRateLimiter;

  @Value("${wisead.url:http://localhost:8080}")
  private String wiseadUrl;

  /** 행사 공개 정보 조회 (RSVP 페이지용) */
  @Transactional(readOnly = true)
  public EventPublicInfoResponse getEventPublicInfo(String eventCode) {
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventCode(eventCode)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "행사를 찾을 수 없습니다."));

    return EventPublicInfoResponse.from(event);
  }

  /** RSVP 제출 (사전 참석 여부 응답) */
  @Transactional
  public RsvpResponse submitRsvp(String eventCode, RsvpRequest request) {
    // 1. eventCode로 행사 조회
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventCode(eventCode)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "행사를 찾을 수 없습니다."));

    // 2. 전화번호 암호화 후 참여자 조회
    String cleanPhone = request.getPhone().replace("-", "");
    String encryptedPhone = encryptPhone(cleanPhone);

    EventParticipant participant =
        participantMapper
            .selectByEventSeqAndPhone(event.getEventSeq(), encryptedPhone)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "등록된 참여자 정보가 없습니다."));

    // 3. 영문 응답값을 한국어로 변환 후 registType 업데이트
    String registType = convertRsvpResponse(request.getResponse());
    participantMapper.updateRegistType(participant.getSeq(), registType);

    return RsvpResponse.builder()
        .participantName(participant.getUserName())
        .response(registType)
        .build();
  }

  /** 현장 참가자 등록 (공개 API) */
  @Transactional
  public OnsiteRegistrationResponse registerOnsiteParticipant(
      String eventCode, OnsiteRegistrationRequest request) {
    // 1. eventCode로 이벤트 조회
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventCode(eventCode)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 행사입니다."));

    // 2. 중복 체크 (동일 이벤트 + 이름 + 전화번호)
    String cleanPhone = request.getUserPhone().replace("-", "");
    String encryptedPhone = encryptPhone(cleanPhone);

    boolean exists =
        participantMapper.existsByEventSeqAndNameAndPhone(
            event.getEventSeq(), request.getUserName(), encryptedPhone);
    if (exists) {
      throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "이미 등록된 참가자입니다.");
    }

    // 3. SURVEY_USER 생성 (이름/이메일 포함)
    String userKey = UUID.randomUUID().toString().replace("-", "");

    SurveyUser surveyUser =
        SurveyUser.builder()
            .eventSeq(event.getEventSeq())
            .userKey(userKey)
            .userName(request.getUserName())
            .userPhone(encryptedPhone)
            .resendUserPhone(encryptedPhone)
            .userEmail(request.getUserEmail())
            .delYn("N")
            .regId("ONSITE")
            .build();

    surveyUserMapper.insertForParticipant(surveyUser);

    // 4. EVENT_PARTICIPANT 생성 (attendTime은 체크인 시 기록)
    EventParticipant participant =
        EventParticipant.create(
            surveyUser.getSeq(),
            event.getEventSeq(),
            request.getDepartment(),
            request.getPosition(),
            "일반",
            null,
            "현장등록",
            null);

    insertWithUniqueCheckCode(participant);

    // 5. 응답 반환
    EventParticipant saved =
        participantMapper
            .selectDetailBySeq(participant.getSeq())
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

    return OnsiteRegistrationResponse.from(saved, cleanPhone, null).withQrCodeUrl(wiseadUrl);
  }

  /** 참가자 인증 (이름 + 연락처로 조회) */
  @Transactional(readOnly = true)
  public VerifyParticipantResponse verifyParticipant(
      String eventCode, VerifyParticipantRequest request) {
    // 1. eventCode로 이벤트 조회
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventCode(eventCode)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 행사입니다."));

    // 2. SQL에서 이름으로 1차 필터링 (대규모 행사 성능 최적화)
    List<EventParticipant> candidates =
        participantMapper.selectByEventSeqAndUserName(event.getEventSeq(), request.getName());

    if (candidates.isEmpty()) {
      return VerifyParticipantResponse.notVerified();
    }

    // 3. Java에서 전화번호 복호화 후 비교 (2차 필터링)
    String requestPhone = request.getPhone().replace("-", "");

    for (EventParticipant candidate : candidates) {
      String decryptedPhone = decryptPhone(candidate.getUserPhone());
      if (decryptedPhone != null) {
        String cleanDecryptedPhone = decryptedPhone.replace("-", "");
        if (requestPhone.equals(cleanDecryptedPhone)) {
          return VerifyParticipantResponse.verified(buildParticipantInfo(candidate, requestPhone));
        }
      }
    }

    // 일치하는 참가자 없음
    return VerifyParticipantResponse.notVerified();
  }

  /** 참가자 QR 조회 by checkCode (인증 불필요, 문자 링크용) */
  @Transactional(readOnly = true)
  public VerifyParticipantResponse verifyParticipantByCheckCode(
      String eventCode, String checkCode) {
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventCode(eventCode)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 행사입니다."));

    EventParticipant participant =
        participantMapper.selectByEventSeqAndCheckCode(event.getEventSeq(), checkCode).orElse(null);

    if (participant == null) {
      return VerifyParticipantResponse.notVerified();
    }

    return VerifyParticipantResponse.verified(buildParticipantInfo(participant, null));
  }

  /** EventParticipant → ParticipantInfo 변환 (QR URL 포함) */
  private VerifyParticipantResponse.ParticipantInfo buildParticipantInfo(
      EventParticipant participant, String phone) {
    String qrCodeUrl =
        wiseadUrl + "/event/" + participant.getEventSeq() + "/check/" + participant.getCheckCode();

    return VerifyParticipantResponse.ParticipantInfo.builder()
        .seq(participant.getSeq())
        .name(participant.getUserName())
        .phone(phone)
        .department(participant.getDepartment())
        .position(participant.getPosition())
        .participantType(participant.getParticipantType())
        .checkCode(participant.getCheckCode())
        .eventName(participant.getEventName())
        .qrCodeUrl(qrCodeUrl)
        .build();
  }

  /** 참가자 등록 */
  @Transactional
  public EventParticipantResponse createParticipant(EventParticipantRequest request, String regId) {
    // 권한 체크: 해당 이벤트의 소유자이거나 A레벨이어야 등록 가능
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventSeq(request.getEventSeq())
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트 정보를 찾을 수 없습니다."));
    Integer userLevel = adminService.getUserLevel(regId);
    adminService.validateModifyPermission(regId, userLevel, event.getRegId());

    // 1. SURVEY_USER 생성
    String userKey = UUID.randomUUID().toString().replace("-", "");
    String encryptedPhone = encryptPhone(request.getUserPhone());

    SurveyUser surveyUser =
        SurveyUser.builder()
            .eventSeq(request.getEventSeq())
            .userKey(userKey)
            .userName(request.getUserName())
            .userPhone(encryptedPhone)
            .resendUserPhone(encryptedPhone)
            .userEmail(request.getUserEmail())
            .delYn("N")
            .regId(regId)
            .build();

    surveyUserMapper.insertForParticipant(surveyUser);

    // 2. EVENT_PARTICIPANT 생성
    EventParticipant participant =
        EventParticipant.create(
            surveyUser.getSeq(),
            request.getEventSeq(),
            request.getDepartment(),
            request.getPosition(),
            request.getParticipantType(),
            request.getMemo(),
            EventParticipant.DEFAULT_REGIST_TYPE,
            null);

    insertWithUniqueCheckCode(participant);

    // 3. 응답 반환
    EventParticipant saved =
        participantMapper
            .selectDetailBySeq(participant.getSeq())
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

    return EventParticipantResponse.from(saved).withQrCodeUrl(wiseadUrl);
  }

  /** 참가자 엑셀 일괄 등록 */
  @Transactional
  public Map<String, Object> uploadParticipantExcel(
      Integer eventSeq, MultipartFile file, String regId) {
    Map<String, Object> result = new HashMap<>();

    // === 1단계 : 파일 기본 검증 ===
    // 확장자 검증
    String originalFilename = file.getOriginalFilename();
    if (originalFilename == null
        || (!originalFilename.endsWith(".xlsx") && !originalFilename.endsWith(".xls"))) {
      throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "엑셀 파일만 업로드 가능합니다. (.xlsx, .xls)");
    }

    // 파일 크기 검증 (5MB)
    if (file.getSize() > 5 * 1024 * 1024) {
      throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED, "파일 크기는 5MB 이하만 가능합니다.");
    }

    // === 2단계: 권한 체크 (기존 createParticipant와 동일) ===
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventSeq(eventSeq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트 정보를 찾을 수 없습니다."));
    Integer userLevel = adminService.getUserLevel(regId);
    adminService.validateModifyPermission(regId, userLevel, event.getRegId());

    // === 3단계: 엑셀 데이터 읽기 ===
    // A: 이름, B: 전화번호, C: 이메일, D: 소속, E: 직급, F: 참여자유형
    // 2행부터 읽기 (1행은 헤더)
    List<Map<String, String>> excelContent =
        excelService.readExcel(file, 2, "A", "B", "C", "D", "E", "F");

    if (excelContent.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일에 데이터가 없습니다.");
    }

    // 행 수 제한 (1,000행)
    if (excelContent.size() > 1000) {
      throw new BusinessException(
          ErrorCode.INVALID_INPUT, "최대 1,000행까지 등록 가능합니다. (현재: " + excelContent.size() + "행)");
    }

    // === 4단계: 행별 검증 및 데이터 수집 ===
    int successCount = 0;
    int failCount = 0;
    List<String> errors = new ArrayList<>();

    // 유효한 참가자를 위한 리스트 (EventParticipant는 나중에 batch insert)
    List<EventParticipant> participantsToInsert = new ArrayList<>();

    // 허용되는 참여자 유형
    Set<String> validTypes = Set.of("VIP", "일반", "스태프");

    for (int i = 0; i < excelContent.size(); i++) {
      Map<String, String> row = excelContent.get(i);
      int rowNum = i + 2; // 실제 엑설 행 번호 (1행은 헤더)

      String name = row.get("A"); // 이름
      String phone = row.get("B"); // 전화번호
      String email = row.get("C"); // 이메일
      String department = row.get("D"); // 소속
      String position = row.get("E"); // 직급
      String participantType = row.get("F"); // 참여자유형

      // ----- 필수값 검증 -----
      if (name == null || name.trim().isEmpty()) {
        errors.add(rowNum + "행: 이름이 비어있습니다.");
        failCount++;
        continue;
      }
      name = name.trim();
      if (phone == null || phone.trim().isEmpty()) {
        errors.add(rowNum + "행: 전화번호가 비어있습니다.");
        failCount++;
        continue;
      }
      phone = phone.trim();
      // ----- 전화번호 형식 검증 -----
      // 하이픈 제거 후 숫자만 남겨서 검증
      String cleanPhone = phone.replace("-", "");
      if (!cleanPhone.matches("^\\d{10,13}$")) {
        errors.add(rowNum + "행: 전화번호 형식이 올바르지 않습니다.");
        failCount++;
        continue;
      }

      // ----- 이메일 형식 검증 (입력된 경우만) -----
      if (email != null && !email.trim().isEmpty()) {
        email = email.trim();
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
          errors.add(rowNum + "행: 이메일 형식이 올바르지 않습니다.");
          failCount++;
          continue;
        }
      } else {
        email = null;
      }

      // ----- 참여자유형 기본값 처리 -----
      if (participantType == null
          || participantType.trim().isEmpty()
          || !validTypes.contains(participantType.trim())) {
        participantType = "일반";
      } else {
        participantType = participantType.trim();
      }
      // ----- 중복 전화번호 검증 (같은 행사 내) -----
      String encryptedPhone = encryptPhone(cleanPhone);
      Optional<EventParticipant> existing =
          participantMapper.selectByEventSeqAndPhone(eventSeq, encryptedPhone);
      if (existing.isPresent()) {
        errors.add(rowNum + "행: 이미 등록된 전화번호입니다 (" + formatPhone(cleanPhone) + ").");
        failCount++;
        continue;
      }

      // ===== 5단계: 유효한 행 → SurveyUser 개별 INSERT =====
      // SurveyUser는 INSERT 후 생성된 seq를 가져와야 하므로 개별 insert
      String userKey = UUID.randomUUID().toString().replace("-", "");
      SurveyUser surveyUser =
          SurveyUser.builder()
              .eventSeq(eventSeq)
              .userKey(userKey)
              .userName(name)
              .userPhone(encryptedPhone)
              .resendUserPhone(encryptedPhone)
              .userEmail(email)
              .delYn("N")
              .regId(regId)
              .build();
      surveyUserMapper.insertForParticipant(surveyUser);
      // EventParticipant 생성 (나중에 batch insert)
      EventParticipant participant =
          EventParticipant.create(
              surveyUser.getSeq(),
              eventSeq,
              department != null ? department.trim() : null,
              position != null ? position.trim() : null,
              participantType,
              null, // memo
              EventParticipant.DEFAULT_REGIST_TYPE, // registType
              null); // attendTime
      participantsToInsert.add(participant);
      successCount++;
    }

    // ===== 6단계: EventParticipant 일괄 INSERT (체크코드 중복 방지) =====
    if (!participantsToInsert.isEmpty()) {
      Set<String> usedCodes = new HashSet<>();
      for (int idx = 0; idx < participantsToInsert.size(); idx++) {
        EventParticipant p = participantsToInsert.get(idx);
        String code = p.getCheckCode();
        int retryCount = 0;
        while (usedCodes.contains(code)
            || participantMapper.existsByEventSeqAndCheckCode(eventSeq, code)) {
          if (++retryCount > 10) {
            throw new BusinessException(
                ErrorCode.INTERNAL_ERROR, "체크코드 생성에 실패했습니다. 잠시 후 다시 시도해주세요.");
          }
          code = EventParticipant.generateCheckCode();
        }
        usedCodes.add(code);
        if (!code.equals(p.getCheckCode())) {
          participantsToInsert.set(idx, p.withCheckCode(code));
        }
      }
      participantMapper.insertBatch(participantsToInsert);
    }
    // ===== 7단계: 결과 반환 =====
    result.put("totalCount", excelContent.size());
    result.put("successCount", successCount);
    result.put("failureCount", failCount);
    if (!errors.isEmpty()) {
      result.put("errors", errors);
    }
    log.info(
        "참가자 엑셀 일괄 등록 완료 - eventSeq: {}, 전체: {}, 성공: {}, 실패: {}",
        eventSeq,
        excelContent.size(),
        successCount,
        failCount);
    return result;
  }

  /** 문자 발송용 참가자 전체 목록 조회 (페이징 없음, 발송 이력 포함) */
  @Transactional(readOnly = true)
  public List<ParticipantForMessageResponse> getParticipantsForMessage(Integer eventSeq) {
    List<EventParticipant> participants = participantMapper.selectByEventSeq(eventSeq);

    // sms_send에서 발송 이력이 있는 userSeq 조회
    Set<Integer> sentUserSeqs = new HashSet<>(smsSendMapper.selectSentUserSeqs(eventSeq));

    return participants.stream()
        .map(
            p ->
                ParticipantForMessageResponse.builder()
                    .participantSeq(p.getSeq())
                    .surveyUserSeq(p.getSurveyUserSeq())
                    .name(p.getUserName())
                    .phone(decryptPhone(p.getUserPhone()))
                    .checkCode(p.getCheckCode())
                    .department(p.getDepartment())
                    .position(p.getPosition())
                    .participantType(p.getParticipantType())
                    .registType(p.getRegistTypeOrDefault())
                    .messageSent(sentUserSeqs.contains(p.getSurveyUserSeq()))
                    .build())
        .collect(Collectors.toList());
  }

  /** 문자 발송용 참가자 자동 등록 (미등록 시 생성, 기등록 시 기존 정보 반환) */
  @Transactional
  public ParticipantForMessageResponse registerParticipantForMessage(
      Integer eventSeq, String name, String phone, String regId) {

    String encryptedPhone = encryptPhone(phone);

    // 1. 기존 참여자 조회 (같은 행사 + 같은 전화번호)
    Optional<EventParticipant> existing =
        participantMapper.selectByEventSeqAndPhone(eventSeq, encryptedPhone);

    if (existing.isPresent()) {
      EventParticipant p = existing.get();
      return ParticipantForMessageResponse.builder()
          .participantSeq(p.getSeq())
          .surveyUserSeq(p.getSurveyUserSeq())
          .name(p.getUserName())
          .phone(phone)
          .checkCode(p.getCheckCode())
          .department(p.getDepartment())
          .position(p.getPosition())
          .participantType(p.getParticipantType())
          .registType(p.getRegistTypeOrDefault())
          .build();
    }

    // 2. SURVEY_USER 생성
    String userKey = UUID.randomUUID().toString().replace("-", "");
    SurveyUser surveyUser =
        SurveyUser.builder()
            .eventSeq(eventSeq)
            .userKey(userKey)
            .userName(name)
            .userPhone(encryptedPhone)
            .resendUserPhone(encryptedPhone)
            .delYn("N")
            .regId(regId)
            .build();
    surveyUserMapper.insertForParticipant(surveyUser);

    // 3. EVENT_PARTICIPANT 생성 (이름/전화번호만, 소속/직급은 참여자 관리에서 수정)
    EventParticipant participant =
        EventParticipant.create(
            surveyUser.getSeq(),
            eventSeq,
            null,
            null,
            "일반",
            "문자발송 시 자동등록",
            EventParticipant.DEFAULT_REGIST_TYPE,
            null);
    insertWithUniqueCheckCode(participant);

    return ParticipantForMessageResponse.builder()
        .participantSeq(participant.getSeq())
        .surveyUserSeq(surveyUser.getSeq())
        .name(name)
        .phone(phone)
        .checkCode(participant.getCheckCode())
        .participantType("일반")
        .registType(EventParticipant.DEFAULT_REGIST_TYPE)
        .build();
  }

  /** 참가자 목록 조회 */
  @Transactional(readOnly = true)
  public PageResponse<EventParticipantResponse> getParticipants(ParticipantSearchRequest request) {
    Map<String, Object> params = new HashMap<>();
    params.put("eventSeq", request.getEventSeq());
    params.put("keyword", request.getKeyword());
    params.put("participantType", request.getParticipantType());
    params.put("registType", request.getRegistType());
    params.put("offset", request.getOffset());
    params.put("limit", request.getSize());

    List<EventParticipant> participants = participantMapper.searchParticipants(params);
    int total = participantMapper.countSearchParticipants(params);

    List<EventParticipantResponse> content =
        participants.stream()
            .map(
                p -> {
                  EventParticipantResponse response = EventParticipantResponse.from(p);
                  // 전화번호 복호화
                  if (p.getUserPhone() != null) {
                    response.setUserPhone(decryptPhone(p.getUserPhone()));
                  }
                  return response.withQrCodeUrl(wiseadUrl);
                })
            .collect(Collectors.toList());

    return PageResponse.of(content, request.getPage(), request.getSize(), total);
  }

  /** 참가자 상세 조회 */
  @Transactional(readOnly = true)
  public EventParticipantResponse getParticipant(Long seq) {
    EventParticipant participant =
        participantMapper
            .selectDetailBySeq(seq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

    return buildParticipantResponse(participant);
  }

  /** 체크코드로 참가자 조회 */
  @Transactional(readOnly = true)
  public EventParticipantResponse getParticipantByCheckCode(String checkCode) {
    EventParticipant participant =
        participantMapper
            .selectDetailByCheckCode(checkCode)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

    return buildParticipantResponse(participant);
  }

  /** 참가자 응답 객체 생성 (공통 로직) */
  private EventParticipantResponse buildParticipantResponse(EventParticipant participant) {
    EventParticipantResponse response = EventParticipantResponse.from(participant);
    if (participant.getUserPhone() != null) {
      response.setUserPhone(decryptPhone(participant.getUserPhone()));
    }
    return response.withQrCodeUrl(wiseadUrl);
  }

  /** 참가자 수정 */
  @Transactional
  public EventParticipantResponse updateParticipant(
      Long seq, EventParticipantRequest request, String userId) {
    EventParticipant participant =
        participantMapper
            .selectBySeq(seq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

    // 권한 체크: 해당 이벤트의 소유자이거나 A레벨이어야 수정 가능
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventSeq(participant.getEventSeq())
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트 정보를 찾을 수 없습니다."));
    Integer userLevel = adminService.getUserLevel(userId);
    adminService.validateModifyPermission(userId, userLevel, event.getRegId());

    participant.update(
        request.getDepartment(),
        request.getPosition(),
        request.getParticipantType(),
        request.getMemo(),
        request.getRegistType());

    participantMapper.update(participant);

    return getParticipant(seq);
  }

  /** 참가자 삭제 */
  @Transactional
  public void deleteParticipant(Long seq, String uptId) {
    EventParticipant participant =
        participantMapper
            .selectBySeq(seq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

    // 권한 체크: 해당 이벤트의 소유자이거나 A레벨이어야 삭제 가능
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventSeq(participant.getEventSeq())
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트 정보를 찾을 수 없습니다."));
    Integer userLevel = adminService.getUserLevel(uptId);
    adminService.validateModifyPermission(uptId, userLevel, event.getRegId());

    // 관련 로그 삭제
    actionLogMapper.deleteByParticipantSeq(seq);
    nametagLogMapper.deleteByParticipantSeq(seq);

    // 참가자 삭제
    participantMapper.delete(seq);

    // SURVEY_USER 소프트 삭제
    surveyUserMapper.softDelete(participant.getSurveyUserSeq(), uptId);
  }

  /** 참가자 상태 조회 (액션 현황 포함) */
  @Transactional(readOnly = true)
  public ParticipantStatusResponse getParticipantStatus(Long seq) {
    EventParticipant participant =
        participantMapper
            .selectDetailBySeq(seq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

    List<Map<String, Object>> actionStatusList =
        actionLogMapper.selectActionStatusByParticipantSeq(seq, participant.getEventSeq());

    List<ParticipantStatusResponse.ActionStatus> actions =
        actionStatusList.stream()
            .map(
                m ->
                    ParticipantStatusResponse.ActionStatus.builder()
                        .actionTypeSeq(((Number) m.get("actionTypeSeq")).longValue())
                        .actionCode((String) m.get("actionCode"))
                        .actionName((String) m.get("actionName"))
                        .completed("Y".equals(m.get("completed")))
                        //                        .completedAt((LocalDateTime) m.get("completedAt"))
                        .completedAt(
                            m.get("completedAt") != null
                                ? ((Timestamp) m.get("completedAt")).toLocalDateTime()
                                : null)
                        .confirmedBy((String) m.get("confirmedBy"))
                        .requireAdminAuth("Y".equals(m.get("requireAdminAuth")))
                        .allowMultiple("Y".equals(m.get("allowMultiple")))
                        .build())
            .collect(Collectors.toList());

    return ParticipantStatusResponse.builder()
        .participant(
            ParticipantStatusResponse.ParticipantInfo.builder()
                .seq(participant.getSeq())
                .checkCode(participant.getCheckCode())
                .name(participant.getUserName())
                .department(participant.getDepartment())
                .position(participant.getPosition())
                .participantType(participant.getParticipantType())
                .phone(decryptPhone(participant.getUserPhone()))
                .email(participant.getUserEmail())
                .nametagPrinted(participant.getNametagPrinted())
                .build())
        .actions(actions)
        .build();
  }

  /** 체크코드로 참가자 상태 조회 */
  @Transactional(readOnly = true)
  public ParticipantStatusResponse getParticipantStatusByCheckCode(
      Integer eventSeq, String checkCode) {
    EventParticipant participant =
        participantMapper
            .selectByEventSeqAndCheckCode(eventSeq, checkCode)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

    return getParticipantStatus(participant.getSeq());
  }

  // ==================== 행사 통합 링크 ====================

  /** 통합 링크 페이지 상태 조회. eventSeq + checkCode capability 모델. */
  @Transactional(readOnly = true)
  public UnifiedLinkStateResponse getUnifiedLinkState(
      Integer eventSeq, String checkCode, String clientIp) {
    // 브루트포스 / 열거 공격 방어: /state 도 rate limit 적용 (IP + eventSeq 기준 1초 1회).
    String stateRateKey = clientIp + ":" + eventSeq + ":state";
    if (!simpleRateLimiter.tryAcquire(stateRateKey)) {
      throw new RateLimitExceededException("요청이 너무 빈번합니다. 잠시 후 다시 시도해 주세요.");
    }

    // selectByEventSeqAndCheckCode가 participant의 eventSeq FK로 event 존재를 이미 증명한다.
    // 별도 selectByEventSeq 호출은 selectWithCounts의 비싼 서브쿼리를 유발하므로 제거.
    EventParticipant participant =
        participantMapper
            .selectByEventSeqAndCheckCode(eventSeq, checkCode)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

    SurveyMasterPreSurveyDto preSurvey =
        surveyMasterMapper.selectPreSurveyDatesByEventSeq(eventSeq);
    boolean preSurveyActive = isPreSurveyActive(preSurvey);

    String registType = participant.getRegistType();
    String mode;
    if (!preSurveyActive) {
      mode = "QR_ONLY";
    } else if (registType == null || registType.isEmpty()) {
      mode = "RSVP";
    } else if (RegistTypeMapper.isAttendType(registType)) {
      mode = "RSVP_ANSWERED_ATTEND";
    } else if (RegistTypeMapper.ABSENT.equals(registType)) {
      mode = "RSVP_ANSWERED_ABSENT";
    } else {
      mode = "RSVP";
    }

    String qrCodeUrl = null;
    if ("QR_ONLY".equals(mode) || "RSVP_ANSWERED_ATTEND".equals(mode)) {
      qrCodeUrl = wiseadUrl + "/event/" + eventSeq + "/check/" + checkCode;
    }

    String nonce = null;
    if (!"QR_ONLY".equals(mode)) {
      nonce = rsvpNonceStore.issue(eventSeq, checkCode);
      if (nonce == null) {
        // 저장소 포화. fail-fast로 cryptic nonce 에러를 방지.
        throw new BusinessException(
            ErrorCode.INTERNAL_SERVER_ERROR, "일시적인 서버 부하로 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.");
      }
    }

    log.debug(
        "unified link state: eventSeq={}, participantSeq={}, mode={}, ip={}",
        eventSeq,
        participant.getSeq(),
        mode,
        clientIp);

    return UnifiedLinkStateResponse.builder()
        .mode(mode)
        .participant(
            UnifiedLinkStateResponse.ParticipantSummary.builder()
                .name(participant.getUserName())
                .checkCode(participant.getCheckCode())
                .build())
        .qrCodeUrl(qrCodeUrl)
        .registType(registType)
        .preSurveyActive(preSurveyActive)
        .nonce(nonce)
        .build();
  }

  /** 통합 링크 RSVP 제출. capability 모델 + nonce + rate limit. */
  @Transactional
  public RsvpSubmitResponse submitRsvpByCheckCode(
      Integer eventSeq, String checkCode, String response, String nonce, String clientIp) {

    String rateKey = clientIp + ":" + eventSeq + ":rsvp";
    if (!simpleRateLimiter.tryAcquire(rateKey)) {
      throw new RateLimitExceededException("요청이 너무 빈번합니다. 잠시 후 다시 시도해 주세요.");
    }

    if (!rsvpNonceStore.validateAndConsume(eventSeq, checkCode, nonce)) {
      throw new BusinessException(ErrorCode.INVALID_TOKEN, "인증 토큰이 유효하지 않습니다. 페이지를 새로고침해 주세요.");
    }

    EventParticipant participant =
        participantMapper
            .selectByEventSeqAndCheckCode(eventSeq, checkCode)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

    SurveyMasterPreSurveyDto preSurvey =
        surveyMasterMapper.selectPreSurveyDatesByEventSeq(eventSeq);
    if (!isPreSurveyActive(preSurvey)) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED, "사전설문기간이 아닙니다.");
    }

    String prevRegistType = participant.getRegistType();
    String newRegistType;
    try {
      newRegistType = RegistTypeMapper.toKorean(response);
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, e.getMessage());
    }

    participantMapper.updateRegistType(participant.getSeq(), newRegistType);

    log.info(
        "RSVP updated: eventSeq={}, participantSeq={}, from={}, to={}, ip={}",
        eventSeq,
        participant.getSeq(),
        prevRegistType,
        newRegistType,
        clientIp);

    return RsvpSubmitResponse.builder()
        .registType(newRegistType)
        .prevRegistType(prevRegistType)
        .build();
  }

  /** 사전설문기간 활성 여부. 양쪽 날짜 중 하나라도 NULL이면 비활성(=QR_ONLY 폴백). */
  private static boolean isPreSurveyActive(SurveyMasterPreSurveyDto preSurvey) {
    if (preSurvey == null) return false;
    LocalDateTime start = preSurvey.getPreSurveyStartDate();
    LocalDateTime end = preSurvey.getPreSurveyEndDate();
    if (start == null || end == null) return false;
    LocalDateTime now = LocalDateTime.now();
    return !start.isAfter(now) && !end.isBefore(now);
  }

  /** RSVP 영문 응답값을 한국어로 변환 */
  private String convertRsvpResponse(String response) {
    return switch (response) {
      case "attend", "preregister" -> "사전등록";
      case "absent" -> "불참석";
      default -> response; // 이미 한국어인 경우 그대로 반환
    };
  }

  /** 전화번호 암호화 */
  private String encryptPhone(String phone) {
    if (phone == null) return null;
    String cleanPhone = phone.replace("-", "");
    return CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(cleanPhone));
  }

  /** 전화번호 복호화 */
  private String decryptPhone(String encryptedPhone) {
    if (encryptedPhone == null) return null;
    try {
      return CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(encryptedPhone));
    } catch (Exception e) {
      log.warn("전화번호 복호화 실패: {}", e.getMessage());
      return encryptedPhone;
    }
  }

  // ==================== 통계 기능 ====================

  /** 행사 통계 조회 */
  @Transactional(readOnly = true)
  public EventStatisticsResponse getStatistics(Integer eventSeq) {
    // 이벤트 정보 조회
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventSeq(eventSeq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "행사 정보를 찾을 수 없습니다."));

    // 참가자 통계 (체크인 현황)
    Map<String, Object> checkInStats = participantMapper.selectCheckInStats(eventSeq);
    int totalCount = getIntValue(checkInStats, "totalCount");
    int checkedInCount = getIntValue(checkInStats, "checkedInCount");
    int notCheckedInCount = totalCount - checkedInCount;
    double checkedInRate =
        totalCount > 0 ? Math.round((double) checkedInCount / totalCount * 1000) / 10.0 : 0;

    // 등록구분별 통계 (registType)
    Map<String, Object> registTypeStats = participantMapper.selectRegistTypeStats(eventSeq);
    int preRegisteredCount = getIntValue(registTypeStats, "preRegisteredCount");
    int onsiteRegisteredCount = getIntValue(registTypeStats, "onsiteRegisteredCount");
    int absentCount = getIntValue(registTypeStats, "absentCount");
    int unregisteredCount = getIntValue(registTypeStats, "unregisteredCount");

    // 참가자 유형별 통계
    List<Map<String, Object>> typeStats = participantMapper.selectParticipantTypeStats(eventSeq);
    List<EventStatisticsResponse.ParticipantTypeStat> byType =
        typeStats.stream()
            .map(
                m ->
                    EventStatisticsResponse.ParticipantTypeStat.builder()
                        .participantType((String) m.get("participantType"))
                        .count(getIntValue(m, "count"))
                        .checkedInCount(getIntValue(m, "checkedInCount"))
                        .build())
            .collect(Collectors.toList());

    EventStatisticsResponse.ParticipantSummary participantSummary =
        EventStatisticsResponse.ParticipantSummary.builder()
            .totalCount(totalCount)
            .checkedInCount(checkedInCount)
            .notCheckedInCount(notCheckedInCount)
            .checkedInRate(checkedInRate)
            .preRegisteredCount(preRegisteredCount)
            .onsiteRegisteredCount(onsiteRegisteredCount)
            .absentCount(absentCount)
            .unregisteredCount(unregisteredCount)
            .byType(byType)
            .build();

    // RSVP 통계 (같은 registType 데이터, 다른 관점)
    EventStatisticsResponse.RsvpSummary rsvpSummary =
        EventStatisticsResponse.RsvpSummary.builder()
            .totalCount(totalCount)
            .attendCount(preRegisteredCount)
            .absentCount(absentCount)
            .noResponseCount(unregisteredCount)
            .onsiteRegisteredCount(onsiteRegisteredCount)
            .build();

    // 문자 발송 통계
    EventStatisticsResponse.MessageSummary messageSummary = buildMessageSummary(event, eventSeq);

    // 액션별 통계
    List<Map<String, Object>> actionStats = actionLogMapper.selectActionStatsByEventSeq(eventSeq);
    List<EventStatisticsResponse.ActionStatistics> actionStatistics =
        actionStats.stream()
            .map(
                m -> {
                  int actionTotal = getIntValue(m, "totalCount");
                  int completedCount = getIntValue(m, "completedCount");
                  double completionRate =
                      actionTotal > 0
                          ? Math.round((double) completedCount / actionTotal * 1000) / 10.0
                          : 0;

                  return EventStatisticsResponse.ActionStatistics.builder()
                      .actionTypeSeq(((Number) m.get("actionTypeSeq")).longValue())
                      .actionCode((String) m.get("actionCode"))
                      .actionName((String) m.get("actionName"))
                      .totalCount(actionTotal)
                      .completedCount(completedCount)
                      .completionRate(completionRate)
                      .build();
                })
            .collect(Collectors.toList());

    // 명찰 출력 통계
    Map<String, Object> nametagStats = participantMapper.selectNametagStats(eventSeq);
    int nametagTotal = getIntValue(nametagStats, "totalCount");
    int printedCount = getIntValue(nametagStats, "printedCount");
    int notPrintedCount = getIntValue(nametagStats, "notPrintedCount");
    double printRate =
        nametagTotal > 0 ? Math.round((double) printedCount / nametagTotal * 1000) / 10.0 : 0;

    EventStatisticsResponse.NametagSummary nametagSummary =
        EventStatisticsResponse.NametagSummary.builder()
            .totalCount(nametagTotal)
            .printedCount(printedCount)
            .notPrintedCount(notPrintedCount)
            .printRate(printRate)
            .build();

    return EventStatisticsResponse.builder()
        .eventSeq(eventSeq)
        .eventName(event.getEventName())
        .participantSummary(participantSummary)
        .rsvpSummary(rsvpSummary)
        .messageSummary(messageSummary)
        .actionStatistics(actionStatistics)
        .nametagSummary(nametagSummary)
        .build();
  }

  // ==================== 엑셀 다운로드 ====================

  /**
   * 엑셀 다운로드용 참가자 데이터 조회 (Map 형태)
   *
   * <p>전화번호 복호화가 필요하므로 Map 형태로 반환합니다. Entity 직접 반환 시 암호화된 전화번호가 포함되어 있어 별도 변환이 필요합니다.
   */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> getParticipantsForExcelAsMap(Integer eventSeq) {
    List<EventParticipant> participants = participantMapper.selectAllForExcel(eventSeq);

    return participants.stream()
        .map(
            p -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("이름", p.getUserName());
              row.put("연락처", formatPhone(decryptPhone(p.getUserPhone())));
              row.put("이메일", p.getUserEmail());
              row.put("소속", p.getDepartment());
              row.put("직책", p.getPosition());
              row.put("참가자 유형", p.getParticipantType());
              row.put("체크코드", p.getCheckCode());
              row.put("등록구분", p.getRegistType() != null ? p.getRegistType() : "미등록");
              row.put("명찰 출력", "Y".equals(p.getNametagPrinted()) ? "출력완료" : "미출력");
              row.put("액션 현황", p.getActionSummary());
              row.put("메모", p.getMemo());
              row.put("등록일", p.getRegDate() != null ? p.getRegDate().toString() : "");
              return row;
            })
        .collect(Collectors.toList());
  }

  private int getIntValue(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value == null) return 0;
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    try {
      return Integer.parseInt(value.toString());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  /** 문자 발송 통계 조회 (msg_result_YYYYMM + msg_queue) */
  private EventStatisticsResponse.MessageSummary buildMessageSummary(
      SurveyMaster event, Integer eventSeq) {
    int totalSent = 0;
    int successCount = 0;
    int failCount = 0;

    // msg_result_YYYYMM에서 완료된 발송 통계 조회 (행사 기간의 월별 테이블)
    try {
      List<String> tableNames = getResultTableNames(event.getStartDate());
      for (String tableName : tableNames) {
        try {
          Map<String, Object> stats =
              sendHistoryMapper.selectMessageStatsByEventSeq(tableName, eventSeq);
          if (stats != null) {
            totalSent += getIntValue(stats, "totalSent");
            successCount += getIntValue(stats, "successCount");
            failCount += getIntValue(stats, "failCount");
          }
        } catch (Exception e) {
          log.debug("msg_result 테이블 조회 실패 (테이블 미존재 가능): {}", tableName);
        }
      }
    } catch (Exception e) {
      log.warn("문자 발송 통계 조회 실패 - eventSeq: {}, error: {}", eventSeq, e.getMessage());
    }

    // msg_queue에서 대기 중인 발송 건수
    int pendingCount = 0;
    try {
      pendingCount = msgQueueMapper.countPendingByEventSeq(eventSeq);
    } catch (Exception e) {
      log.warn("대기 발송 건수 조회 실패 - eventSeq: {}, error: {}", eventSeq, e.getMessage());
    }

    totalSent += pendingCount;

    return EventStatisticsResponse.MessageSummary.builder()
        .totalSent(totalSent)
        .successCount(successCount)
        .failCount(failCount)
        .pendingCount(pendingCount)
        .build();
  }

  /** 행사 시작일 기준으로 현재까지의 msg_result_YYYYMM 테이블명 목록 생성 */
  private List<String> getResultTableNames(String startDateStr) {
    List<String> tableNames = new ArrayList<>();
    DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyyMM");
    YearMonth startMonth;
    try {
      LocalDate startDate = LocalDate.parse(startDateStr.substring(0, 10));
      startMonth = YearMonth.from(startDate);
    } catch (Exception e) {
      // 파싱 실패 시 현재 월만 조회
      startMonth = YearMonth.now();
    }

    YearMonth currentMonth = YearMonth.now();
    YearMonth month = startMonth;
    while (!month.isAfter(currentMonth)) {
      tableNames.add("msg_result_" + month.format(monthFormatter));
      month = month.plusMonths(1);
    }
    return tableNames;
  }

  /** 참가자 INSERT (체크코드 충돌 시 재생성) */
  private void insertWithUniqueCheckCode(EventParticipant participant) {
    for (int i = 0; i < 10; i++) {
      if (!participantMapper.existsByEventSeqAndCheckCode(
          participant.getEventSeq(), participant.getCheckCode())) {
        participantMapper.insert(participant);
        return;
      }
      // 충돌 시 새 코드로 재설정
      participant = participant.withCheckCode(EventParticipant.generateCheckCode());
    }
    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "체크코드 생성에 실패했습니다. 잠시 후 다시 시도해주세요.");
  }

  private String formatPhone(String phone) {
    if (phone == null || phone.length() < 10) return phone;
    // 01012345678 -> 010-1234-5678
    if (phone.length() == 11) {
      return phone.substring(0, 3) + "-" + phone.substring(3, 7) + "-" + phone.substring(7);
    } else if (phone.length() == 10) {
      return phone.substring(0, 3) + "-" + phone.substring(3, 6) + "-" + phone.substring(6);
    }
    return phone;
  }
}
