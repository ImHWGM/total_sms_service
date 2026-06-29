package kr.wisead.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.wisead.domain.survey.dto.AnswerResponse;
import kr.wisead.domain.survey.dto.AnswerStatisticsResponse;
import kr.wisead.domain.survey.service.SurveyAnswerService;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 설문 응답 기록 확인 테스트
 *
 * 테스트 시나리오:
 * 1. 이벤트별 답변 수 조회
 * 2. 이벤트별 전체 답변 목록 조회
 * 3. 사용자별 답변 조회
 * 4. 문항별 답변 조회
 * 5. 문항별 응답 통계 조회
 * 6. 이벤트 전체 문항 통계 조회
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("설문 응답 기록 확인 테스트")
class SurveyAnswerRecordTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private SurveyAnswerService surveyAnswerService;

    @MockitoBean
    private UserIdResolver userIdResolver;

    private static final Integer TEST_EVENT_SEQ = 100;
    private static final Integer TEST_USER_SEQ = 1;
    private static final Integer TEST_QUESTION_SEQ = 10;
    private static final String TEST_USER_ID = "testuser01";

    private String userToken;

    @BeforeEach
    void setUp() {
        var userAuth = new UsernamePasswordAuthenticationToken(
                TEST_USER_ID, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        userToken = jwtTokenProvider.createAccessToken(userAuth, "테스트사용자");
        lenient().when(userIdResolver.fromJwtUsername(anyString())).thenReturn(1);
        lenient().when(userIdResolver.toUserId(any())).thenReturn(TEST_USER_ID);
    }

    @Test
    @Order(1)
    @DisplayName("1. 이벤트별 답변 수 조회")
    void getAnswerCount_Success() throws Exception {
        // Given
        when(surveyAnswerService.getAnswerCount(eq(TEST_EVENT_SEQ), anyString())).thenReturn(25);

        // When & Then
        mockMvc.perform(get("/api/survey/answers/count")
                        .header("Authorization", "Bearer " + userToken)
                        .param("eventSeq", String.valueOf(TEST_EVENT_SEQ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(25));

        verify(surveyAnswerService, times(1)).getAnswerCount(eq(TEST_EVENT_SEQ), anyString());
    }

    @Test
    @Order(2)
    @DisplayName("2. 이벤트별 전체 답변 목록 조회")
    void getAnswersByEvent_Success() throws Exception {
        // Given
        List<AnswerResponse> answers = List.of(
                AnswerResponse.builder()
                        .answerSeq(1)
                        .eventSeq(TEST_EVENT_SEQ)
                        .questionSeq(10)
                        .userSeq(1)
                        .questionType("MC")
                        .answer("5")
                        .regDate(LocalDateTime.now())
                        .build(),
                AnswerResponse.builder()
                        .answerSeq(2)
                        .eventSeq(TEST_EVENT_SEQ)
                        .questionSeq(11)
                        .userSeq(1)
                        .questionType("SA")
                        .answer("좋은 서비스입니다.")
                        .regDate(LocalDateTime.now())
                        .build()
        );

        when(surveyAnswerService.getAnswersByEvent(eq(TEST_EVENT_SEQ), anyString())).thenReturn(answers);

        // When & Then
        mockMvc.perform(get("/api/survey/answers")
                        .header("Authorization", "Bearer " + userToken)
                        .param("eventSeq", String.valueOf(TEST_EVENT_SEQ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].questionType").value("MC"))
                .andExpect(jsonPath("$.data[1].questionType").value("SA"));
    }

    @Test
    @Order(3)
    @DisplayName("3. 사용자별 답변 조회")
    void getAnswersByUser_Success() throws Exception {
        // Given
        List<AnswerResponse> userAnswers = List.of(
                AnswerResponse.builder()
                        .answerSeq(1)
                        .eventSeq(TEST_EVENT_SEQ)
                        .questionSeq(10)
                        .userSeq(TEST_USER_SEQ)
                        .questionType("MC")
                        .answer("매우 만족")
                        .build(),
                AnswerResponse.builder()
                        .answerSeq(2)
                        .eventSeq(TEST_EVENT_SEQ)
                        .questionSeq(11)
                        .userSeq(TEST_USER_SEQ)
                        .questionType("SA")
                        .answer("응답 속도가 빨라서 좋았습니다.")
                        .build()
        );

        when(surveyAnswerService.getAnswersByUser(eq(TEST_EVENT_SEQ), eq(TEST_USER_SEQ), anyString()))
                .thenReturn(userAnswers);

        // When & Then
        mockMvc.perform(get("/api/survey/answers/user")
                        .header("Authorization", "Bearer " + userToken)
                        .param("eventSeq", String.valueOf(TEST_EVENT_SEQ))
                        .param("userSeq", String.valueOf(TEST_USER_SEQ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @Order(4)
    @DisplayName("4. 문항별 답변 조회")
    void getAnswersByQuestion_Success() throws Exception {
        // Given
        List<AnswerResponse> questionAnswers = List.of(
                AnswerResponse.builder()
                        .answerSeq(1)
                        .eventSeq(TEST_EVENT_SEQ)
                        .questionSeq(TEST_QUESTION_SEQ)
                        .userSeq(1)
                        .answer("5")
                        .build(),
                AnswerResponse.builder()
                        .answerSeq(5)
                        .eventSeq(TEST_EVENT_SEQ)
                        .questionSeq(TEST_QUESTION_SEQ)
                        .userSeq(2)
                        .answer("4")
                        .build(),
                AnswerResponse.builder()
                        .answerSeq(9)
                        .eventSeq(TEST_EVENT_SEQ)
                        .questionSeq(TEST_QUESTION_SEQ)
                        .userSeq(3)
                        .answer("5")
                        .build()
        );

        when(surveyAnswerService.getAnswersByQuestion(eq(TEST_EVENT_SEQ), eq(TEST_QUESTION_SEQ), anyString()))
                .thenReturn(questionAnswers);

        // When & Then
        mockMvc.perform(get("/api/survey/answers/question")
                        .header("Authorization", "Bearer " + userToken)
                        .param("eventSeq", String.valueOf(TEST_EVENT_SEQ))
                        .param("questionSeq", String.valueOf(TEST_QUESTION_SEQ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    @Order(5)
    @DisplayName("5. 문항별 응답 통계 조회 - 객관식")
    void getQuestionStatistics_MultipleChoice_Success() throws Exception {
        // Given
        AnswerStatisticsResponse stats = AnswerStatisticsResponse.builder()
                .eventSeq(TEST_EVENT_SEQ)
                .questionSeq(TEST_QUESTION_SEQ)
                .questionType("MC")
                .questionTypeDetail("MCS")
                .respondentCount(50)
                .itemStatistics(List.of(
                        AnswerStatisticsResponse.ItemStatistics.builder()
                                .itemSeq(1)
                                .itemValue("5")
                                .itemName("매우 만족")
                                .selectCount(25)
                                .percentage(50.0)
                                .build(),
                        AnswerStatisticsResponse.ItemStatistics.builder()
                                .itemSeq(2)
                                .itemValue("4")
                                .itemName("만족")
                                .selectCount(15)
                                .percentage(30.0)
                                .build(),
                        AnswerStatisticsResponse.ItemStatistics.builder()
                                .itemSeq(3)
                                .itemValue("3")
                                .itemName("보통")
                                .selectCount(10)
                                .percentage(20.0)
                                .build()
                ))
                .build();

        when(surveyAnswerService.getQuestionStatistics(eq(TEST_EVENT_SEQ), eq(TEST_QUESTION_SEQ), anyString()))
                .thenReturn(stats);

        // When & Then
        mockMvc.perform(get("/api/survey/answers/statistics/question")
                        .header("Authorization", "Bearer " + userToken)
                        .param("eventSeq", String.valueOf(TEST_EVENT_SEQ))
                        .param("questionSeq", String.valueOf(TEST_QUESTION_SEQ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.respondentCount").value(50))
                .andExpect(jsonPath("$.data.itemStatistics").isArray())
                .andExpect(jsonPath("$.data.itemStatistics[0].selectCount").value(25))
                .andExpect(jsonPath("$.data.itemStatistics[0].percentage").value(50.0));
    }

    @Test
    @Order(6)
    @DisplayName("6. 문항별 응답 통계 조회 - 주관식")
    void getQuestionStatistics_ShortAnswer_Success() throws Exception {
        // Given
        AnswerStatisticsResponse stats = AnswerStatisticsResponse.builder()
                .eventSeq(TEST_EVENT_SEQ)
                .questionSeq(11)
                .questionType("SA")
                .questionTypeDetail("SAL")
                .respondentCount(30)
                .textAnswers(List.of(
                        "서비스가 좋습니다.",
                        "응답 속도 개선이 필요합니다.",
                        "전반적으로 만족합니다.",
                        "가격이 적당했으면 좋겠습니다."
                ))
                .build();

        when(surveyAnswerService.getQuestionStatistics(eq(TEST_EVENT_SEQ), eq(11), anyString()))
                .thenReturn(stats);

        // When & Then
        mockMvc.perform(get("/api/survey/answers/statistics/question")
                        .header("Authorization", "Bearer " + userToken)
                        .param("eventSeq", String.valueOf(TEST_EVENT_SEQ))
                        .param("questionSeq", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionType").value("SA"))
                .andExpect(jsonPath("$.data.respondentCount").value(30))
                .andExpect(jsonPath("$.data.textAnswers").isArray())
                .andExpect(jsonPath("$.data.textAnswers.length()").value(4));
    }

    @Test
    @Order(7)
    @DisplayName("7. 이벤트 전체 문항 통계 조회")
    void getEventStatistics_Success() throws Exception {
        // Given
        List<AnswerStatisticsResponse> allStats = List.of(
                AnswerStatisticsResponse.builder()
                        .eventSeq(TEST_EVENT_SEQ)
                        .questionSeq(10)
                        .questionType("MC")
                        .respondentCount(50)
                        .itemStatistics(List.of(
                                AnswerStatisticsResponse.ItemStatistics.builder()
                                        .itemSeq(1)
                                        .itemName("매우 만족")
                                        .selectCount(25)
                                        .percentage(50.0)
                                        .build()
                        ))
                        .build(),
                AnswerStatisticsResponse.builder()
                        .eventSeq(TEST_EVENT_SEQ)
                        .questionSeq(11)
                        .questionType("SA")
                        .respondentCount(45)
                        .textAnswers(List.of("좋습니다", "만족합니다"))
                        .build()
        );

        when(surveyAnswerService.getEventStatistics(eq(TEST_EVENT_SEQ), anyString())).thenReturn(allStats);

        // When & Then
        mockMvc.perform(get("/api/survey/answers/statistics")
                        .header("Authorization", "Bearer " + userToken)
                        .param("eventSeq", String.valueOf(TEST_EVENT_SEQ)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].questionType").value("MC"))
                .andExpect(jsonPath("$.data[1].questionType").value("SA"));
    }

    @Test
    @Order(8)
    @DisplayName("8. 존재하지 않는 이벤트의 답변 조회 - 빈 결과")
    void getAnswersByEvent_NotFound_EmptyList() throws Exception {
        // Given
        Integer nonExistentEventSeq = 99999;
        when(surveyAnswerService.getAnswersByEvent(eq(nonExistentEventSeq), anyString()))
                .thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/survey/answers")
                        .header("Authorization", "Bearer " + userToken)
                        .param("eventSeq", String.valueOf(nonExistentEventSeq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @Order(9)
    @DisplayName("9. 복수선택 문항 통계 조회")
    void getQuestionStatistics_MultiSelect_Success() throws Exception {
        // Given: 복수선택 문항 (체크박스)
        AnswerStatisticsResponse stats = AnswerStatisticsResponse.builder()
                .eventSeq(TEST_EVENT_SEQ)
                .questionSeq(12)
                .questionType("MC")
                .questionTypeDetail("MCM")  // Multiple Choice Multiple
                .respondentCount(40)
                .itemStatistics(List.of(
                        AnswerStatisticsResponse.ItemStatistics.builder()
                                .itemSeq(1)
                                .itemValue("1")
                                .itemName("품질")
                                .selectCount(35)
                                .percentage(87.5)  // 복수선택이므로 100% 초과 가능
                                .build(),
                        AnswerStatisticsResponse.ItemStatistics.builder()
                                .itemSeq(2)
                                .itemValue("2")
                                .itemName("가격")
                                .selectCount(28)
                                .percentage(70.0)
                                .build(),
                        AnswerStatisticsResponse.ItemStatistics.builder()
                                .itemSeq(3)
                                .itemValue("3")
                                .itemName("서비스")
                                .selectCount(32)
                                .percentage(80.0)
                                .build()
                ))
                .build();

        when(surveyAnswerService.getQuestionStatistics(eq(TEST_EVENT_SEQ), eq(12), anyString()))
                .thenReturn(stats);

        // When & Then
        mockMvc.perform(get("/api/survey/answers/statistics/question")
                        .header("Authorization", "Bearer " + userToken)
                        .param("eventSeq", String.valueOf(TEST_EVENT_SEQ))
                        .param("questionSeq", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionTypeDetail").value("MCM"))
                .andExpect(jsonPath("$.data.itemStatistics[0].percentage").value(87.5));
    }

    @Test
    @Order(10)
    @DisplayName("10. 비로그인 사용자의 답변 조회 - 인증 필요(PII 보호)이므로 401")
    void getAnswers_Unauthenticated_Unauthorized() throws Exception {
        // When & Then: 토큰 없으면 차단 (미인증 PII 노출 방지)
        mockMvc.perform(get("/api/survey/answers")
                        .param("eventSeq", String.valueOf(TEST_EVENT_SEQ)))
                .andExpect(status().isUnauthorized());

        verify(surveyAnswerService, never()).getAnswersByEvent(any(), any());
    }
}
