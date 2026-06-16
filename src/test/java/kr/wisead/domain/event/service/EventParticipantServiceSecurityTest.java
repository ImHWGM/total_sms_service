package kr.wisead.domain.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.ratelimit.FailureRateLimiter;
import kr.wisead.common.ratelimit.RateLimitExceededException;
import kr.wisead.common.ratelimit.SimpleRateLimiter;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.event.dto.ParticipantSearchRequest;
import kr.wisead.domain.event.dto.ParticipantStatusResponse;
import kr.wisead.domain.event.entity.EventParticipant;
import kr.wisead.domain.event.security.RsvpNonceStore;
import kr.wisead.domain.excel.service.ExcelService;
import kr.wisead.domain.survey.entity.SurveyMaster;
import kr.wisead.mapper.primary.EventActionLogMapper;
import kr.wisead.mapper.primary.EventActionTypeMapper;
import kr.wisead.mapper.primary.EventNametagLogMapper;
import kr.wisead.mapper.primary.EventParticipantMapper;
import kr.wisead.mapper.primary.SmsSendMapper;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import kr.wisead.mapper.primary.SurveyUserMapper;
import kr.wisead.mapper.sms.MsgQueueMapper;
import kr.wisead.mapper.sms.SendHistoryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventParticipantServiceSecurityTest {

  private static final Integer EVENT_SEQ = 100;
  private static final Integer OTHER_EVENT_SEQ = 200;
  private static final Long PARTICIPANT_SEQ = 10L;
  private static final String OWNER_ID = "owner";
  private static final String OTHER_USER_ID = "other";
  private static final String CHECK_CODE = "ABCDE";
  private static final String CLIENT_IP = "127.0.0.1";

  @Mock private EventParticipantMapper participantMapper;
  @Mock private EventActionTypeMapper actionTypeMapper;
  @Mock private EventActionLogMapper actionLogMapper;
  @Mock private EventNametagLogMapper nametagLogMapper;
  @Mock private SurveyUserMapper surveyUserMapper;
  @Mock private SurveyMasterMapper surveyMasterMapper;
  @Mock private SmsSendMapper smsSendMapper;
  @Mock private MsgQueueMapper msgQueueMapper;
  @Mock private SendHistoryMapper sendHistoryMapper;
  @Mock private AdminService adminService;
  @Mock private ExcelService excelService;
  @Mock private RsvpNonceStore rsvpNonceStore;
  @Mock private SimpleRateLimiter simpleRateLimiter;
  @Spy private FailureRateLimiter failureRateLimiter = new FailureRateLimiter();

  @InjectMocks private EventParticipantService service;

  @Test
  @DisplayName("비소유자 참가자 목록 read는 ACCESS_DENIED를 전파한다")
  void getParticipants_deniesNonOwner() {
    givenEventOwner();
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "접근 권한이 없습니다."))
        .when(adminService)
        .validateModifyPermission(OTHER_USER_ID, 1, OWNER_ID);

    ParticipantSearchRequest request =
        ParticipantSearchRequest.builder().eventSeq(EVENT_SEQ).page(1).size(20).build();

    assertThatThrownBy(() -> service.getParticipants(request, OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);
  }

  @Test
  @DisplayName("문자 발송용 read도 행사 소유권을 검증한다")
  void getParticipantsForMessage_validatesReadAccess() {
    givenEventOwner();
    when(participantMapper.selectByEventSeq(EVENT_SEQ)).thenReturn(List.of());
    when(smsSendMapper.selectSentUserSeqs(EVENT_SEQ)).thenReturn(List.of());

    service.getParticipantsForMessage(EVENT_SEQ, OWNER_ID);

    verify(adminService).validateModifyPermission(OWNER_ID, 1, OWNER_ID);
  }

  @Test
  @DisplayName("통계 read도 비소유자 접근을 차단한다")
  void getStatistics_deniesNonOwner() {
    givenEventOwner();
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "접근 권한이 없습니다."))
        .when(adminService)
        .validateModifyPermission(OTHER_USER_ID, 1, OWNER_ID);

    assertThatThrownBy(() -> service.getStatistics(EVENT_SEQ, OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);
  }

  @Test
  @DisplayName("seq 기반 상세 read에서 path eventSeq와 참가자 eventSeq가 다르면 RESOURCE_NOT_FOUND")
  void getParticipant_rejectsEventSeqMismatch() {
    givenEventOwner();
    when(participantMapper.selectDetailBySeq(PARTICIPANT_SEQ))
        .thenReturn(Optional.of(participant(OTHER_EVENT_SEQ)));

    assertThatThrownBy(() -> service.getParticipant(EVENT_SEQ, PARTICIPANT_SEQ, OWNER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  @DisplayName("seq 기반 상세 read 비소유자는 참가자가 해당 이벤트 소속이어도 ACCESS_DENIED")
  void getParticipant_deniesNonOwnerForMatchingParticipant() {
    givenEventOwner();
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "접근 권한이 없습니다."))
        .when(adminService)
        .validateModifyPermission(OTHER_USER_ID, 1, OWNER_ID);

    assertThatThrownBy(() -> service.getParticipant(EVENT_SEQ, PARTICIPANT_SEQ, OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);
    verify(participantMapper, never()).selectDetailBySeq(any());
  }

  @Test
  @DisplayName("seq 기반 상세 read 비소유자는 참가자 eventSeq 일치 여부와 무관하게 ACCESS_DENIED")
  void getParticipant_deniesNonOwnerBeforeParticipantEventValidation() {
    givenEventOwner();
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "접근 권한이 없습니다."))
        .when(adminService)
        .validateModifyPermission(OTHER_USER_ID, 1, OWNER_ID);

    assertThatThrownBy(() -> service.getParticipant(EVENT_SEQ, PARTICIPANT_SEQ, OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);
    verify(participantMapper, never()).selectDetailBySeq(any());
  }

  @Test
  @DisplayName("seq 기반 상세 read 소유자는 참가자 eventSeq가 일치하면 정상 조회한다")
  void getParticipant_ownerCanReadMatchingParticipant() {
    givenEventOwner();
    when(participantMapper.selectDetailBySeq(PARTICIPANT_SEQ))
        .thenReturn(Optional.of(participant(EVENT_SEQ)));

    assertThat(service.getParticipant(EVENT_SEQ, PARTICIPANT_SEQ, OWNER_ID).getSeq())
        .isEqualTo(PARTICIPANT_SEQ);
  }

  @Test
  @DisplayName("seq 기반 status read도 행사 소유권을 검증한다")
  void getParticipantStatus_validatesReadAccess() {
    givenEventOwner();
    when(participantMapper.selectDetailBySeq(PARTICIPANT_SEQ))
        .thenReturn(Optional.of(participant(EVENT_SEQ)));
    when(actionLogMapper.selectActionStatusByParticipantSeq(PARTICIPANT_SEQ, EVENT_SEQ))
        .thenReturn(List.of());

    service.getParticipantStatus(EVENT_SEQ, PARTICIPANT_SEQ, OWNER_ID);

    verify(adminService).validateModifyPermission(OWNER_ID, 1, OWNER_ID);
  }

  @Test
  @DisplayName("seq 기반 status read에서 path eventSeq와 참가자 eventSeq가 다르면 RESOURCE_NOT_FOUND")
  void getParticipantStatus_rejectsEventSeqMismatch() {
    givenEventOwner();
    when(participantMapper.selectDetailBySeq(PARTICIPANT_SEQ))
        .thenReturn(Optional.of(participant(OTHER_EVENT_SEQ)));

    assertThatThrownBy(() -> service.getParticipantStatus(EVENT_SEQ, PARTICIPANT_SEQ, OWNER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  @DisplayName("seq 기반 status read 비소유자는 참가자가 해당 이벤트 소속이어도 ACCESS_DENIED")
  void getParticipantStatus_deniesNonOwnerForMatchingParticipant() {
    givenEventOwner();
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "접근 권한이 없습니다."))
        .when(adminService)
        .validateModifyPermission(OTHER_USER_ID, 1, OWNER_ID);

    assertThatThrownBy(() -> service.getParticipantStatus(EVENT_SEQ, PARTICIPANT_SEQ, OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);
    verify(participantMapper, never()).selectDetailBySeq(any());
  }

  @Test
  @DisplayName("seq 기반 status read 비소유자는 참가자 eventSeq 일치 여부와 무관하게 ACCESS_DENIED")
  void getParticipantStatus_deniesNonOwnerBeforeParticipantEventValidation() {
    givenEventOwner();
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "접근 권한이 없습니다."))
        .when(adminService)
        .validateModifyPermission(OTHER_USER_ID, 1, OWNER_ID);

    assertThatThrownBy(() -> service.getParticipantStatus(EVENT_SEQ, PARTICIPANT_SEQ, OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);
    verify(participantMapper, never()).selectDetailBySeq(any());
  }

  @Test
  @DisplayName("공개 check GET status 응답은 phone/email을 제거한다")
  void publicStatusByCheckCode_removesContactFields() throws Exception {
    when(participantMapper.selectByEventSeqAndCheckCode(EVENT_SEQ, CHECK_CODE))
        .thenReturn(Optional.of(participant(EVENT_SEQ)));
    when(actionLogMapper.selectActionStatusByParticipantSeq(PARTICIPANT_SEQ, EVENT_SEQ))
        .thenReturn(List.of());

    ParticipantStatusResponse response =
        service.getParticipantStatusByCheckCode(EVENT_SEQ, CHECK_CODE, false, CLIENT_IP);

    assertThat(response.getParticipant().getPhone()).isNull();
    assertThat(response.getParticipant().getEmail()).isNull();
    String json = new ObjectMapper().writeValueAsString(response);
    assertThat(json).doesNotContain("phone").doesNotContain("email");
  }

  @Test
  @DisplayName("스태프 check GET status 응답은 기존처럼 phone/email을 유지한다")
  void staffStatusByCheckCode_keepsContactFields() {
    when(participantMapper.selectByEventSeqAndCheckCode(EVENT_SEQ, CHECK_CODE))
        .thenReturn(Optional.of(participant(EVENT_SEQ)));
    when(actionLogMapper.selectActionStatusByParticipantSeq(PARTICIPANT_SEQ, EVENT_SEQ))
        .thenReturn(List.of());

    ParticipantStatusResponse response =
        service.getParticipantStatusByCheckCode(EVENT_SEQ, CHECK_CODE, true, null);

    assertThat(response.getParticipant().getPhone()).isEqualTo("01012345678");
    assertThat(response.getParticipant().getEmail()).isEqualTo("user@example.com");
  }

  @Test
  @DisplayName("공개 check GET 유효 조회는 rate limit 카운터를 쓰지 않고 반복 허용된다")
  void publicStatusByCheckCode_validLookupIsNotRateLimited() {
    when(participantMapper.selectByEventSeqAndCheckCode(EVENT_SEQ, CHECK_CODE))
        .thenReturn(Optional.of(participant(EVENT_SEQ)));
    when(actionLogMapper.selectActionStatusByParticipantSeq(PARTICIPANT_SEQ, EVENT_SEQ))
        .thenReturn(List.of());

    for (int i = 0; i < 20; i++) {
      service.getParticipantStatusByCheckCode(EVENT_SEQ, CHECK_CODE, false, CLIENT_IP);
    }
  }

  @Test
  @DisplayName("공개 check GET 조회 실패는 IP+eventSeq 기준 60초 10회 초과 시 rate limit")
  void publicStatusByCheckCode_failedLookupIsRateLimited() {
    when(participantMapper.selectByEventSeqAndCheckCode(EVENT_SEQ, "NOPE"))
        .thenReturn(Optional.empty());

    for (int i = 0; i < 10; i++) {
      assertThatThrownBy(
              () -> service.getParticipantStatusByCheckCode(EVENT_SEQ, "NOPE", false, CLIENT_IP))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    assertThatThrownBy(
            () -> service.getParticipantStatusByCheckCode(EVENT_SEQ, "NOPE", false, CLIENT_IP))
        .isInstanceOf(RateLimitExceededException.class);
  }

  private void givenEventOwner() {
    when(surveyMasterMapper.selectByEventSeq(EVENT_SEQ))
        .thenReturn(Optional.of(SurveyMaster.builder().eventSeq(EVENT_SEQ).regId(OWNER_ID).build()));
    when(adminService.getUserLevel(any())).thenReturn(1);
  }

  private EventParticipant participant(Integer eventSeq) {
    return EventParticipant.builder()
        .seq(PARTICIPANT_SEQ)
        .eventSeq(eventSeq)
        .surveyUserSeq(1)
        .checkCode(CHECK_CODE)
        .userName("홍길동")
        .userPhone(CryptoUtils.encodeBase64(CryptoUtils.encryptAES256("01012345678")))
        .userEmail("user@example.com")
        .department("개발팀")
        .position("매니저")
        .participantType("일반")
        .nametagPrinted("N")
        .build();
  }
}
