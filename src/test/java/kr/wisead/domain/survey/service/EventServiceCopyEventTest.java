package kr.wisead.domain.survey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.event.entity.EventActionType;
import kr.wisead.domain.event.entity.EventParticipant;
import kr.wisead.domain.event.service.EventActionTypeService;
import kr.wisead.domain.excel.service.ExcelService;
import kr.wisead.domain.file.service.FileStorageService;
import kr.wisead.domain.payment.service.BillingService;
import kr.wisead.domain.survey.dto.EventResponse;
import kr.wisead.domain.survey.entity.SurveyMaster;
import kr.wisead.domain.survey.entity.SurveyUser;
import kr.wisead.mapper.primary.AuthUserMappingMapper;
import kr.wisead.mapper.primary.EventActionTypeMapper;
import kr.wisead.mapper.primary.EventParticipantMapper;
import kr.wisead.mapper.primary.SurveyAnswerMapper;
import kr.wisead.mapper.primary.SurveyItemMapper;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import kr.wisead.mapper.primary.SurveyQuestionMapper;
import kr.wisead.mapper.primary.SurveyUserMapper;
import kr.wisead.mapper.primary.UserMapper;
import kr.wisead.common.util.UserIdResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EventServiceCopyEventTest {

  @Mock private SurveyMasterMapper surveyMasterMapper;
  @Mock private SurveyQuestionMapper surveyQuestionMapper;
  @Mock private SurveyItemMapper surveyItemMapper;
  @Mock private SurveyUserMapper surveyUserMapper;
  @Mock private SurveyAnswerMapper surveyAnswerMapper;
  @Mock private AuthUserMappingMapper authUserMappingMapper;
  @Mock private UserMapper userMapper;
  @Mock private EventParticipantMapper eventParticipantMapper;
  @Mock private EventActionTypeMapper eventActionTypeMapper;
  @Mock private ExcelService excelService;
  @Mock private AdminService adminService;
  @Mock private FileStorageService fileStorageService;
  @Mock private BillingService billingService;
  @Mock private EventActionTypeService eventActionTypeService;
  @Mock private UserIdResolver userIdResolver;

  @InjectMocks private EventService eventService;

  @Test
  void copyEvent_copiesMasterParticipantsAndActionTypesWithExpectedResets() {
    Integer sourceEventSeq = 100;
    Integer copiedEventSeq = 200;

    SurveyMaster sourceEvent =
        SurveyMaster.builder()
            .eventSeq(sourceEventSeq)
            .userSeq(1)
            .eventCode("SRCODE123456")
            .eventName("원본 행사")
            .eventEmphasisYn("Y")
            .eventDesc("행사 설명")
            .eventDescImg("/survey/100/Desc.png")
            .eventEndImg("/survey/100/End.png")
            .eventType("E")
            .startDate("2026-03-11 09:00:00")
            .endDate("2026-03-11 18:00:00")
            .status("P")
            .privacyPolicyYn("N")
            .auth("NA")
            .qrCode("Y")
            .qrCodeImgPath("/qrcode/original.png")
            .authCodeUrl("AUTHCODE")
            .endMessage("종료 문구")
            .venue("서울")
            .organizer("Syscuss")
            .badgePrintType("N")
            .nametagConfig("{\"paperSize\":\"80x80\"}")
            .authKeyDesc("설명 문구")
            .regId("owner")
            .build();

    SurveyUser sourceUser =
        SurveyUser.builder()
            .seq(11)
            .eventSeq(sourceEventSeq)
            .userKey("user-key-1")
            .userName("홍길동")
            .userPhone("ENC_PHONE")
            .resendUserPhone("ENC_PHONE")
            .userEmail("test@example.com")
            .regId("owner")
            .build();

    EventParticipant sourceParticipant =
        EventParticipant.builder()
            .seq(21L)
            .surveyUserSeq(11)
            .eventSeq(sourceEventSeq)
            .checkCode("ABCDE")
            .department("개발팀")
            .position("매니저")
            .participantType("VIP")
            .memo("메모")
            .nametagPrinted("Y")
            .attendTime("09:30")
            .registType("사전등록")
            .build();

    List<EventActionType> sourceActionTypes =
        List.of(
            EventActionType.builder()
                .seq(1L)
                .eventSeq(sourceEventSeq)
                .actionCode("CHECK_IN")
                .actionName("입장")
                .requireAdminAuth("N")
                .allowMultiple("N")
                .sortOrder(1)
                .useYn("Y")
                .build(),
            EventActionType.builder()
                .seq(2L)
                .eventSeq(sourceEventSeq)
                .actionCode("GIFT")
                .actionName("기념품")
                .requireAdminAuth("N")
                .allowMultiple("N")
                .sortOrder(2)
                .useYn("Y")
                .build());

    when(surveyMasterMapper.selectByEventSeq(sourceEventSeq)).thenReturn(Optional.of(sourceEvent));
    when(adminService.getUserLevel("jwt-user")).thenReturn(10);
    doNothing().when(adminService).validateModifyPermission("jwt-user", 10, "owner");
    when(userIdResolver.resolveUserId("jwt-user")).thenReturn("copier");
    when(userIdResolver.fromJwtUsername("jwt-user")).thenReturn(77);

    when(eventActionTypeMapper.selectByEventSeq(sourceEventSeq)).thenReturn(sourceActionTypes);
    when(surveyUserMapper.selectByEventSeq(sourceEventSeq)).thenReturn(List.of(sourceUser));
    when(eventParticipantMapper.selectByEventSeq(sourceEventSeq)).thenReturn(List.of(sourceParticipant));
    when(eventParticipantMapper.existsByEventSeqAndCheckCode(eq(copiedEventSeq), any())).thenReturn(false);

    when(surveyQuestionMapper.selectByEventSeq(copiedEventSeq)).thenReturn(List.of());
    when(surveyItemMapper.selectByEventSeq(copiedEventSeq)).thenReturn(List.of());

    ArgumentCaptor<SurveyMaster> insertedEventCaptor = ArgumentCaptor.forClass(SurveyMaster.class);
    when(surveyMasterMapper.insert(insertedEventCaptor.capture()))
        .thenAnswer(
            invocation -> {
              SurveyMaster inserted = invocation.getArgument(0);
              ReflectionTestUtils.setField(inserted, "eventSeq", copiedEventSeq);
              when(surveyMasterMapper.selectByEventSeq(copiedEventSeq)).thenReturn(Optional.of(inserted));
              return 1;
            });

    when(surveyUserMapper.insertForParticipant(any(SurveyUser.class)))
        .thenAnswer(
            invocation -> {
              SurveyUser inserted = invocation.getArgument(0);
              ReflectionTestUtils.setField(inserted, "seq", 301);
              return 1;
            });

    EventResponse response = eventService.copyEvent(sourceEventSeq, "jwt-user");

    SurveyMaster insertedEvent = insertedEventCaptor.getValue();
    assertThat(response.getEventSeq()).isEqualTo(copiedEventSeq);
    assertThat(insertedEvent.getStatus()).isEqualTo("A");
    assertThat(insertedEvent.getBadgePrintType()).isNull();
    assertThat(insertedEvent.getNametagConfig()).isNull();
    assertThat(insertedEvent.getQrCode()).isEqualTo("N");
    assertThat(insertedEvent.getQrCodeImgPath()).isNull();
    assertThat(insertedEvent.getAuthCodeUrl()).isNull();
    assertThat(insertedEvent.getEventCode()).isNotBlank().isNotEqualTo(sourceEvent.getEventCode());
    assertThat(insertedEvent.getRegId()).isEqualTo("copier");

    verify(surveyMasterMapper).updateAuthKeyDesc(copiedEventSeq, "설명 문구");

    ArgumentCaptor<List<EventActionType>> actionTypesCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventActionTypeMapper).insertBatch(actionTypesCaptor.capture());
    assertThat(actionTypesCaptor.getValue())
        .extracting(EventActionType::getEventSeq, EventActionType::getActionCode)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(copiedEventSeq, "CHECK_IN"),
            org.assertj.core.groups.Tuple.tuple(copiedEventSeq, "GIFT"));

    ArgumentCaptor<List<EventParticipant>> participantsCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventParticipantMapper).insertBatch(participantsCaptor.capture());
    EventParticipant copiedParticipant = participantsCaptor.getValue().get(0);
    assertThat(copiedParticipant.getEventSeq()).isEqualTo(copiedEventSeq);
    assertThat(copiedParticipant.getSurveyUserSeq()).isEqualTo(301);
    assertThat(copiedParticipant.getDepartment()).isEqualTo("개발팀");
    assertThat(copiedParticipant.getPosition()).isEqualTo("매니저");
    assertThat(copiedParticipant.getParticipantType()).isEqualTo("VIP");
    assertThat(copiedParticipant.getMemo()).isEqualTo("메모");
    assertThat(copiedParticipant.getRegistType()).isEqualTo("사전등록");
    assertThat(copiedParticipant.getNametagPrinted()).isEqualTo("N");
    assertThat(copiedParticipant.getAttendTime()).isNull();
    assertThat(copiedParticipant.getCheckCode()).isNotBlank().isNotEqualTo("ABCDE");
  }
}
