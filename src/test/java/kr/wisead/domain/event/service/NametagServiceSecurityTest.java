package kr.wisead.domain.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.ratelimit.FailureRateLimiter;
import kr.wisead.common.ratelimit.RateLimitExceededException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.event.dto.NametagPrintRequest;
import kr.wisead.domain.event.dto.NametagResponse;
import kr.wisead.domain.event.entity.EventParticipant;
import kr.wisead.domain.event.entity.EventNametagLog;
import kr.wisead.domain.survey.entity.SurveyMaster;
import kr.wisead.mapper.primary.EventNametagLogMapper;
import kr.wisead.mapper.primary.EventParticipantMapper;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NametagServiceSecurityTest {

  private static final Integer EVENT_SEQ = 100;
  private static final Integer OTHER_EVENT_SEQ = 200;
  private static final Long PARTICIPANT_SEQ = 10L;
  private static final String OWNER_ID = "owner";
  private static final String OTHER_USER_ID = "other";
  private static final String CHECK_CODE = "ABCDE";
  private static final String CLIENT_IP = "1.2.3.4";

  @Mock private EventParticipantMapper participantMapper;
  @Mock private EventNametagLogMapper nametagLogMapper;
  @Mock private SurveyMasterMapper surveyMasterMapper;
  @Mock private AdminService adminService;
  @Mock private FailureRateLimiter failureRateLimiter;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  private NametagService service;

  @BeforeEach
  void setUp() {
    EventAccessValidator eventAccessValidator =
        new EventAccessValidator(surveyMasterMapper, adminService);
    service =
        new NametagService(
            participantMapper,
            nametagLogMapper,
            objectMapper,
            failureRateLimiter,
            eventAccessValidator);
  }

  @Test
  @DisplayName("인증 명찰 read는 비소유자 접근을 차단한다")
  void getNametagData_deniesNonOwner() {
    givenEventOwner();
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "접근 권한이 없습니다."))
        .when(adminService)
        .validateModifyPermission(OTHER_USER_ID, 1, OWNER_ID);

    assertThatThrownBy(() -> service.getNametagData(EVENT_SEQ, PARTICIPANT_SEQ, OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);
    verify(participantMapper, never()).selectDetailBySeq(any());
  }

  @Test
  @DisplayName("인증 명찰 read에서 path eventSeq와 참가자 eventSeq가 다르면 RESOURCE_NOT_FOUND")
  void getNametagData_rejectsEventSeqMismatch() {
    givenEventOwner();
    when(participantMapper.selectDetailBySeq(PARTICIPANT_SEQ))
        .thenReturn(Optional.of(participant(OTHER_EVENT_SEQ, config(true))));

    assertThatThrownBy(() -> service.getNametagData(EVENT_SEQ, PARTICIPANT_SEQ, OWNER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  @DisplayName("인증 명찰 read 비소유자는 참가자 eventSeq 일치 여부와 무관하게 ACCESS_DENIED")
  void getNametagData_deniesNonOwnerBeforeParticipantEventValidation() {
    givenEventOwner();
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "접근 권한이 없습니다."))
        .when(adminService)
        .validateModifyPermission(OTHER_USER_ID, 1, OWNER_ID);

    assertThatThrownBy(() -> service.getNametagData(EVENT_SEQ, PARTICIPANT_SEQ, OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);
    verify(participantMapper, never()).selectDetailBySeq(any());
  }

  @Test
  @DisplayName("인증 명찰 print는 소유권 확인 후 로그를 기록한다")
  void recordPrint_validatesReadAccess() {
    when(participantMapper.selectBySeq(PARTICIPANT_SEQ))
        .thenReturn(Optional.of(participant(EVENT_SEQ, "{\"contact\":true}")));
    givenEventOwner();

    service.recordPrint(EVENT_SEQ, printRequest(), OWNER_ID);

    verify(adminService).validateModifyPermission(OWNER_ID, 1, OWNER_ID);
    verify(nametagLogMapper).insert(any(EventNametagLog.class));
  }

  @Test
  @DisplayName("인증 명찰 print 비소유자는 출력 부작용 없이 차단된다")
  void recordPrint_deniesNonOwnerBeforeWrite() {
    givenEventOwner();
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "접근 권한이 없습니다."))
        .when(adminService)
        .validateModifyPermission(OTHER_USER_ID, 1, OWNER_ID);

    assertThatThrownBy(() -> service.recordPrint(EVENT_SEQ, printRequest(), OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);
    verify(participantMapper, never()).selectBySeq(any());
    verify(nametagLogMapper, never()).insert(any(EventNametagLog.class));
  }

  @Test
  @DisplayName("인증 명찰 print 비소유자는 참가자 eventSeq 일치 여부와 무관하게 ACCESS_DENIED")
  void recordPrint_deniesNonOwnerBeforeParticipantEventValidation() {
    givenEventOwner();
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "접근 권한이 없습니다."))
        .when(adminService)
        .validateModifyPermission(OTHER_USER_ID, 1, OWNER_ID);

    assertThatThrownBy(() -> service.recordPrint(EVENT_SEQ, printRequest(), OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);
    verify(participantMapper, never()).selectBySeq(any());
    verify(nametagLogMapper, never()).insert(any(EventNametagLog.class));
  }

  @Test
  @DisplayName("공개 명찰 GET은 contact 필드 enabled=true일 때 contact를 채운다")
  void publicNametag_includesContactWhenContactFieldEnabled() {
    when(participantMapper.selectDetailByEventSeqAndCheckCode(EVENT_SEQ, CHECK_CODE))
        .thenReturn(Optional.of(participant(EVENT_SEQ, config(true))));

    NametagResponse response = service.getNametagDataByCheckCode(EVENT_SEQ, CHECK_CODE, CLIENT_IP);

    assertThat(response.getContact()).isEqualTo("01012345678");
  }

  @Test
  @DisplayName("공개 명찰 GET은 contact 필드 enabled=false이면 contact를 null로 둔다")
  void publicNametag_omitsContactWhenContactFieldDisabled() {
    when(participantMapper.selectDetailByEventSeqAndCheckCode(EVENT_SEQ, CHECK_CODE))
        .thenReturn(Optional.of(participant(EVENT_SEQ, config(false))));

    NametagResponse response = service.getNametagDataByCheckCode(EVENT_SEQ, CHECK_CODE, CLIENT_IP);

    assertThat(response.getContact()).isNull();
  }

  @Test
  @DisplayName("공개 명찰 GET은 contact 필드가 없으면 contact를 null로 둔다")
  void publicNametag_omitsContactWhenContactFieldMissing() {
    when(participantMapper.selectDetailByEventSeqAndCheckCode(EVENT_SEQ, CHECK_CODE))
        .thenReturn(Optional.of(participant(EVENT_SEQ, "{\"fields\":[{\"key\":\"name\",\"enabled\":true}]}")));

    NametagResponse response = service.getNametagDataByCheckCode(EVENT_SEQ, CHECK_CODE, CLIENT_IP);

    assertThat(response.getContact()).isNull();
  }

  @Test
  @DisplayName("공개 명찰 GET은 malformed config이면 contact를 null로 둔다")
  void publicNametag_omitsContactWhenConfigMalformed() {
    when(participantMapper.selectDetailByEventSeqAndCheckCode(EVENT_SEQ, CHECK_CODE))
        .thenReturn(Optional.of(participant(EVENT_SEQ, "{malformed")));

    NametagResponse response = service.getNametagDataByCheckCode(EVENT_SEQ, CHECK_CODE, CLIENT_IP);

    assertThat(response.getContact()).isNull();
  }

  @Test
  @DisplayName("공개 명찰 GET은 null config이면 contact를 null로 둔다")
  void publicNametag_omitsContactWhenConfigNull() {
    when(participantMapper.selectDetailByEventSeqAndCheckCode(EVENT_SEQ, CHECK_CODE))
        .thenReturn(Optional.of(participant(EVENT_SEQ, null)));

    NametagResponse response = service.getNametagDataByCheckCode(EVENT_SEQ, CHECK_CODE, CLIENT_IP);

    assertThat(response.getContact()).isNull();
  }

  @Test
  @DisplayName("공개 명찰 GET 미발견 + IP는 실패 카운터를 누적하고 한도 초과 시 RateLimitExceeded")
  void publicNametag_rateLimitsBruteForceOnNotFound() {
    when(participantMapper.selectDetailByEventSeqAndCheckCode(EVENT_SEQ, CHECK_CODE))
        .thenReturn(Optional.empty());
    when(failureRateLimiter.recordFailureAndCheckAllowed(CLIENT_IP + ":" + EVENT_SEQ + ":check"))
        .thenReturn(false);

    assertThatThrownBy(() -> service.getNametagDataByCheckCode(EVENT_SEQ, CHECK_CODE, CLIENT_IP))
        .isInstanceOf(RateLimitExceededException.class);
  }

  @Test
  @DisplayName("공개 명찰 GET 미발견이고 한도 내면 실패를 기록하고 RESOURCE_NOT_FOUND")
  void publicNametag_recordsFailureAndReturnsNotFoundWithinLimit() {
    when(participantMapper.selectDetailByEventSeqAndCheckCode(EVENT_SEQ, CHECK_CODE))
        .thenReturn(Optional.empty());
    when(failureRateLimiter.recordFailureAndCheckAllowed(CLIENT_IP + ":" + EVENT_SEQ + ":check"))
        .thenReturn(true);

    assertThatThrownBy(() -> service.getNametagDataByCheckCode(EVENT_SEQ, CHECK_CODE, CLIENT_IP))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    verify(failureRateLimiter).recordFailureAndCheckAllowed(CLIENT_IP + ":" + EVENT_SEQ + ":check");
  }

  @Test
  @DisplayName("공개 명찰 GET 미발견이고 clientIp가 없으면 rate limiter를 건드리지 않는다")
  void publicNametag_skipsRateLimiterWhenNoClientIp() {
    when(participantMapper.selectDetailByEventSeqAndCheckCode(EVENT_SEQ, CHECK_CODE))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getNametagDataByCheckCode(EVENT_SEQ, CHECK_CODE, null))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    verify(failureRateLimiter, never()).recordFailureAndCheckAllowed(any());
  }

  @Test
  @DisplayName("공개 명찰 print 미발견 + IP 한도 초과 시 출력 부작용 없이 RateLimitExceeded")
  void publicNametagPrint_rateLimitsBruteForceOnNotFound() {
    when(participantMapper.selectByEventSeqAndCheckCode(EVENT_SEQ, CHECK_CODE))
        .thenReturn(Optional.empty());
    when(failureRateLimiter.recordFailureAndCheckAllowed(CLIENT_IP + ":" + EVENT_SEQ + ":check"))
        .thenReturn(false);

    assertThatThrownBy(
            () ->
                service.recordPrintByCheckCode(
                    EVENT_SEQ, CHECK_CODE, printRequest(), null, CLIENT_IP))
        .isInstanceOf(RateLimitExceededException.class);
    verify(nametagLogMapper, never()).insert(any(EventNametagLog.class));
  }

  private void givenEventOwner() {
    when(surveyMasterMapper.selectByEventSeq(EVENT_SEQ))
        .thenReturn(Optional.of(SurveyMaster.builder().eventSeq(EVENT_SEQ).regId(OWNER_ID).build()));
    when(adminService.getUserLevel(any())).thenReturn(1);
  }

  private NametagPrintRequest printRequest() {
    NametagPrintRequest request = new NametagPrintRequest();
    request.setParticipantSeq(PARTICIPANT_SEQ);
    request.setTemplateType("DEFAULT");
    return request;
  }

  private EventParticipant participant(Integer eventSeq, String nametagConfig) {
    return EventParticipant.builder()
        .seq(PARTICIPANT_SEQ)
        .eventSeq(eventSeq)
        .surveyUserSeq(1)
        .checkCode(CHECK_CODE)
        .eventName("행사")
        .userName(CryptoUtils.encodeBase64(CryptoUtils.encryptAES256("홍길동")))
        .userPhone(CryptoUtils.encodeBase64(CryptoUtils.encryptAES256("01012345678")))
        .department("개발팀")
        .position("매니저")
        .participantType("일반")
        .nametagPrinted("N")
        .nametagConfig(nametagConfig)
        .build();
  }

  private String config(boolean contactEnabled) {
    return "{\"fields\":["
        + "{\"key\":\"name\",\"label\":\"이름\",\"enabled\":true,\"order\":1,\"fontSize\":14},"
        + "{\"key\":\"contact\",\"label\":\"연락처\",\"enabled\":"
        + contactEnabled
        + ",\"order\":2,\"fontSize\":12}"
        + "],\"paperSize\":\"A6\"}";
  }
}
