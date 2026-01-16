package kr.wisead.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.event.dto.*;
import kr.wisead.domain.event.service.*;
import kr.wisead.domain.survey.dto.*;
import kr.wisead.domain.survey.service.EventService;
import kr.wisead.domain.survey.service.SurveyService;
import kr.wisead.domain.survey.service.SurveyUserService;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 설문 응답 → 행사 참여 통합 테스트
 *
 * 테스트 시나리오 A: 사전 설문 응답 후 현장 체크인
 * 1. 행사(설문) 생성
 * 2. 참가자 등록 (SURVEY_USER)
 * 3. 사전 설문 발송 및 응답
 * 4. 현장에서 QR 체크인
 * 5. 경품/기념품 수령
 *
 * 테스트 시나리오 B: 설문 미응답 상태에서 현장 체크인
 * 1. 행사에 등록된 참가자가 설문 미응답 상태
 * 2. 현장에서 QR 체크인 가능
 * 3. 경품 수령 (관리자 판단에 따라)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("설문 응답 → 행사 참여 통합 테스트")
class SurveyToEventParticipantIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private SurveyService surveyService;

    @MockitoBean
    private SurveyUserService surveyUserService;

    @MockitoBean
    private EventParticipantService participantService;

    @MockitoBean
    private EventCheckService checkService;

    @MockitoBean
    private EventActionTypeService actionTypeService;

    @MockitoBean
    private NametagService nametagService;

    private static final String TEST_USER_ID = "testuser01";
    private static final Integer TEST_EVENT_SEQ = 200;
    private static final String TEST_EVENT_CODE = "SEMINAR2025";
    private static final String TEST_USER_KEY_RESPONDENT = "RESP_USER_KEY_001";
    private static final String TEST_USER_KEY_NON_RESPONDENT = "NON_RESP_USER_KEY_002";
    private static final Long TEST_PARTICIPANT_RESPONDENT_SEQ = 10L;
    private static final Long TEST_PARTICIPANT_NON_RESPONDENT_SEQ = 11L;
    private static final String TEST_CHECK_CODE_RESPONDENT = "check_code_respondent_123";
    private static final String TEST_CHECK_CODE_NON_RESPONDENT = "check_code_non_resp_456";

    private String userToken;

    @BeforeEach
    void setUp() {
        var userAuth = new UsernamePasswordAuthenticationToken(
                TEST_USER_ID, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        userToken = jwtTokenProvider.createAccessToken(userAuth, "테스트사용자");
    }

    // ========== 시나리오 A: 사전 설문 응답 후 현장 체크인 ==========

    @Test
    @Order(1)
    @DisplayName("A-1. 행사(설문 포함) 생성")
    void createEventWithSurvey_Success() throws Exception {
        // Given: 행사 + 설문 생성 요청
        EventRequest request = EventRequest.builder()
                .eventName("2025 기술 세미나")
                .eventType("S")  // 설문 포함
                .eventDesc("사전 설문 응답 후 현장 참여")
                .startDate("2025-02-01")
                .endDate("2025-02-01")
                .status("P")  // 진행중
                .auth("NA")
                .privacyPolicyYn("Y")
                .privacyPolicyTtl("개인정보 수집 동의")
                .privacyPolicyDesc("이름, 연락처, 이메일")
                .questions(List.of(
                        QuestionRequest.builder()
                                .questionType("MC")
                                .questionTypeDetail("MCS")
                                .question("세미나 참석 경로를 선택해주세요.")
                                .order(1)
                                .items(List.of(
                                        ItemRequest.builder().item("이메일 초대").itemValue("1").order(1).build(),
                                        ItemRequest.builder().item("SNS 광고").itemValue("2").order(2).build(),
                                        ItemRequest.builder().item("지인 추천").itemValue("3").order(3).build()
                                ))
                                .build(),
                        QuestionRequest.builder()
                                .questionType("SA")
                                .questionTypeDetail("SAL")
                                .question("세미나에서 듣고 싶은 주제가 있으신가요?")
                                .order(2)
                                .build()
                ))
                .build();

        EventResponse mockResponse = EventResponse.builder()
                .eventSeq(TEST_EVENT_SEQ)
                .eventCode(TEST_EVENT_CODE)
                .eventName("2025 기술 세미나")
                .eventType("S")
                .status("P")
                .build();

        when(eventService.createEvent(anyString(), any(EventRequest.class),
                any(), any(), anyList(), anyList()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/event")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventSeq").value(TEST_EVENT_SEQ));
    }

    @Test
    @Order(2)
    @DisplayName("A-2. 참가자 등록 (설문 대상자)")
    void createSurveyUsers_Success() throws Exception {
        // Given: 설문 대상자 등록
        SurveyUserRequest request = SurveyUserRequest.builder()
                .eventSeq(TEST_EVENT_SEQ)
                .userPhone("01023456789")
                .build();

        SurveyUserResponse mockResponse = SurveyUserResponse.builder()
                .userSeq(1)
                .eventSeq(TEST_EVENT_SEQ)
                .userKey(TEST_USER_KEY_RESPONDENT)
                .status("미참여")
                .build();

        when(surveyUserService.createUser(any(SurveyUserRequest.class), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/survey/users")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userKey").value(TEST_USER_KEY_RESPONDENT));
    }

    @Test
    @Order(3)
    @DisplayName("A-3. 참가자가 사전 설문 응답 (인증 불필요)")
    void submitSurvey_BeforeEvent_Success() throws Exception {
        // Given: 설문 응답 제출
        SurveySubmitRequest request = SurveySubmitRequest.builder()
                .userKey(TEST_USER_KEY_RESPONDENT)
                .userName("김철수")
                .userPhone("01023456789")
                .userEmail("chulsoo@test.com")
                .answers(List.of(
                        SurveySubmitRequest.AnswerRequest.builder()
                                .questionSeq(1)
                                .questionType("MC")
                                .questionTypeDetail("MCS")
                                .itemSeq(1)
                                .answer("1")
                                .build(),
                        SurveySubmitRequest.AnswerRequest.builder()
                                .questionSeq(2)
                                .questionType("SA")
                                .questionTypeDetail("SAL")
                                .answer("AI/ML 관련 주제를 듣고 싶습니다.")
                                .build()
                ))
                .build();

        doNothing().when(surveyService).submitSurvey(eq(TEST_EVENT_SEQ), any(SurveySubmitRequest.class));

        // When & Then: 인증 없이 설문 응답
        mockMvc.perform(post("/api/survey/{eventSeq}/submit", TEST_EVENT_SEQ)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(surveyService, times(1)).submitSurvey(eq(TEST_EVENT_SEQ), any(SurveySubmitRequest.class));
    }

    @Test
    @Order(4)
    @DisplayName("A-4. 행사 참가자로 등록 (EVENT_PARTICIPANT)")
    void createEventParticipant_ForRespondent_Success() throws Exception {
        // Given: 설문 응답자를 행사 참가자로 등록
        EventParticipantRequest request = EventParticipantRequest.builder()
                .userName("김철수")
                .userPhone("010-2345-6789")
                .userEmail("chulsoo@test.com")
                .department("마케팅팀")
                .position("대리")
                .participantType("일반")
                .build();

        EventParticipantResponse mockResponse = EventParticipantResponse.builder()
                .seq(TEST_PARTICIPANT_RESPONDENT_SEQ)
                .surveyUserSeq(1)
                .eventSeq(TEST_EVENT_SEQ)
                .checkCode(TEST_CHECK_CODE_RESPONDENT)
                .userName("김철수")
                .department("마케팅팀")
                .position("대리")
                .participantType("일반")
                .nametagPrinted("N")
                .qrCodeUrl("http://localhost:8080/event/check/" + TEST_CHECK_CODE_RESPONDENT)
                .build();

        when(participantService.createParticipant(any(EventParticipantRequest.class), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/events/{eventSeq}/participants", TEST_EVENT_SEQ)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.checkCode").value(TEST_CHECK_CODE_RESPONDENT));
    }

    @Test
    @Order(5)
    @DisplayName("A-5. 설문 응답자 현장 체크인 (인증 불필요)")
    void checkIn_Respondent_Success() throws Exception {
        // Given: 설문 응답자 체크인
        EventCheckResponse mockResponse = EventCheckResponse.builder()
                .action("CHECK_IN")
                .actionName("입장")
                .participant(EventCheckResponse.ParticipantInfo.builder()
                        .seq(TEST_PARTICIPANT_RESPONDENT_SEQ)
                        .name("김철수")
                        .department("마케팅팀")
                        .position("대리")
                        .participantType("일반")
                        .build())
                .nametagUrl("http://localhost:8080/api/events/200/nametag/10")
                .message("입장 처리되었습니다. 명찰을 출력해주세요.")
                .build();

        when(checkService.checkIn(eq(TEST_CHECK_CODE_RESPONDENT), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/events/check/{checkCode}", TEST_CHECK_CODE_RESPONDENT)
                        .header("X-Device-Info", "iPad Pro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.action").value("CHECK_IN"))
                .andExpect(jsonPath("$.data.participant.name").value("김철수"));
    }

    // ========== 시나리오 B: 설문 미응답 상태에서 현장 체크인 ==========

    @Test
    @Order(6)
    @DisplayName("B-1. 설문 미응답 참가자 등록")
    void createEventParticipant_NonRespondent_Success() throws Exception {
        // Given: 설문 미응답 참가자 등록
        EventParticipantRequest request = EventParticipantRequest.builder()
                .userName("이영희")
                .userPhone("010-9876-5432")
                .userEmail("younghee@test.com")
                .department("개발팀")
                .position("사원")
                .participantType("일반")
                .memo("설문 미응답 상태")
                .build();

        EventParticipantResponse mockResponse = EventParticipantResponse.builder()
                .seq(TEST_PARTICIPANT_NON_RESPONDENT_SEQ)
                .surveyUserSeq(2)
                .eventSeq(TEST_EVENT_SEQ)
                .checkCode(TEST_CHECK_CODE_NON_RESPONDENT)
                .userName("이영희")
                .department("개발팀")
                .position("사원")
                .participantType("일반")
                .memo("설문 미응답 상태")
                .nametagPrinted("N")
                .qrCodeUrl("http://localhost:8080/event/check/" + TEST_CHECK_CODE_NON_RESPONDENT)
                .build();

        when(participantService.createParticipant(any(EventParticipantRequest.class), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/events/{eventSeq}/participants", TEST_EVENT_SEQ)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.checkCode").value(TEST_CHECK_CODE_NON_RESPONDENT));
    }

    @Test
    @Order(7)
    @DisplayName("B-2. 설문 미응답 참가자 상태 조회 (인증 불필요)")
    void getStatus_NonRespondent_Success() throws Exception {
        // Given: 설문 미응답 참가자 상태
        ParticipantStatusResponse mockResponse = ParticipantStatusResponse.builder()
                .participant(ParticipantStatusResponse.ParticipantInfo.builder()
                        .seq(TEST_PARTICIPANT_NON_RESPONDENT_SEQ)
                        .checkCode(TEST_CHECK_CODE_NON_RESPONDENT)
                        .name("이영희")
                        .department("개발팀")
                        .position("사원")
                        .participantType("일반")
                        .nametagPrinted("N")
                        .build())
                .actions(List.of(
                        ParticipantStatusResponse.ActionStatus.builder()
                                .actionCode("CHECK_IN")
                                .actionName("입장")
                                .completed(false)
                                .requireAdminAuth(false)
                                .build()
                ))
                .build();

        when(participantService.getParticipantStatusByCheckCode(TEST_CHECK_CODE_NON_RESPONDENT))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/events/check/{checkCode}", TEST_CHECK_CODE_NON_RESPONDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.participant.name").value("이영희"))
                .andExpect(jsonPath("$.data.actions[0].completed").value(false));
    }

    @Test
    @Order(8)
    @DisplayName("B-3. 설문 미응답 참가자도 체크인 가능 (인증 불필요)")
    void checkIn_NonRespondent_Success() throws Exception {
        // Given: 설문 미응답 참가자 체크인
        EventCheckResponse mockResponse = EventCheckResponse.builder()
                .action("CHECK_IN")
                .actionName("입장")
                .participant(EventCheckResponse.ParticipantInfo.builder()
                        .seq(TEST_PARTICIPANT_NON_RESPONDENT_SEQ)
                        .name("이영희")
                        .department("개발팀")
                        .position("사원")
                        .build())
                .nametagUrl("http://localhost:8080/api/events/200/nametag/11")
                .message("입장 처리되었습니다. 명찰을 출력해주세요.")
                .build();

        when(checkService.checkIn(eq(TEST_CHECK_CODE_NON_RESPONDENT), any()))
                .thenReturn(mockResponse);

        // When & Then: 설문 미응답자도 체크인 가능
        mockMvc.perform(post("/api/events/check/{checkCode}", TEST_CHECK_CODE_NON_RESPONDENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.action").value("CHECK_IN"))
                .andExpect(jsonPath("$.data.participant.name").value("이영희"));
    }

    @Test
    @Order(9)
    @DisplayName("B-4. 설문 미응답 참가자 경품 수령 (관리자 판단)")
    void processAction_NonRespondent_Prize_Success() throws Exception {
        // Given: 관리자가 설문 미응답자에게도 경품 지급
        EventCheckRequest request = EventCheckRequest.builder()
                .participantSeq(TEST_PARTICIPANT_NON_RESPONDENT_SEQ)
                .actionCode("PRIZE")
                .adminPassword("admin1234!")
                .memo("현장 설문 응답 완료 - 경품 지급")
                .build();

        EventCheckResponse mockResponse = EventCheckResponse.builder()
                .action("PRIZE")
                .actionName("경품 수령")
                .participant(EventCheckResponse.ParticipantInfo.builder()
                        .seq(TEST_PARTICIPANT_NON_RESPONDENT_SEQ)
                        .name("이영희")
                        .build())
                .message("경품 수령 처리가 완료되었습니다.")
                .build();

        when(checkService.processAction(eq(TEST_EVENT_SEQ), any(EventCheckRequest.class), anyString()))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/events/{eventSeq}/participants/action", TEST_EVENT_SEQ)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.action").value("PRIZE"));
    }

    // ========== 공통 케이스 ==========

    @Test
    @Order(10)
    @DisplayName("C-1. 인증 없이 관리자 액션 시도 - 실패")
    void processAction_WithoutAuth_Fail() throws Exception {
        // Given: 인증 없이 관리자 액션 시도
        EventCheckRequest request = EventCheckRequest.builder()
                .participantSeq(TEST_PARTICIPANT_RESPONDENT_SEQ)
                .actionCode("PRIZE")
                .adminPassword("admin1234!")
                .build();

        // When & Then: 인증 필요 API에 인증 없이 접근
        mockMvc.perform(post("/api/events/{eventSeq}/participants/action", TEST_EVENT_SEQ)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(11)
    @DisplayName("C-2. 인증 없이 참가자 등록 시도 - 실패")
    void createParticipant_WithoutAuth_Fail() throws Exception {
        // Given: 인증 없이 참가자 등록 시도
        EventParticipantRequest request = EventParticipantRequest.builder()
                .userName("테스트")
                .userPhone("010-1111-2222")
                .build();

        // When & Then: 인증 필요
        mockMvc.perform(post("/api/events/{eventSeq}/participants", TEST_EVENT_SEQ)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(12)
    @DisplayName("C-3. 체크인 API는 인증 없이 접근 가능")
    void checkIn_NoAuthRequired_Success() throws Exception {
        // Given: 체크인은 인증 불필요
        EventCheckResponse mockResponse = EventCheckResponse.builder()
                .action("CHECK_IN")
                .actionName("입장")
                .participant(EventCheckResponse.ParticipantInfo.builder()
                        .name("테스트")
                        .build())
                .message("입장 처리되었습니다.")
                .build();

        when(checkService.checkIn(eq("any_check_code"), any()))
                .thenReturn(mockResponse);

        // When & Then: 인증 없이 접근 가능
        mockMvc.perform(post("/api/events/check/{checkCode}", "any_check_code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(13)
    @DisplayName("C-4. 참가자 정보 조회도 인증 없이 가능")
    void getParticipantStatus_NoAuthRequired_Success() throws Exception {
        // Given: 참가자 상태 조회 (체크인 전 확인용)
        ParticipantStatusResponse mockResponse = ParticipantStatusResponse.builder()
                .participant(ParticipantStatusResponse.ParticipantInfo.builder()
                        .name("테스트")
                        .build())
                .actions(List.of())
                .build();

        when(participantService.getParticipantStatusByCheckCode("any_check_code"))
                .thenReturn(mockResponse);

        // When & Then: GET 요청도 인증 불필요
        mockMvc.perform(get("/api/events/check/{checkCode}", "any_check_code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
