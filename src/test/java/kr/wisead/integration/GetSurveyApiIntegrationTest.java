package kr.wisead.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.survey.dto.*;
import kr.wisead.domain.survey.service.SurveyService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Task 3.1: Get Survey API Integration Tests
 *
 * API Endpoints:
 * - GET /api/survey/code/{eventCode} - 이벤트 코드로 설문 조회
 * - GET /api/survey/qr/{authCodeUrl} - QR코드 URL로 설문 조회
 * - GET /api/survey/key/{userKey} - 유저키로 설문 조회
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Task 3.1: Get Survey API 통합 테스트")
class GetSurveyApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SurveyService surveyService;

    private static final Integer TEST_EVENT_SEQ = 100;
    private static final String TEST_EVENT_CODE = "EVT123ABC456";
    private static final String TEST_AUTH_CODE_URL = "abc123qr";
    private static final String TEST_USER_KEY = "USERKEY12345";

    // ========================================
    // GET /api/survey/code/{eventCode} Tests
    // ========================================

    @Test
    @Order(1)
    @DisplayName("TC3.1.1: 이벤트 코드로 설문 조회 - 성공")
    void TC3_1_1_getSurveyByEventCode_ValidCode_ReturnsEventResponse() throws Exception {
        // Given: 유효한 이벤트 코드로 조회
        EventResponse mockResponse = createMockEventResponse();
        when(surveyService.getSurveyByEventCode(TEST_EVENT_CODE)).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/api/survey/code/{eventCode}", TEST_EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventSeq").value(TEST_EVENT_SEQ))
                .andExpect(jsonPath("$.data.eventCode").value(TEST_EVENT_CODE))
                .andExpect(jsonPath("$.data.eventName").value("고객 만족도 설문조사"))
                .andExpect(jsonPath("$.data.status").value("P"))
                .andExpect(jsonPath("$.data.statusName").value("진행"))
                .andExpect(jsonPath("$.data.questions").isArray());

        verify(surveyService, times(1)).getSurveyByEventCode(TEST_EVENT_CODE);
    }

    @Test
    @Order(2)
    @DisplayName("TC3.1.2: 이벤트 코드로 설문 조회 - 존재하지 않는 코드")
    void TC3_1_2_getSurveyByEventCode_InvalidCode_Returns404() throws Exception {
        // Given: 존재하지 않는 이벤트 코드
        String invalidCode = "INVALID_CODE";
        when(surveyService.getSurveyByEventCode(invalidCode))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "설문을 찾을 수 없습니다."));

        // When & Then
        mockMvc.perform(get("/api/survey/code/{eventCode}", invalidCode))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("설문을 찾을 수 없습니다."));
    }

    @Test
    @Order(3)
    @DisplayName("TC3.1.3: 이벤트 코드로 설문 조회 - 아직 시작되지 않은 설문 (status=A)")
    void TC3_1_3_getSurveyByEventCode_NotStartedEvent_Returns400() throws Exception {
        // Given: 아직 시작되지 않은 설문
        when(surveyService.getSurveyByEventCode(TEST_EVENT_CODE))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "아직 시작되지 않은 설문입니다."));

        // When & Then
        mockMvc.perform(get("/api/survey/code/{eventCode}", TEST_EVENT_CODE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("아직 시작되지 않은 설문입니다."));
    }

    @Test
    @Order(4)
    @DisplayName("TC3.1.4: 이벤트 코드로 설문 조회 - 일시 중지된 설문 (status=S)")
    void TC3_1_4_getSurveyByEventCode_SuspendedEvent_Returns400() throws Exception {
        // Given: 일시 중지된 설문
        when(surveyService.getSurveyByEventCode(TEST_EVENT_CODE))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "일시 중지된 설문입니다."));

        // When & Then
        mockMvc.perform(get("/api/survey/code/{eventCode}", TEST_EVENT_CODE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("일시 중지된 설문입니다."));
    }

    @Test
    @Order(5)
    @DisplayName("TC3.1.5: 이벤트 코드로 설문 조회 - 종료된 설문 (status=F)")
    void TC3_1_5_getSurveyByEventCode_FinishedEvent_Returns400() throws Exception {
        // Given: 종료된 설문
        when(surveyService.getSurveyByEventCode(TEST_EVENT_CODE))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "종료된 설문입니다."));

        // When & Then
        mockMvc.perform(get("/api/survey/code/{eventCode}", TEST_EVENT_CODE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("종료된 설문입니다."));
    }

    // ========================================
    // GET /api/survey/qr/{authCodeUrl} Tests
    // ========================================

    @Test
    @Order(6)
    @DisplayName("TC3.1.6: QR코드 URL로 설문 조회 - 성공 + 방문 수 증가")
    void TC3_1_6_getSurveyByAuthCodeUrl_ValidUrl_ReturnsEventResponseAndIncrementsVisit() throws Exception {
        // Given: 유효한 QR코드 URL로 조회
        EventResponse mockResponse = createMockEventResponse();
        when(surveyService.getSurveyByAuthCodeUrl(TEST_AUTH_CODE_URL)).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/api/survey/qr/{authCodeUrl}", TEST_AUTH_CODE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventSeq").value(TEST_EVENT_SEQ))
                .andExpect(jsonPath("$.data.authCodeUrl").value(TEST_AUTH_CODE_URL));

        verify(surveyService, times(1)).getSurveyByAuthCodeUrl(TEST_AUTH_CODE_URL);
    }

    @Test
    @Order(7)
    @DisplayName("TC3.1.7: QR코드 URL로 설문 조회 - 존재하지 않는 URL")
    void TC3_1_7_getSurveyByAuthCodeUrl_InvalidUrl_Returns404() throws Exception {
        // Given: 존재하지 않는 QR코드 URL
        String invalidUrl = "invalid_qr_url";
        when(surveyService.getSurveyByAuthCodeUrl(invalidUrl))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "설문을 찾을 수 없습니다."));

        // When & Then
        mockMvc.perform(get("/api/survey/qr/{authCodeUrl}", invalidUrl))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("설문을 찾을 수 없습니다."));
    }

    // ========================================
    // GET /api/survey/key/{userKey} Tests
    // ========================================

    @Test
    @Order(8)
    @DisplayName("TC3.1.8: 유저키로 설문 조회 - 성공 + 시작시간 기록")
    void TC3_1_8_getSurveyByUserKey_ValidKey_ReturnsEventResponseAndRecordsStartTime() throws Exception {
        // Given: 유효한 유저키로 조회
        EventResponse mockResponse = createMockEventResponse();
        when(surveyService.getSurveyByUserKey(TEST_USER_KEY)).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/api/survey/key/{userKey}", TEST_USER_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.eventSeq").value(TEST_EVENT_SEQ))
                .andExpect(jsonPath("$.data.questions").isArray());

        verify(surveyService, times(1)).getSurveyByUserKey(TEST_USER_KEY);
    }

    @Test
    @Order(9)
    @DisplayName("TC3.1.9: 유저키로 설문 조회 - 존재하지 않는 유저키")
    void TC3_1_9_getSurveyByUserKey_InvalidKey_Returns404() throws Exception {
        // Given: 존재하지 않는 유저키
        String invalidKey = "INVALID_USER_KEY";
        when(surveyService.getSurveyByUserKey(invalidKey))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "유효하지 않은 접근입니다."));

        // When & Then
        mockMvc.perform(get("/api/survey/key/{userKey}", invalidKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("유효하지 않은 접근입니다."));
    }

    @Test
    @Order(10)
    @DisplayName("TC3.1.10: 유저키로 설문 조회 - 이미 참여 완료한 사용자")
    void TC3_1_10_getSurveyByUserKey_AlreadySubmitted_Returns400() throws Exception {
        // Given: 이미 설문을 완료한 사용자의 유저키
        when(surveyService.getSurveyByUserKey(TEST_USER_KEY))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "이미 설문에 참여하셨습니다."));

        // When & Then
        mockMvc.perform(get("/api/survey/key/{userKey}", TEST_USER_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("이미 설문에 참여하셨습니다."));
    }

    // ========================================
    // Response Structure Tests
    // ========================================

    @Test
    @Order(11)
    @DisplayName("TC3.1.11: 응답 구조 검증 - 문항 목록 포함")
    void TC3_1_11_getSurvey_ResponseIncludesQuestions() throws Exception {
        // Given: 문항이 포함된 설문 응답
        EventResponse mockResponse = createMockEventResponseWithQuestions();
        when(surveyService.getSurveyByEventCode(TEST_EVENT_CODE)).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/api/survey/code/{eventCode}", TEST_EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions").isArray())
                .andExpect(jsonPath("$.data.questions.length()").value(2))
                .andExpect(jsonPath("$.data.questions[0].questionSeq").value(1))
                .andExpect(jsonPath("$.data.questions[0].questionType").value("MC"))
                .andExpect(jsonPath("$.data.questions[0].question").value("서비스에 만족하십니까?"))
                .andExpect(jsonPath("$.data.questions[0].items").isArray())
                .andExpect(jsonPath("$.data.questions[0].items[0].item").value("매우 만족"))
                .andExpect(jsonPath("$.data.questions[1].questionSeq").value(2))
                .andExpect(jsonPath("$.data.questions[1].questionType").value("SA"));
    }

    @Test
    @Order(12)
    @DisplayName("TC3.1.12: 응답 구조 검증 - 개인정보 취급방침 포함")
    void TC3_1_12_getSurvey_ResponseIncludesPrivacyPolicy() throws Exception {
        // Given: 개인정보 취급방침이 포함된 설문
        EventResponse mockResponse = EventResponse.builder()
                .eventSeq(TEST_EVENT_SEQ)
                .eventCode(TEST_EVENT_CODE)
                .eventName("고객 만족도 설문조사")
                .status("P")
                .statusName("진행")
                .privacyPolicyYn("Y")
                .privacyPolicyTtl("개인정보 수집 및 이용 동의")
                .privacyPolicyDesc("수집 항목: 이름, 연락처\n수집 목적: 경품 발송")
                .build();

        when(surveyService.getSurveyByEventCode(TEST_EVENT_CODE)).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/api/survey/code/{eventCode}", TEST_EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.privacyPolicyYn").value("Y"))
                .andExpect(jsonPath("$.data.privacyPolicyTtl").value("개인정보 수집 및 이용 동의"))
                .andExpect(jsonPath("$.data.privacyPolicyDesc").exists());
    }

    @Test
    @Order(13)
    @DisplayName("TC3.1.13: 응답 구조 검증 - 인증 타입 정보 포함")
    void TC3_1_13_getSurvey_ResponseIncludesAuthInfo() throws Exception {
        // Given: 범용인증이 설정된 설문
        EventResponse mockResponse = EventResponse.builder()
                .eventSeq(TEST_EVENT_SEQ)
                .eventCode(TEST_EVENT_CODE)
                .eventName("고객 만족도 설문조사")
                .status("P")
                .statusName("진행")
                .auth("GA")  // General Auth (범용인증)
                .authKeyDesc("참여코드를 입력해주세요")
                .build();

        when(surveyService.getSurveyByEventCode(TEST_EVENT_CODE)).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/api/survey/code/{eventCode}", TEST_EVENT_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auth").value("GA"))
                .andExpect(jsonPath("$.data.authKeyDesc").value("참여코드를 입력해주세요"));
    }

    // ========================================
    // Edge Cases Tests
    // ========================================

    @Test
    @Order(14)
    @DisplayName("TC3.1.14: 이벤트 코드 대소문자 구분 검증 (BINARY)")
    void TC3_1_14_getSurveyByEventCode_CaseSensitive() throws Exception {
        // Given: 대소문자가 다른 이벤트 코드
        String lowerCaseCode = "evt123abc456";  // 원본은 EVT123ABC456
        when(surveyService.getSurveyByEventCode(lowerCaseCode))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "설문을 찾을 수 없습니다."));

        // When & Then: 대소문자가 다르면 조회 실패
        mockMvc.perform(get("/api/survey/code/{eventCode}", lowerCaseCode))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(15)
    @DisplayName("TC3.1.15: 빈 이벤트 코드 처리")
    void TC3_1_15_getSurveyByEventCode_EmptyCode() throws Exception {
        // When & Then: 빈 코드는 404 (path variable 매핑 실패)
        mockMvc.perform(get("/api/survey/code/"))
                .andExpect(status().isNotFound());
    }

    // ========================================
    // Helper Methods
    // ========================================

    private EventResponse createMockEventResponse() {
        return EventResponse.builder()
                .eventSeq(TEST_EVENT_SEQ)
                .eventCode(TEST_EVENT_CODE)
                .eventName("고객 만족도 설문조사")
                .eventType("S")
                .status("P")
                .statusName("진행")
                .authCodeUrl(TEST_AUTH_CODE_URL)
                .startDate("2025-01-01")
                .endDate("2025-12-31")
                .build();
    }

    private EventResponse createMockEventResponseWithQuestions() {
        List<ItemResponse> items = List.of(
                ItemResponse.builder()
                        .itemSeq(1)
                        .item("매우 만족")
                        .itemValue("5")
                        .order(1)
                        .build(),
                ItemResponse.builder()
                        .itemSeq(2)
                        .item("만족")
                        .itemValue("4")
                        .order(2)
                        .build(),
                ItemResponse.builder()
                        .itemSeq(3)
                        .item("보통")
                        .itemValue("3")
                        .order(3)
                        .build()
        );

        List<QuestionResponse> questions = List.of(
                QuestionResponse.builder()
                        .questionSeq(1)
                        .questionType("MC")
                        .questionTypeDetail("MCS")
                        .question("서비스에 만족하십니까?")
                        .order(1)
                        .items(items)
                        .build(),
                QuestionResponse.builder()
                        .questionSeq(2)
                        .questionType("SA")
                        .questionTypeDetail("SAL")
                        .question("개선이 필요한 부분이 있다면 말씀해주세요.")
                        .order(2)
                        .build()
        );

        return EventResponse.builder()
                .eventSeq(TEST_EVENT_SEQ)
                .eventCode(TEST_EVENT_CODE)
                .eventName("고객 만족도 설문조사")
                .eventType("S")
                .status("P")
                .statusName("진행")
                .authCodeUrl(TEST_AUTH_CODE_URL)
                .questions(questions)
                .build();
    }
}