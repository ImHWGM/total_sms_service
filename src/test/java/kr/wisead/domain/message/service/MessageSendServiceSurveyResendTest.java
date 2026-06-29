package kr.wisead.domain.message.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.ars.service.BlockedNumberService;
import kr.wisead.domain.event.service.EventParticipantService;
import kr.wisead.domain.message.dto.ResendRequest;
import kr.wisead.domain.message.dto.ResendResponse;
import kr.wisead.domain.message.dto.SurveyMessageRequest;
import kr.wisead.domain.message.dto.SurveyMessageResponse;
import kr.wisead.domain.message.entity.MsgQueue;
import kr.wisead.domain.payment.service.WalletService;
import kr.wisead.domain.survey.entity.SurveyMaster;
import kr.wisead.domain.survey.entity.SurveyUser;
import kr.wisead.domain.survey.entity.SurveyUserRepChar;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.SmsSendMapper;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import kr.wisead.mapper.primary.SurveyUserMapper;
import kr.wisead.mapper.primary.SurveyUserRepCharMapper;
import kr.wisead.mapper.primary.UserMapper;
import kr.wisead.mapper.sms.MsgQueueMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * resendToDuplicates() 서비스 경로 검증 (리뷰 MEDIUM 대응).
 *
 * <p>보호 대상 동작:
 *
 * <ul>
 *   <li>기존 userSeq에 설문 치환문자가 저장되는지 (SURVEY_USER_REP_CHAR)
 *   <li>#설문대치N# 토큰이 실제 큐 메시지에 치환되는지
 *   <li>잘못된 eventSeq–userSeq 조합이 거부되어 저장·발송되지 않는지 (HIGH IDOR)
 *   <li>이벤트 권한 없는 호출자가 거부되는지 (HIGH IDOR)
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class MessageSendServiceSurveyResendTest {

  @Mock private MsgQueueMapper msgQueueMapper;
  @Mock private SurveyUserMapper surveyUserMapper;
  @Mock private SurveyUserRepCharMapper surveyUserRepCharMapper;
  @Mock private SurveyMasterMapper surveyMasterMapper;
  @Mock private SmsSendMapper smsSendMapper;
  @Mock private WalletService walletService;
  @Mock private UserIdResolver userIdResolver;
  @Mock private EventParticipantService eventParticipantService;
  @Mock private BlockedNumberService blockedNumberService;
  @Mock private UserMapper userMapper;
  @Mock private AdminService adminService;

  @InjectMocks private MessageSendService service;

  private static final String REG_ID = "owner";
  private static final Integer EVENT_SEQ = 754;
  private static final Integer USER_SEQ = 100;

  private ResendRequest buildRequest(Integer userSeq, Integer eventSeq, String s1, String s2) {
    ResendRequest.DuplicateReceiver receiver =
        ResendRequest.DuplicateReceiver.builder()
            .phone("01011112222")
            .userSeq(userSeq)
            .userKey("userkey123")
            .surveyRepChar01(s1)
            .surveyRepChar02(s2)
            .build();
    return ResendRequest.builder()
        .eventSeq(eventSeq)
        .eventCode("EVT")
        .callback("01000000000")
        .subject("제목")
        .text("문항: #설문대치1# / #설문대치2#")
        .duplicateReceivers(List.of(receiver))
        .build();
  }

  /** 호출자가 이벤트 소유자인 경우 (validateEventSendPermission 소유자 단락 통과). */
  private void stubEventOwnedByCaller() {
    when(surveyMasterMapper.selectByEventSeq(EVENT_SEQ))
        .thenReturn(
            Optional.of(SurveyMaster.builder().eventSeq(EVENT_SEQ).eventCode("EVT").regId(REG_ID).build()));
  }

  private void stubDeduction() {
    when(userIdResolver.toUserSeq(REG_ID)).thenReturn(1);
    when(walletService.getAppliedRate(1, "survey")).thenReturn(new BigDecimal("99"));
    when(walletService.hasEnoughBalance(eq(1), any())).thenReturn(true);
    when(walletService.deductWithPriority(eq(1), eq("survey"), any(), anyString(), eq(REG_ID)))
        .thenReturn("tx1");
  }

  @Test
  void resend_validReceiver_persistsRepChars_andSubstitutesTokens() {
    ReflectionTestUtils.setField(service, "wiseadUrl", "https://wisead.kr");
    stubEventOwnedByCaller();
    when(surveyUserMapper.selectBySeq(USER_SEQ))
        .thenReturn(
            Optional.of(
                SurveyUser.builder()
                    .seq(USER_SEQ)
                    .eventSeq(EVENT_SEQ)
                    .userKey("userkey123")
                    .delYn("N")
                    .build()));
    stubDeduction();

    ResendResponse response =
        service.resendToDuplicates(buildRequest(USER_SEQ, EVENT_SEQ, "테스트", "asdidsaf"), REG_ID);

    assertThat(response.getSuccessCount()).isEqualTo(1);
    assertThat(response.getFailCount()).isZero();

    // 1) 치환문자 영속화 검증
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<SurveyUserRepChar>> rowsCaptor = ArgumentCaptor.forClass(List.class);
    verify(surveyUserRepCharMapper).insertIgnoreBatch(rowsCaptor.capture());
    List<SurveyUserRepChar> rows = rowsCaptor.getValue();
    assertThat(rows).extracting(SurveyUserRepChar::getUserSeq).containsOnly(USER_SEQ);
    assertThat(rows).extracting(SurveyUserRepChar::getRepCharVal).containsExactly("테스트", "asdidsaf");

    // 2) 큐 메시지 토큰 치환 검증
    ArgumentCaptor<MsgQueue> queueCaptor = ArgumentCaptor.forClass(MsgQueue.class);
    verify(msgQueueMapper).insertLms(queueCaptor.capture());
    String queuedText = queueCaptor.getValue().getText();
    assertThat(queuedText).contains("테스트").contains("asdidsaf");
    assertThat(queuedText).doesNotContain("#설문대치1#").doesNotContain("#설문대치2#");
  }

  @Test
  void resend_mismatchedEventSeq_rejected_noPersistNoQueue() {
    stubEventOwnedByCaller();
    // userSeq는 다른 이벤트(999)에 속함 → 거부되어야 함
    when(surveyUserMapper.selectBySeq(USER_SEQ))
        .thenReturn(
            Optional.of(
                SurveyUser.builder().seq(USER_SEQ).eventSeq(999).delYn("N").build()));

    ResendResponse response =
        service.resendToDuplicates(buildRequest(USER_SEQ, EVENT_SEQ, "테스트", null), REG_ID);

    assertThat(response.getResultCode()).isEqualTo(-1);
    verify(surveyUserRepCharMapper, never()).insertIgnoreBatch(any());
    verify(msgQueueMapper, never()).insertLms(any());
    verify(walletService, never()).deductWithPriority(any(), any(), any(), any(), any());
  }

  @Test
  void resend_callerWithoutEventPermission_throwsAccessDenied() {
    when(surveyMasterMapper.selectByEventSeq(EVENT_SEQ))
        .thenReturn(
            Optional.of(
                SurveyMaster.builder().eventSeq(EVENT_SEQ).eventCode("EVT").regId("other").build()));
    when(userMapper.findByUserId(REG_ID))
        .thenReturn(Optional.of(User.builder().userLevel(10).build()));
    when(adminService.canModify(eq(REG_ID), eq(10), eq("other"))).thenReturn(false);

    assertThatThrownBy(
            () ->
                service.resendToDuplicates(
                    buildRequest(USER_SEQ, EVENT_SEQ, "테스트", null), REG_ID))
        .isInstanceOf(BusinessException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

    verify(surveyUserRepCharMapper, never()).insertIgnoreBatch(any());
    verify(msgQueueMapper, never()).insertLms(any());
    verify(walletService, never()).deductWithPriority(any(), any(), any(), any(), any());
  }
  @Test
  void resend_nonOwnerAdminWithCanModify_succeeds() {
    ReflectionTestUtils.setField(service, "wiseadUrl", "https://wisead.kr");
    // 호출자는 소유자가 아니나 관리 권한(canModify=true) 보유
    when(surveyMasterMapper.selectByEventSeq(EVENT_SEQ))
        .thenReturn(
            Optional.of(
                SurveyMaster.builder().eventSeq(EVENT_SEQ).eventCode("EVT").regId("other").build()));
    when(userMapper.findByUserId(REG_ID))
        .thenReturn(Optional.of(User.builder().userLevel(60).build()));
    when(adminService.canModify(eq(REG_ID), eq(60), eq("other"))).thenReturn(true);
    when(surveyUserMapper.selectBySeq(USER_SEQ))
        .thenReturn(
            Optional.of(
                SurveyUser.builder()
                    .seq(USER_SEQ)
                    .eventSeq(EVENT_SEQ)
                    .userKey("userkey123")
                    .delYn("N")
                    .build()));
    stubDeduction();

    ResendResponse response =
        service.resendToDuplicates(buildRequest(USER_SEQ, EVENT_SEQ, "테스트", null), REG_ID);

    assertThat(response.getSuccessCount()).isEqualTo(1);
    verify(surveyUserRepCharMapper).insertIgnoreBatch(any());
    verify(msgQueueMapper).insertLms(any());
  }
  @Test
  void directSend_clientUserSeqFromOtherEvent_rejected_noPersistNoQueueNoCharge() {
    // 직접 발송(sendSurveyMessages)에 클라이언트가 타 이벤트(999) userSeq를 넣은 경우 → 거부 (IDOR 차단)
    when(surveyUserMapper.selectBySeq(USER_SEQ))
        .thenReturn(
            Optional.of(
                SurveyUser.builder().seq(USER_SEQ).eventSeq(999).delYn("N").build()));

    SurveyMessageRequest.Receiver receiver =
        SurveyMessageRequest.Receiver.builder()
            .phone("01011112222")
            .userSeq(USER_SEQ)
            .surveyRepChar01("테스트")
            .build();
    SurveyMessageRequest request =
        SurveyMessageRequest.builder()
            .eventSeq(EVENT_SEQ)
            .callback("01000000000")
            .subject("제목")
            .text("문항: #설문대치1#")
            .receivers(List.of(receiver))
            .build();

    SurveyMessageResponse response = service.sendSurveyMessages(request, REG_ID);

    assertThat(response.isSuccess()).isFalse();
    verify(surveyUserRepCharMapper, never()).insertIgnoreBatch(any());
    verify(msgQueueMapper, never()).insertLms(any());
    verify(walletService, never()).deductWithPriority(any(), any(), any(), any(), any());
  }
}
