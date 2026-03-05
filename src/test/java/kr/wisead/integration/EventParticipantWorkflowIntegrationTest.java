package kr.wisead.integration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.event.dto.*;
import kr.wisead.domain.event.service.*;
import kr.wisead.domain.survey.dto.EventRequest;
import kr.wisead.domain.survey.dto.EventResponse;
import kr.wisead.domain.survey.service.EventService;
import kr.wisead.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 행사 참가자 워크플로우 통합 테스트
 *
 * <p>테스트 시나리오: 1. 관리자가 행사를 생성한다 2. 액션 유형을 설정한다 (입장, 경품수령, 기념품수령) 3. 참가자를 등록한다 4. 참가자가 QR 스캔으로 체크인한다
 * (인증 불필요) 5. 관리자가 경품/기념품 수령 처리를 한다 (관리자 비밀번호 필요) 6. 명찰을 출력한다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("행사 참가자 워크플로우 통합 테스트")
class EventParticipantWorkflowIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JwtTokenProvider jwtTokenProvider;

  @MockitoBean private EventService eventService;

  @MockitoBean private EventParticipantService participantService;

  @MockitoBean private EventCheckService checkService;

  @MockitoBean private EventActionTypeService actionTypeService;

  @MockitoBean private NametagService nametagService;

  private static final String TEST_USER_ID = "testuser01";
  private static final String TEST_ADMIN_ID = "admin";
  private static final Integer TEST_EVENT_SEQ = 100;
  private static final String TEST_EVENT_CODE = "EVT123ABC456";
  private static final Long TEST_PARTICIPANT_SEQ = 1L;
  private static final String TEST_CHECK_CODE = "abc123def456ghi789";
  private static final Long TEST_CHECKIN_ACTION_SEQ = 1L;
  private static final Long TEST_PRIZE_ACTION_SEQ = 2L;
  private static final Long TEST_GIFT_ACTION_SEQ = 3L;

  private String userToken;
  private String adminToken;

  @BeforeEach
  void setUp() {
    // 일반 사용자 토큰 생성
    var userAuth =
        new UsernamePasswordAuthenticationToken(
            TEST_USER_ID, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    userToken = jwtTokenProvider.createAccessToken(userAuth, "테스트사용자");

    // 관리자 토큰 생성
    var adminAuth =
        new UsernamePasswordAuthenticationToken(
            TEST_ADMIN_ID,
            null,
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
    adminToken = jwtTokenProvider.createAccessToken(adminAuth, "관리자");
  }

  @Test
  @Order(1)
  @DisplayName("1. 행사 생성 (인증 필요)")
  void createEvent_Success() throws Exception {
    // Given: 행사 생성 요청
    EventRequest request =
        EventRequest.builder()
            .eventName("2025 신년 세미나")
            .eventType("S")
            .eventDesc("신년 맞이 기술 세미나입니다.")
            .startDate("2025-01-15")
            .endDate("2025-01-15")
            .status("A")
            .auth("NA")
            .build();

    // Mock: 행사 생성 응답
    EventResponse mockResponse =
        EventResponse.builder()
            .eventSeq(TEST_EVENT_SEQ)
            .eventCode(TEST_EVENT_CODE)
            .eventName("2025 신년 세미나")
            .eventType("S")
            .status("A")
            .build();

    when(eventService.createEvent(
            anyString(), any(EventRequest.class), any(), any(), anyList(), anyList()))
        .thenReturn(mockResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/event")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.eventSeq").value(TEST_EVENT_SEQ));

    verify(eventService, times(1))
        .createEvent(anyString(), any(EventRequest.class), any(), any(), anyList(), anyList());
  }

  @Test
  @Order(2)
  @DisplayName("2. 액션 유형 목록 조회 (인증 필요)")
  void getActionTypes_Success() throws Exception {
    // Given: 기본 액션 유형 목록
    List<EventActionTypeResponse> actionTypes =
        List.of(
            EventActionTypeResponse.builder()
                .seq(TEST_CHECKIN_ACTION_SEQ)
                .eventSeq(TEST_EVENT_SEQ)
                .actionCode("CHECK_IN")
                .actionName("입장")
                .requireAdminAuth("N")
                .allowMultiple("N")
                .sortOrder(1)
                .useYn("Y")
                .build());

    when(actionTypeService.getActionTypes(TEST_EVENT_SEQ)).thenReturn(actionTypes);

    // When & Then
    mockMvc
        .perform(
            get("/api/events/{eventSeq}/action-types", TEST_EVENT_SEQ)
                .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data[0].actionCode").value("CHECK_IN"));
  }

  @Test
  @Order(3)
  @DisplayName("3. 경품 수령 액션 유형 추가 (인증 필요)")
  void createPrizeActionType_Success() throws Exception {
    // Given: 경품 수령 액션 유형 추가 요청
    EventActionTypeRequest request =
        EventActionTypeRequest.builder()
            .actionCode("PRIZE")
            .actionName("경품 수령")
            .requireAdminAuth("Y")
            .allowMultiple("N")
            .sortOrder(2)
            .useYn("Y")
            .build();

    EventActionTypeResponse mockResponse =
        EventActionTypeResponse.builder()
            .seq(TEST_PRIZE_ACTION_SEQ)
            .eventSeq(TEST_EVENT_SEQ)
            .actionCode("PRIZE")
            .actionName("경품 수령")
            .requireAdminAuth("Y")
            .allowMultiple("N")
            .sortOrder(2)
            .useYn("Y")
            .regDate(LocalDateTime.now())
            .build();

    when(actionTypeService.createActionType(any(EventActionTypeRequest.class)))
        .thenReturn(mockResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/events/{eventSeq}/action-types", TEST_EVENT_SEQ)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.actionCode").value("PRIZE"))
        .andExpect(jsonPath("$.data.requireAdminAuth").value("Y"));
  }

  @Test
  @Order(4)
  @DisplayName("4. 기념품 수령 액션 유형 추가 (인증 필요)")
  void createGiftActionType_Success() throws Exception {
    // Given: 기념품 수령 액션 유형 추가 요청
    EventActionTypeRequest request =
        EventActionTypeRequest.builder()
            .actionCode("GIFT")
            .actionName("기념품 수령")
            .requireAdminAuth("Y")
            .allowMultiple("N")
            .sortOrder(3)
            .useYn("Y")
            .build();

    EventActionTypeResponse mockResponse =
        EventActionTypeResponse.builder()
            .seq(TEST_GIFT_ACTION_SEQ)
            .eventSeq(TEST_EVENT_SEQ)
            .actionCode("GIFT")
            .actionName("기념품 수령")
            .requireAdminAuth("Y")
            .allowMultiple("N")
            .sortOrder(3)
            .useYn("Y")
            .regDate(LocalDateTime.now())
            .build();

    when(actionTypeService.createActionType(any(EventActionTypeRequest.class)))
        .thenReturn(mockResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/events/{eventSeq}/action-types", TEST_EVENT_SEQ)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.actionCode").value("GIFT"));
  }

  @Test
  @Order(5)
  @DisplayName("5. 참가자 등록 (인증 필요)")
  void createParticipant_Success() throws Exception {
    // Given: 참가자 등록 요청
    EventParticipantRequest request =
        EventParticipantRequest.builder()
            .userName("홍길동")
            .userPhone("010-2345-6789")
            .userEmail("hong@test.com")
            .department("개발팀")
            .position("과장")
            .participantType("일반")
            .memo("VIP 대우 필요")
            .build();

    EventParticipantResponse mockResponse =
        EventParticipantResponse.builder()
            .seq(TEST_PARTICIPANT_SEQ)
            .surveyUserSeq(1)
            .eventSeq(TEST_EVENT_SEQ)
            .checkCode(TEST_CHECK_CODE)
            .userName("홍길동")
            .userPhone("01023456789")
            .userEmail("hong@test.com")
            .department("개발팀")
            .position("과장")
            .participantType("일반")
            .nametagPrinted("N")
            .qrCodeUrl(
                "http://localhost:8080/event/" + TEST_EVENT_SEQ + "/check/" + TEST_CHECK_CODE)
            .regDate(LocalDateTime.now())
            .build();

    when(participantService.createParticipant(any(EventParticipantRequest.class), anyString()))
        .thenReturn(mockResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/events/{eventSeq}/participants", TEST_EVENT_SEQ)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.userName").value("홍길동"))
        .andExpect(jsonPath("$.data.checkCode").value(TEST_CHECK_CODE))
        .andExpect(jsonPath("$.data.qrCodeUrl").exists());

    verify(participantService, times(1))
        .createParticipant(any(EventParticipantRequest.class), anyString());
  }

  @Test
  @Order(6)
  @DisplayName("6. 참가자 목록 조회 (인증 필요)")
  void getParticipants_Success() throws Exception {
    // Given: 참가자 목록
    List<EventParticipantResponse> participants =
        List.of(
            EventParticipantResponse.builder()
                .seq(TEST_PARTICIPANT_SEQ)
                .userName("홍길동")
                .department("개발팀")
                .position("과장")
                .participantType("일반")
                .checkCode(TEST_CHECK_CODE)
                .nametagPrinted("N")
                .build());

    PageResponse<EventParticipantResponse> mockResponse = PageResponse.of(participants, 1, 20, 1);

    when(participantService.getParticipants(any(ParticipantSearchRequest.class)))
        .thenReturn(mockResponse);

    // When & Then
    mockMvc
        .perform(
            get("/api/events/{eventSeq}/participants", TEST_EVENT_SEQ)
                .header("Authorization", "Bearer " + userToken)
                .param("page", "1")
                .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.content").isArray())
        .andExpect(jsonPath("$.data.content[0].userName").value("홍길동"));
  }

  @Test
  @Order(7)
  @DisplayName("7. 인증 없이 참가자 목록 조회 시도 - 실패")
  void getParticipants_WithoutAuth_Fail() throws Exception {
    // When & Then: 인증 없이 조회 시도
    mockMvc
        .perform(get("/api/events/{eventSeq}/participants", TEST_EVENT_SEQ))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @Order(8)
  @DisplayName("8. QR 스캔으로 참가자 정보 조회 (인증 불필요)")
  void getParticipantByCheckCode_NoAuth_Success() throws Exception {
    // Given: 체크코드로 참가자 정보 조회
    ParticipantStatusResponse mockResponse =
        ParticipantStatusResponse.builder()
            .participant(
                ParticipantStatusResponse.ParticipantInfo.builder()
                    .seq(TEST_PARTICIPANT_SEQ)
                    .checkCode(TEST_CHECK_CODE)
                    .name("홍길동")
                    .department("개발팀")
                    .position("과장")
                    .participantType("일반")
                    .phone("01023456789")
                    .email("hong@test.com")
                    .nametagPrinted("N")
                    .build())
            .actions(
                List.of(
                    ParticipantStatusResponse.ActionStatus.builder()
                        .actionTypeSeq(TEST_CHECKIN_ACTION_SEQ)
                        .actionCode("CHECK_IN")
                        .actionName("입장")
                        .completed(false)
                        .requireAdminAuth(false)
                        .allowMultiple(false)
                        .build(),
                    ParticipantStatusResponse.ActionStatus.builder()
                        .actionTypeSeq(TEST_PRIZE_ACTION_SEQ)
                        .actionCode("PRIZE")
                        .actionName("경품 수령")
                        .completed(false)
                        .requireAdminAuth(true)
                        .allowMultiple(false)
                        .build()))
            .build();

    when(participantService.getParticipantStatusByCheckCode(
            eq(TEST_EVENT_SEQ), eq(TEST_CHECK_CODE)))
        .thenReturn(mockResponse);

    // When & Then: 인증 없이 접근 가능
    mockMvc
        .perform(get("/api/events/{eventSeq}/check/{checkCode}", TEST_EVENT_SEQ, TEST_CHECK_CODE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.participant.name").value("홍길동"))
        .andExpect(jsonPath("$.data.actions").isArray())
        .andExpect(jsonPath("$.data.actions[0].actionCode").value("CHECK_IN"))
        .andExpect(jsonPath("$.data.actions[0].completed").value(false));
  }

  @Test
  @Order(9)
  @DisplayName("9. QR 스캔으로 체크인 (인증 불필요)")
  void checkIn_NoAuth_Success() throws Exception {
    // Given: 체크인 응답
    EventCheckResponse mockResponse =
        EventCheckResponse.builder()
            .action("CHECK_IN")
            .actionName("입장")
            .participant(
                EventCheckResponse.ParticipantInfo.builder()
                    .seq(TEST_PARTICIPANT_SEQ)
                    .name("홍길동")
                    .department("개발팀")
                    .position("과장")
                    .participantType("일반")
                    .build())
            .nametagUrl("http://localhost:8080/api/events/100/nametag/1")
            .message("입장 처리되었습니다. 명찰을 출력해주세요.")
            .build();

    when(checkService.checkIn(eq(TEST_EVENT_SEQ), eq(TEST_CHECK_CODE), any()))
        .thenReturn(mockResponse);

    // When & Then: 인증 없이 체크인 가능
    mockMvc
        .perform(
            post("/api/events/{eventSeq}/check/{checkCode}", TEST_EVENT_SEQ, TEST_CHECK_CODE)
                .header("X-Device-Info", "Samsung Galaxy S21"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.action").value("CHECK_IN"))
        .andExpect(jsonPath("$.data.participant.name").value("홍길동"))
        .andExpect(jsonPath("$.data.nametagUrl").exists())
        .andExpect(jsonPath("$.message").value("입장 처리되었습니다. 명찰을 출력해주세요."));

    verify(checkService, times(1)).checkIn(eq(TEST_EVENT_SEQ), eq(TEST_CHECK_CODE), any());
  }

  @Test
  @Order(10)
  @DisplayName("10. 이미 체크인한 참가자 재스캔 (인증 불필요)")
  void checkIn_AlreadyCheckedIn_Success() throws Exception {
    // Given: 이미 체크인한 참가자
    EventCheckResponse mockResponse =
        EventCheckResponse.builder()
            .action("ALREADY_CHECKED_IN")
            .actionName("입장 완료")
            .participant(
                EventCheckResponse.ParticipantInfo.builder()
                    .seq(TEST_PARTICIPANT_SEQ)
                    .name("홍길동")
                    .department("개발팀")
                    .position("과장")
                    .build())
            .message("이미 입장 처리된 참가자입니다.")
            .build();

    when(checkService.checkIn(eq(TEST_EVENT_SEQ), eq(TEST_CHECK_CODE), any()))
        .thenReturn(mockResponse);

    // When & Then
    mockMvc
        .perform(post("/api/events/{eventSeq}/check/{checkCode}", TEST_EVENT_SEQ, TEST_CHECK_CODE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.action").value("ALREADY_CHECKED_IN"))
        .andExpect(jsonPath("$.message").value("이미 입장 처리된 참가자입니다."));
  }

  @Test
  @Order(11)
  @DisplayName("11. 참가자 상태 조회 - 체크인 완료 확인 (인증 필요)")
  void getParticipantStatus_AfterCheckIn() throws Exception {
    // Given: 체크인 완료 후 상태
    ParticipantStatusResponse mockResponse =
        ParticipantStatusResponse.builder()
            .participant(
                ParticipantStatusResponse.ParticipantInfo.builder()
                    .seq(TEST_PARTICIPANT_SEQ)
                    .name("홍길동")
                    .department("개발팀")
                    .position("과장")
                    .nametagPrinted("N")
                    .build())
            .actions(
                List.of(
                    ParticipantStatusResponse.ActionStatus.builder()
                        .actionTypeSeq(TEST_CHECKIN_ACTION_SEQ)
                        .actionCode("CHECK_IN")
                        .actionName("입장")
                        .completed(true)
                        .completedAt(LocalDateTime.now())
                        .requireAdminAuth(false)
                        .build(),
                    ParticipantStatusResponse.ActionStatus.builder()
                        .actionTypeSeq(TEST_PRIZE_ACTION_SEQ)
                        .actionCode("PRIZE")
                        .actionName("경품 수령")
                        .completed(false)
                        .requireAdminAuth(true)
                        .build()))
            .build();

    when(participantService.getParticipantStatus(TEST_PARTICIPANT_SEQ)).thenReturn(mockResponse);

    // When & Then
    mockMvc
        .perform(
            get(
                    "/api/events/{eventSeq}/participants/{seq}/status",
                    TEST_EVENT_SEQ,
                    TEST_PARTICIPANT_SEQ)
                .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.actions[0].actionCode").value("CHECK_IN"))
        .andExpect(jsonPath("$.data.actions[0].completed").value(true))
        .andExpect(jsonPath("$.data.actions[1].actionCode").value("PRIZE"))
        .andExpect(jsonPath("$.data.actions[1].completed").value(false));
  }

  @Test
  @Order(12)
  @DisplayName("12. 명찰 데이터 조회 (인증 필요)")
  void getNametagData_Success() throws Exception {
    // Given: 명찰 데이터
    Map<String, Object> nametagData = new HashMap<>();
    nametagData.put("participantSeq", TEST_PARTICIPANT_SEQ);
    nametagData.put("eventName", "2025 신년 세미나");
    nametagData.put("name", "홍길동");
    nametagData.put("department", "개발팀");
    nametagData.put("position", "과장");
    nametagData.put("participantType", "일반");
    nametagData.put("checkCode", TEST_CHECK_CODE);

    when(nametagService.getNametagData(TEST_PARTICIPANT_SEQ)).thenReturn(nametagData);

    // When & Then
    mockMvc
        .perform(
            get(
                    "/api/events/{eventSeq}/participants/{seq}/nametag",
                    TEST_EVENT_SEQ,
                    TEST_PARTICIPANT_SEQ)
                .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.name").value("홍길동"))
        .andExpect(jsonPath("$.data.department").value("개발팀"))
        .andExpect(jsonPath("$.data.eventName").value("2025 신년 세미나"));
  }

  @Test
  @Order(13)
  @DisplayName("13. 명찰 출력 로그 기록 (인증 필요)")
  void recordNametagPrint_Success() throws Exception {
    // Given: 명찰 출력 요청
    NametagPrintRequest request = NametagPrintRequest.builder().templateType("DEFAULT").build();

    doNothing().when(nametagService).recordPrint(any(NametagPrintRequest.class), anyString());

    // When & Then
    mockMvc
        .perform(
            post(
                    "/api/events/{eventSeq}/participants/{seq}/nametag/print",
                    TEST_EVENT_SEQ,
                    TEST_PARTICIPANT_SEQ)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("명찰 출력이 기록되었습니다."));

    verify(nametagService, times(1)).recordPrint(any(NametagPrintRequest.class), anyString());
  }

  @Test
  @Order(14)
  @DisplayName("14. 관리자 액션 처리 - 경품 수령 (관리자 비밀번호 필요)")
  void processAction_Prize_Success() throws Exception {
    // Given: 경품 수령 처리 요청
    EventCheckRequest request =
        EventCheckRequest.builder()
            .participantSeq(TEST_PARTICIPANT_SEQ)
            .actionCode("PRIZE")
            .adminPassword("admin1234!")
            .memo("1등 경품 수령")
            .build();

    EventCheckResponse mockResponse =
        EventCheckResponse.builder()
            .action("PRIZE")
            .actionName("경품 수령")
            .participant(
                EventCheckResponse.ParticipantInfo.builder()
                    .seq(TEST_PARTICIPANT_SEQ)
                    .name("홍길동")
                    .department("개발팀")
                    .position("과장")
                    .build())
            .message("경품 수령 처리가 완료되었습니다.")
            .build();

    when(checkService.processAction(eq(TEST_EVENT_SEQ), any(EventCheckRequest.class), anyString()))
        .thenReturn(mockResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/events/{eventSeq}/participants/action", TEST_EVENT_SEQ)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.action").value("PRIZE"))
        .andExpect(jsonPath("$.message").value("경품 수령 처리가 완료되었습니다."));

    verify(checkService, times(1))
        .processAction(eq(TEST_EVENT_SEQ), any(EventCheckRequest.class), anyString());
  }

  @Test
  @Order(15)
  @DisplayName("15. 관리자 액션 처리 - 비밀번호 오류 (실패)")
  void processAction_WrongPassword_Fail() throws Exception {
    // Given: 잘못된 비밀번호
    EventCheckRequest request =
        EventCheckRequest.builder()
            .participantSeq(TEST_PARTICIPANT_SEQ)
            .actionCode("GIFT")
            .adminPassword("wrongPassword")
            .build();

    when(checkService.processAction(eq(TEST_EVENT_SEQ), any(EventCheckRequest.class), anyString()))
        .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "관리자 비밀번호가 일치하지 않습니다."));

    // When & Then
    mockMvc
        .perform(
            post("/api/events/{eventSeq}/participants/action", TEST_EVENT_SEQ)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("관리자 비밀번호가 일치하지 않습니다."));
  }

  @Test
  @Order(16)
  @DisplayName("16. 중복 액션 처리 시도 - 실패")
  void processAction_Duplicate_Fail() throws Exception {
    // Given: 이미 처리된 액션 재처리 시도
    EventCheckRequest request =
        EventCheckRequest.builder()
            .participantSeq(TEST_PARTICIPANT_SEQ)
            .actionCode("PRIZE")
            .adminPassword("admin1234!")
            .build();

    when(checkService.processAction(eq(TEST_EVENT_SEQ), any(EventCheckRequest.class), anyString()))
        .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "이미 경품 수령 처리된 참가자입니다."));

    // When & Then
    mockMvc
        .perform(
            post("/api/events/{eventSeq}/participants/action", TEST_EVENT_SEQ)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("이미 경품 수령 처리된 참가자입니다."));
  }

  @Test
  @Order(17)
  @DisplayName("17. 관리자 액션 처리 - 기념품 수령")
  void processAction_Gift_Success() throws Exception {
    // Given: 기념품 수령 처리 요청
    EventCheckRequest request =
        EventCheckRequest.builder()
            .participantSeq(TEST_PARTICIPANT_SEQ)
            .actionCode("GIFT")
            .adminPassword("admin1234!")
            .build();

    EventCheckResponse mockResponse =
        EventCheckResponse.builder()
            .action("GIFT")
            .actionName("기념품 수령")
            .participant(
                EventCheckResponse.ParticipantInfo.builder()
                    .seq(TEST_PARTICIPANT_SEQ)
                    .name("홍길동")
                    .build())
            .message("기념품 수령 처리가 완료되었습니다.")
            .build();

    when(checkService.processAction(eq(TEST_EVENT_SEQ), any(EventCheckRequest.class), anyString()))
        .thenReturn(mockResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/events/{eventSeq}/participants/action", TEST_EVENT_SEQ)
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.action").value("GIFT"))
        .andExpect(jsonPath("$.message").value("기념품 수령 처리가 완료되었습니다."));
  }

  @Test
  @Order(18)
  @DisplayName("18. 최종 참가자 상태 조회 - 모든 액션 완료")
  void getParticipantStatus_AllActionsCompleted() throws Exception {
    // Given: 모든 액션 완료 상태
    ParticipantStatusResponse mockResponse =
        ParticipantStatusResponse.builder()
            .participant(
                ParticipantStatusResponse.ParticipantInfo.builder()
                    .seq(TEST_PARTICIPANT_SEQ)
                    .name("홍길동")
                    .department("개발팀")
                    .position("과장")
                    .nametagPrinted("Y")
                    .build())
            .actions(
                List.of(
                    ParticipantStatusResponse.ActionStatus.builder()
                        .actionTypeSeq(TEST_CHECKIN_ACTION_SEQ)
                        .actionCode("CHECK_IN")
                        .actionName("입장")
                        .completed(true)
                        .completedAt(LocalDateTime.now().minusHours(2))
                        .requireAdminAuth(false)
                        .build(),
                    ParticipantStatusResponse.ActionStatus.builder()
                        .actionTypeSeq(TEST_PRIZE_ACTION_SEQ)
                        .actionCode("PRIZE")
                        .actionName("경품 수령")
                        .completed(true)
                        .completedAt(LocalDateTime.now().minusHours(1))
                        .confirmedBy(TEST_USER_ID)
                        .requireAdminAuth(true)
                        .build(),
                    ParticipantStatusResponse.ActionStatus.builder()
                        .actionTypeSeq(TEST_GIFT_ACTION_SEQ)
                        .actionCode("GIFT")
                        .actionName("기념품 수령")
                        .completed(true)
                        .completedAt(LocalDateTime.now())
                        .confirmedBy(TEST_USER_ID)
                        .requireAdminAuth(true)
                        .build()))
            .build();

    when(participantService.getParticipantStatus(TEST_PARTICIPANT_SEQ)).thenReturn(mockResponse);

    // When & Then
    mockMvc
        .perform(
            get(
                    "/api/events/{eventSeq}/participants/{seq}/status",
                    TEST_EVENT_SEQ,
                    TEST_PARTICIPANT_SEQ)
                .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.participant.nametagPrinted").value("Y"))
        .andExpect(jsonPath("$.data.actions[0].completed").value(true))
        .andExpect(jsonPath("$.data.actions[1].completed").value(true))
        .andExpect(jsonPath("$.data.actions[2].completed").value(true));
  }

  @Test
  @Order(19)
  @DisplayName("19. 존재하지 않는 체크코드로 조회 - 실패")
  void checkIn_InvalidCheckCode_Fail() throws Exception {
    // Given: 존재하지 않는 체크코드
    String invalidCheckCode = "invalid_check_code_123";

    when(checkService.checkIn(eq(TEST_EVENT_SEQ), eq(invalidCheckCode), any()))
        .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

    // When & Then
    mockMvc
        .perform(post("/api/events/{eventSeq}/check/{checkCode}", TEST_EVENT_SEQ, invalidCheckCode))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("참가자 정보를 찾을 수 없습니다."));
  }

  @Test
  @Order(20)
  @DisplayName("20. 참가자 삭제 (인증 필요)")
  void deleteParticipant_Success() throws Exception {
    // Given: 참가자 삭제
    doNothing().when(participantService).deleteParticipant(eq(TEST_PARTICIPANT_SEQ), anyString());

    // When & Then
    mockMvc
        .perform(
            delete(
                    "/api/events/{eventSeq}/participants/{seq}",
                    TEST_EVENT_SEQ,
                    TEST_PARTICIPANT_SEQ)
                .header("Authorization", "Bearer " + userToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("참가자가 삭제되었습니다."));

    verify(participantService, times(1)).deleteParticipant(eq(TEST_PARTICIPANT_SEQ), anyString());
  }
}
