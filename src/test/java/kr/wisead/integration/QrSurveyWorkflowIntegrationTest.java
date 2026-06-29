package kr.wisead.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.wisead.domain.survey.dto.*;
import kr.wisead.domain.survey.service.EventService;
import kr.wisead.domain.survey.service.FrontAuthService;
import kr.wisead.domain.survey.service.SurveyAnswerService;
import kr.wisead.domain.survey.service.SurveyService;
import kr.wisead.common.util.UserIdResolver;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * QR 코드 설문 워크플로우 통합 테스트
 *
 * 테스트 시나리오:
 * 1. QR 간편인증을 사용하는 설문(이벤트) 생성
 * 2. authCodeUrl과 qrCodeImgPath 생성 확인
 * 3. QR 코드 스캔 시 설문 조회 (authCodeUrl로 접근)
 * 4. QR 사용자 자동 생성
 * 5. 설문 응답 제출
 * 6. 응답 기록 확인
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("QR 코드 설문 워크플로우 통합 테스트")
class QrSurveyWorkflowIntegrationTest {

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
    private FrontAuthService frontAuthService;

    @MockitoBean
    private SurveyAnswerService surveyAnswerService;

    @MockitoBean
    private UserIdResolver userIdResolver;

    private static final String TEST_USER_ID = "testuser01";
    private static final Integer TEST_EVENT_SEQ = 100;
    private static final String TEST_EVENT_CODE = "QREVT123ABC";
    private static final String TEST_AUTH_CODE_URL = "AbCdEfGhIjKlMnOpQrSt";
    private static final String TEST_QR_CODE_IMG_PATH = "http://localhost:8080/files/qrcode/test-uuid.png";
    private static final String TEST_USER_KEY = "QRUSERKEY12345678901";

    private String userToken;

    @BeforeEach
    void setUp() {
        // 일반 사용자 토큰 생성
        var userAuth = new UsernamePasswordAuthenticationToken(
                TEST_USER_ID, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        userToken = jwtTokenProvider.createAccessToken(userAuth, "테스트사용자");
        lenient().when(userIdResolver.fromJwtUsername(anyString())).thenReturn(1);
        lenient().when(userIdResolver.toUserId(any())).thenReturn(TEST_USER_ID);
    }

    @Test
    @Order(1)
    @DisplayName("1. QR 간편인증 사용 설문(이벤트) 생성")
    void createEventWithQrCode_Success() throws Exception {
        // Given: QR 간편인증 사용하는 설문 생성 요청
        EventRequest request = EventRequest.builder()
                .eventName("QR 코드 설문조사")
                .eventType("S")
                .eventDesc("QR 코드를 스캔하여 참여하는 설문조사입니다.")
                .startDate("2025-01-01")
                .endDate("2025-12-31")
                .status("A")
                .auth("None")  // 인증 없음 (QR로 바로 접근)
                .qrCode("Y")   // QR 간편인증 사용
                .privacyPolicyYn("Y")
                .privacyPolicyTtl("개인정보 수집 및 이용 동의")
                .privacyPolicyDesc("수집 항목: 이름, 연락처")
                .questions(List.of(
                        QuestionRequest.builder()
                                .questionType("MC")
                                .questionTypeDetail("MCS")
                                .question("이 서비스를 추천하시겠습니까?")
                                .order(1)
                                .items(List.of(
                                        ItemRequest.builder()
                                                .item("예")
                                                .itemValue("Y")
                                                .order(1)
                                                .build(),
                                        ItemRequest.builder()
                                                .item("아니오")
                                                .itemValue("N")
                                                .order(2)
                                                .build()
                                ))
                                .build(),
                        QuestionRequest.builder()
                                .questionType("SA")
                                .questionTypeDetail("SAL")
                                .question("의견이 있으시면 남겨주세요.")
                                .order(2)
                                .build()
                ))
                .build();

        // Mock: QR 코드가 포함된 설문 생성 응답
        EventResponse mockResponse = EventResponse.builder()
                .eventSeq(TEST_EVENT_SEQ)
                .eventCode(TEST_EVENT_CODE)
                .eventName("QR 코드 설문조사")
                .eventType("S")
                .status("A")
                .qrCode("Y")
                .authCodeUrl(TEST_AUTH_CODE_URL)
                .qrCodeImgPath(TEST_QR_CODE_IMG_PATH)
                .build();

        when(eventService.createEvent(anyString(), any(EventRequest.class), any(), any(), any(), any()))
                .thenReturn(mockResponse);

        // When & Then
        MvcResult result = mockMvc.perform(post("/api/event")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventSeq").value(TEST_EVENT_SEQ))
                .andExpect(jsonPath("$.data.qrCode").value("Y"))
                .andExpect(jsonPath("$.data.authCodeUrl").value(TEST_AUTH_CODE_URL))
                .andExpect(jsonPath("$.data.qrCodeImgPath").value(TEST_QR_CODE_IMG_PATH))
                .andReturn();

        verify(eventService, times(1)).createEvent(anyString(), any(EventRequest.class), any(), any(), any(), any());

        // authCodeUrl이 20자리인지 확인
        assertThat(TEST_AUTH_CODE_URL).hasSize(20);
    }

    @Test
    @Order(2)
    @DisplayName("2. QR 코드 이미지 URL 형식 검증")
    void verifyQrCodeImageUrl_Format() throws Exception {
        // Given: 생성된 QR 코드 이미지 경로
        String qrCodeImgPath = TEST_QR_CODE_IMG_PATH;

        // Then: URL 형식 검증
        assertThat(qrCodeImgPath)
                .startsWith("http")
                .contains("/files/qrcode/")
                .endsWith(".png");
    }

    @Test
    @Order(3)
    @DisplayName("3. QR 코드 스캔으로 설문 조회 (authCodeUrl)")
    void getSurveyByAuthCodeUrl_Success() throws Exception {
        // Given: QR 코드에 담긴 authCodeUrl로 설문 조회
        EventResponse mockResponse = EventResponse.builder()
                .eventSeq(TEST_EVENT_SEQ)
                .eventCode(TEST_EVENT_CODE)
                .eventName("QR 코드 설문조사")
                .eventType("S")
                .status("P")  // 진행중
                .qrCode("Y")
                .questions(List.of(
                        QuestionResponse.builder()
                                .questionSeq(1)
                                .questionType("MC")
                                .question("이 서비스를 추천하시겠습니까?")
                                .items(List.of(
                                        ItemResponse.builder()
                                                .itemSeq(1)
                                                .item("예")
                                                .itemValue("Y")
                                                .build(),
                                        ItemResponse.builder()
                                                .itemSeq(2)
                                                .item("아니오")
                                                .itemValue("N")
                                                .build()
                                ))
                                .build(),
                        QuestionResponse.builder()
                                .questionSeq(2)
                                .questionType("SA")
                                .question("의견이 있으시면 남겨주세요.")
                                .build()
                ))
                .build();

        when(surveyService.getSurveyByAuthCodeUrl(TEST_AUTH_CODE_URL))
                .thenReturn(mockResponse);

        // When & Then (인증 불필요 - 공개 API)
        mockMvc.perform(get("/api/survey/qr/{authCodeUrl}", TEST_AUTH_CODE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventSeq").value(TEST_EVENT_SEQ))
                .andExpect(jsonPath("$.data.qrCode").value("Y"))
                .andExpect(jsonPath("$.data.questions").isArray())
                .andExpect(jsonPath("$.data.questions.length()").value(2));

        verify(surveyService, times(1)).getSurveyByAuthCodeUrl(TEST_AUTH_CODE_URL);
    }

    @Test
    @Order(4)
    @DisplayName("4. QR 접근 시 사용자 자동 생성")
    void createQrUser_Success() throws Exception {
        // Given: QR 코드로 접근한 사용자 자동 생성
        SurveyUserResponse mockResponse = SurveyUserResponse.builder()
                .userSeq(1)
                .eventSeq(TEST_EVENT_SEQ)
                .userKey(TEST_USER_KEY)
                .status("미참여")
                .build();

        when(frontAuthService.createQrUser(TEST_AUTH_CODE_URL))
                .thenReturn(mockResponse);

        // When & Then (인증 불필요 - 공개 API)
        mockMvc.perform(post("/api/front/auth/qr/user")
                        .param("authCodeUrl", TEST_AUTH_CODE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userKey").value(TEST_USER_KEY))
                .andExpect(jsonPath("$.data.eventSeq").value(TEST_EVENT_SEQ));

        verify(frontAuthService, times(1)).createQrUser(TEST_AUTH_CODE_URL);
    }

    @Test
    @Order(5)
    @DisplayName("5. QR 사용자 설문 응답 제출")
    void submitSurveyByQrUser_Success() throws Exception {
        // Given: QR로 생성된 사용자가 설문 응답 제출
        SurveySubmitRequest request = SurveySubmitRequest.builder()
                .userKey(TEST_USER_KEY)
                .userName("QR응답자")
                .userPhone("01098765432")
                .answers(List.of(
                        SurveySubmitRequest.AnswerRequest.builder()
                                .questionSeq(1)
                                .questionType("MC")
                                .questionTypeDetail("MCS")
                                .itemSeq(1)
                                .answer("Y")
                                .build(),
                        SurveySubmitRequest.AnswerRequest.builder()
                                .questionSeq(2)
                                .questionType("SA")
                                .questionTypeDetail("SAL")
                                .answer("QR 코드로 편하게 참여했습니다.")
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
    @Order(6)
    @DisplayName("6. QR 설문 응답 기록 확인")
    void getAnswerCountAfterQrSubmission_Success() throws Exception {
        // Given: QR 설문 응답 후 기록 확인
        when(surveyAnswerService.getAnswerCount(eq(TEST_EVENT_SEQ), anyString())).thenReturn(2);

        // When & Then
        mockMvc.perform(get("/api/survey/answers/count")
                        .header("Authorization", "Bearer " + userToken)
                        .param("eventSeq", String.valueOf(TEST_EVENT_SEQ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(2));

        verify(surveyAnswerService, times(1)).getAnswerCount(eq(TEST_EVENT_SEQ), anyString());
    }

    @Test
    @Order(7)
    @DisplayName("7. QR 방문 수 증가 확인 (통계)")
    void getQrCodeVisitCount_Success() throws Exception {
        // Given: QR 설문 통계 조회
        SurveyStatisticsResponse mockStats = SurveyStatisticsResponse.builder()
                .eventSeq(TEST_EVENT_SEQ)
                .eventName("QR 코드 설문조사")
                .totalParticipants(5)
                .completedParticipants(3)
                .absentees(0)
                .lurkers(2)  // QR 스캔만 하고 응답 안 한 사용자
                .responseRate(60.0)
                .build();

        when(eventService.getStatistics(TEST_EVENT_SEQ)).thenReturn(mockStats);

        // When & Then
        mockMvc.perform(get("/api/event/{eventSeq}/statistics", TEST_EVENT_SEQ)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalParticipants").value(5))
                .andExpect(jsonPath("$.data.completedParticipants").value(3))
                .andExpect(jsonPath("$.data.lurkers").value(2));
    }

    @Test
    @Order(8)
    @DisplayName("8. 존재하지 않는 authCodeUrl로 설문 조회 시 실패")
    void getSurveyByInvalidAuthCodeUrl_Fail() throws Exception {
        // Given: 존재하지 않는 authCodeUrl
        String invalidAuthCodeUrl = "InvalidAuthCodeUrl123";

        when(surveyService.getSurveyByAuthCodeUrl(invalidAuthCodeUrl))
                .thenThrow(new kr.wisead.common.exception.BusinessException(
                        kr.wisead.common.response.ErrorCode.RESOURCE_NOT_FOUND,
                        "설문을 찾을 수 없습니다."));

        // When & Then
        mockMvc.perform(get("/api/survey/qr/{authCodeUrl}", invalidAuthCodeUrl))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("설문을 찾을 수 없습니다."));
    }

    @Test
    @Order(9)
    @DisplayName("9. 종료된 이벤트의 QR 코드로 접근 시 실패")
    void getSurveyByExpiredEventQrCode_Fail() throws Exception {
        // Given: 종료된 이벤트의 authCodeUrl
        String expiredAuthCodeUrl = "ExpiredEventQrCode12";

        when(surveyService.getSurveyByAuthCodeUrl(expiredAuthCodeUrl))
                .thenThrow(new kr.wisead.common.exception.BusinessException(
                        kr.wisead.common.response.ErrorCode.INVALID_INPUT,
                        "종료된 설문입니다."));

        // When & Then
        mockMvc.perform(get("/api/survey/qr/{authCodeUrl}", expiredAuthCodeUrl))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("종료된 설문입니다."));
    }

    @Test
    @Order(10)
    @DisplayName("10. QR 미사용 설문 생성 시 authCodeUrl, qrCodeImgPath 없음")
    void createEventWithoutQrCode_NoQrFields() throws Exception {
        // Given: QR 미사용 설문 생성 요청
        EventRequest request = EventRequest.builder()
                .eventName("일반 설문조사")
                .eventType("S")
                .eventDesc("QR 코드를 사용하지 않는 일반 설문조사입니다.")
                .startDate("2025-01-01")
                .endDate("2025-12-31")
                .status("A")
                .auth("Phone")  // 휴대폰 인증
                .qrCode("N")    // QR 미사용
                .privacyPolicyYn("Y")
                .privacyPolicyTtl("개인정보 수집 동의")
                .privacyPolicyDesc("수집 항목: 연락처")
                .build();

        // Mock: QR 관련 필드가 null인 응답
        EventResponse mockResponse = EventResponse.builder()
                .eventSeq(101)
                .eventCode("NORMALEVT123")
                .eventName("일반 설문조사")
                .eventType("S")
                .status("A")
                .qrCode("N")
                .authCodeUrl(null)
                .qrCodeImgPath(null)
                .build();

        when(eventService.createEvent(anyString(), any(EventRequest.class), any(), any(), any(), any()))
                .thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/event")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.qrCode").value("N"))
                .andExpect(jsonPath("$.data.authCodeUrl").doesNotExist())
                .andExpect(jsonPath("$.data.qrCodeImgPath").doesNotExist());
    }

    @Test
    @Order(11)
    @DisplayName("11. 기존 이벤트에 QR 간편인증 활성화 시 QR 코드 생성")
    void updateEventToEnableQrCode_Success() throws Exception {
        // Given: QR 미사용에서 사용으로 변경
        EventRequest request = EventRequest.builder()
                .eventName("QR 코드 설문조사")
                .eventType("S")
                .eventDesc("QR 코드를 새로 활성화합니다.")
                .startDate("2025-01-01")
                .endDate("2025-12-31")
                .status("A")
                .auth("None")
                .qrCode("Y")  // QR 사용으로 변경
                .privacyPolicyYn("Y")
                .privacyPolicyTtl("개인정보 수집 동의")
                .privacyPolicyDesc("수집 항목: 이름")
                .build();

        // Mock: QR 코드가 새로 생성된 응답
        EventResponse mockResponse = EventResponse.builder()
                .eventSeq(TEST_EVENT_SEQ)
                .eventCode(TEST_EVENT_CODE)
                .eventName("QR 코드 설문조사")
                .eventType("S")
                .status("A")
                .qrCode("Y")
                .authCodeUrl("NewAuthCodeUrl12345")
                .qrCodeImgPath("http://localhost:8080/files/qrcode/new-uuid.png")
                .build();

        when(eventService.updateEvent(eq(TEST_EVENT_SEQ), any(EventRequest.class), anyString(), any(), any(), any(), any()))
                .thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(put("/api/event/{eventSeq}", TEST_EVENT_SEQ)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.qrCode").value("Y"))
                .andExpect(jsonPath("$.data.authCodeUrl").exists())
                .andExpect(jsonPath("$.data.qrCodeImgPath").exists());

        verify(eventService, times(1)).updateEvent(eq(TEST_EVENT_SEQ), any(EventRequest.class), anyString(), any(), any(), any(), any());
    }
}
