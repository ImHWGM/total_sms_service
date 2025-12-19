package kr.wisead.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.wisead.domain.message.dto.SmsSendRequest;
import kr.wisead.domain.message.dto.SmsSendResponse;
import kr.wisead.domain.message.service.MessageSendService;
import kr.wisead.domain.survey.dto.*;
import kr.wisead.domain.survey.service.EventService;
import kr.wisead.domain.survey.service.SurveyAnswerService;
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

import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 설문 워크플로우 통합 테스트
 *
 * 테스트 시나리오:
 * 1. 사용자가 설문을 생성한다
 * 2. 설문 대상자를 등록한다
 * 3. 설문 문자를 발송한다
 * 4. 설문 응답자가 설문에 참여한다
 * 5. 응답 기록을 확인한다
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("설문 워크플로우 통합 테스트")
class SurveyWorkflowIntegrationTest {

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
    private SurveyAnswerService surveyAnswerService;

    @MockitoBean
    private MessageSendService messageSendService;

    private static final String TEST_USER_ID = "testuser01";
    private static final Integer TEST_USER_SEQ = 1;
    private static final Integer TEST_EVENT_SEQ = 100;
    private static final String TEST_EVENT_CODE = "EVT123ABC456";
    private static final String TEST_USER_KEY = "USERKEY12345";

    private String userToken;

    @BeforeEach
    void setUp() {
        // 일반 사용자 토큰 생성
        var userAuth = new UsernamePasswordAuthenticationToken(
                TEST_USER_ID, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        userToken = jwtTokenProvider.createAccessToken(userAuth, "테스트사용자");
    }

    @Test
    @Order(1)
    @DisplayName("1. 설문(이벤트) 생성")
    void createEvent_Success() throws Exception {
        // Given: 설문 생성 요청
        EventRequest request = EventRequest.builder()
                .eventName("고객 만족도 설문조사")
                .eventType("S")
                .eventDesc("서비스 품질 향상을 위한 설문조사입니다.")
                .startDate("2025-01-01")
                .endDate("2025-01-31")
                .status("A")
                .auth("NA")
                .privacyPolicyYn("Y")
                .privacyPolicyTtl("개인정보 수집 및 이용 동의")
                .privacyPolicyDesc("수집 항목: 이름, 연락처")
                .questions(List.of(
                        QuestionRequest.builder()
                                .questionType("MC")
                                .questionTypeDetail("MCS")
                                .question("서비스에 만족하십니까?")
                                .order(1)
                                .items(List.of(
                                        ItemRequest.builder()
                                                .item("매우 만족")
                                                .itemValue("5")
                                                .order(1)
                                                .build(),
                                        ItemRequest.builder()
                                                .item("만족")
                                                .itemValue("4")
                                                .order(2)
                                                .build(),
                                        ItemRequest.builder()
                                                .item("보통")
                                                .itemValue("3")
                                                .order(3)
                                                .build()
                                ))
                                .build(),
                        QuestionRequest.builder()
                                .questionType("SA")
                                .questionTypeDetail("SAL")
                                .question("개선이 필요한 부분이 있다면 말씀해주세요.")
                                .order(2)
                                .build()
                ))
                .build();

        // Mock: 설문 생성 응답
        EventResponse mockResponse = EventResponse.builder()
                .eventSeq(TEST_EVENT_SEQ)
                .eventCode(TEST_EVENT_CODE)
                .eventName("고객 만족도 설문조사")
                .eventType("S")
                .status("A")
                .build();

        when(eventService.createEvent(anyString(), any(EventRequest.class)))
                .thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/event")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventSeq").value(TEST_EVENT_SEQ))
                .andExpect(jsonPath("$.data.eventCode").value(TEST_EVENT_CODE));

        verify(eventService, times(1)).createEvent(anyString(), any(EventRequest.class));
    }

    @Test
    @Order(2)
    @DisplayName("2. 설문 상태를 '진행중'으로 변경")
    void updateEventStatus_ToProgress() throws Exception {
        // Given: 상태 변경 요청
        doNothing().when(eventService).updateEventStatus(TEST_EVENT_SEQ, "P");

        // When & Then
        mockMvc.perform(patch("/api/event/{eventSeq}/status", TEST_EVENT_SEQ)
                        .header("Authorization", "Bearer " + userToken)
                        .param("status", "P"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(eventService, times(1)).updateEventStatus(TEST_EVENT_SEQ, "P");
    }

    @Test
    @Order(3)
    @DisplayName("3. 설문 대상자 등록")
    void createSurveyUser_Success() throws Exception {
        // Given: 대상자 등록 요청
        SurveyUserRequest request = SurveyUserRequest.builder()
                .eventSeq(TEST_EVENT_SEQ)
                .userPhone("01023456789")
                .build();

        // Mock: 대상자 등록 응답
        SurveyUserResponse mockResponse = SurveyUserResponse.builder()
                .userSeq(1)
                .eventSeq(TEST_EVENT_SEQ)
                .userKey(TEST_USER_KEY)
                .status("미참여")
                .build();

        when(surveyUserService.createUser(any(SurveyUserRequest.class), anyString()))
                .thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/survey/users")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userKey").value(TEST_USER_KEY));
    }

    @Test
    @Order(4)
    @DisplayName("4. 설문 문자 발송")
    void sendSurveyMessage_Success() throws Exception {
        // Given: 문자 발송 요청
        SmsSendRequest request = SmsSendRequest.builder()
                .msgType("L")  // LMS
                .callback("01011112222")
                .receivers(List.of("01023456789"))
                .subject("[설문조사] 고객 만족도 조사")
                .text("안녕하세요. 고객 만족도 설문조사에 참여해주세요.\n" +
                        "설문 링크: https://wisead.kr/survey/" + TEST_USER_KEY)
                .eventSeq(TEST_EVENT_SEQ)
                .build();

        // Mock: 문자 발송 응답
        SmsSendResponse mockResponse = SmsSendResponse.builder()
                .totalCount(1)
                .successCount(1)
                .failCount(0)
                .mseqList(List.of(123))
                .requestTime(LocalDateTime.now())
                .sendType("즉시발송")
                .build();

        when(messageSendService.sendMessage(any(SmsSendRequest.class), anyString()))
                .thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/message/send")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.successCount").value(1));

        verify(messageSendService, times(1)).sendMessage(any(SmsSendRequest.class), anyString());
    }

    @Test
    @Order(5)
    @DisplayName("5. 설문 응답자가 userKey로 설문 조회")
    void getSurveyByUserKey_Success() throws Exception {
        // Given: 설문 조회 요청
        EventResponse mockResponse = EventResponse.builder()
                .eventSeq(TEST_EVENT_SEQ)
                .eventCode(TEST_EVENT_CODE)
                .eventName("고객 만족도 설문조사")
                .eventType("S")
                .status("P")
                .questions(List.of(
                        QuestionResponse.builder()
                                .questionSeq(1)
                                .questionType("MC")
                                .question("서비스에 만족하십니까?")
                                .items(List.of(
                                        ItemResponse.builder()
                                                .itemSeq(1)
                                                .item("매우 만족")
                                                .itemValue("5")
                                                .build()
                                ))
                                .build()
                ))
                .build();

        when(surveyService.getSurveyByUserKey(TEST_USER_KEY)).thenReturn(mockResponse);

        // When & Then (인증 불필요 - 공개 API)
        mockMvc.perform(get("/api/survey/key/{userKey}", TEST_USER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventSeq").value(TEST_EVENT_SEQ))
                .andExpect(jsonPath("$.data.questions").isArray());
    }

    @Test
    @Order(6)
    @DisplayName("6. 설문 응답 제출")
    void submitSurvey_Success() throws Exception {
        // Given: 설문 제출 요청
        SurveySubmitRequest request = SurveySubmitRequest.builder()
                .userKey(TEST_USER_KEY)
                .userName("응답자홍길동")
                .userPhone("01023456789")
                .userEmail("respondent@test.com")
                .answers(List.of(
                        SurveySubmitRequest.AnswerRequest.builder()
                                .questionSeq(1)
                                .questionType("MC")
                                .questionTypeDetail("MCS")
                                .itemSeq(1)
                                .answer("5")
                                .build(),
                        SurveySubmitRequest.AnswerRequest.builder()
                                .questionSeq(2)
                                .questionType("SA")
                                .questionTypeDetail("SAL")
                                .answer("서비스가 전반적으로 좋았습니다. 다만 응답 속도 개선이 필요합니다.")
                                .build()
                ))
                .build();

        doNothing().when(surveyService).submitSurvey(eq(TEST_EVENT_SEQ), any(SurveySubmitRequest.class));

        // When & Then (인증 불필요 - 공개 API)
        mockMvc.perform(post("/api/survey/{eventSeq}/submit", TEST_EVENT_SEQ)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(surveyService, times(1)).submitSurvey(eq(TEST_EVENT_SEQ), any(SurveySubmitRequest.class));
    }

    @Test
    @Order(7)
    @DisplayName("7. 설문 응답 기록 확인 - 이벤트별 답변 수")
    void getAnswerCount_AfterSubmission() throws Exception {
        // Given: 응답 기록 확인
        when(surveyAnswerService.getAnswerCount(TEST_EVENT_SEQ)).thenReturn(2);

        // When & Then
        mockMvc.perform(get("/api/survey/answers/count")
                        .header("Authorization", "Bearer " + userToken)
                        .param("eventSeq", String.valueOf(TEST_EVENT_SEQ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    @Order(8)
    @DisplayName("8. 설문 통계 조회")
    void getSurveyStatistics_Success() throws Exception {
        // Given: 통계 조회
        SurveyStatisticsResponse mockStats = SurveyStatisticsResponse.builder()
                .eventSeq(TEST_EVENT_SEQ)
                .eventName("고객 만족도 설문조사")
                .totalParticipants(10)
                .completedParticipants(8)
                .absentees(2)
                .lurkers(0)
                .responseRate(80.0)
                .questionStatistics(List.of(
                        SurveyStatisticsResponse.QuestionStatistics.builder()
                                .questionSeq(1)
                                .question("서비스에 만족하십니까?")
                                .questionType("MC")
                                .totalAnswers(8)
                                .itemStatistics(List.of(
                                        SurveyStatisticsResponse.ItemStatistics.builder()
                                                .itemSeq(1)
                                                .item("매우 만족")
                                                .itemValue("5")
                                                .count(5)
                                                .percentage(62.5)
                                                .build()
                                ))
                                .build()
                ))
                .build();

        when(eventService.getStatistics(TEST_EVENT_SEQ)).thenReturn(mockStats);

        // When & Then
        mockMvc.perform(get("/api/event/{eventSeq}/statistics", TEST_EVENT_SEQ)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalParticipants").value(10))
                .andExpect(jsonPath("$.data.completedParticipants").value(8))
                .andExpect(jsonPath("$.data.responseRate").value(80.0));
    }

    @Test
    @Order(9)
    @DisplayName("9. 참여자 목록 조회")
    void getParticipants_Success() throws Exception {
        // Given: 참여자 목록
        List<SurveyUserResponse> participants = List.of(
                SurveyUserResponse.builder()
                        .userSeq(1)
                        .eventSeq(TEST_EVENT_SEQ)
                        .userKey(TEST_USER_KEY)
                        .userName("응답자홍길동")
                        .status("참여완료")
                        .build()
        );

        when(surveyService.getParticipants(TEST_EVENT_SEQ)).thenReturn(participants);

        // When & Then
        mockMvc.perform(get("/api/survey/{eventSeq}/participants", TEST_EVENT_SEQ)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].status").value("참여완료"));
    }

    @Test
    @Order(10)
    @DisplayName("10. 중복 제출 시도 - 실패")
    void submitSurvey_Duplicate_Fail() throws Exception {
        // Given: 이미 제출한 사용자의 재제출 시도
        SurveySubmitRequest request = SurveySubmitRequest.builder()
                .userKey(TEST_USER_KEY)
                .answers(List.of())
                .build();

        doThrow(new kr.wisead.common.exception.BusinessException(
                kr.wisead.common.response.ErrorCode.INVALID_INPUT,
                "이미 설문에 참여하셨습니다."))
                .when(surveyService).submitSurvey(eq(TEST_EVENT_SEQ), any(SurveySubmitRequest.class));

        // When & Then
        mockMvc.perform(post("/api/survey/{eventSeq}/submit", TEST_EVENT_SEQ)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("이미 설문에 참여하셨습니다."));
    }
}
