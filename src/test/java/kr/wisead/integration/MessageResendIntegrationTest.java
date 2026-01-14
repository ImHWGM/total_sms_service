ㅡpackage kr.wisead.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.wisead.domain.message.dto.ResendRequest;
import kr.wisead.domain.message.dto.ResendResponse;
import kr.wisead.domain.message.service.MessageSendService;
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

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Task 2.2: Resend Message API Integration Tests
 *
 * Test Scenarios:
 * - TC2.2.1: Single resend success (use original content)
 * - TC2.2.2: Single resend success (new content)
 * - TC2.2.3: Single resend failure - user not found
 * - TC2.2.4: Batch resend success
 * - TC2.2.5: Batch resend partial success
 * - TC2.2.6: Duplicate resend success
 * - TC2.2.7: Resend with insufficient balance (PENDING - requires balance integration)
 * - TC2.2.8: Resend without authentication
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Task 2.2: Resend Message API Tests")
class MessageResendIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MessageSendService messageSendService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private String validToken;

    @BeforeEach
    void setUp() {
        // Generate valid JWT token for tests
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "testuser",
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        validToken = jwtTokenProvider.generateAccessToken(auth);
    }

    // ==================== TC2.2.1: Single Resend - Original Content ====================

    @Test
    @Order(1)
    @DisplayName("TC2.2.1: Single resend success - use original content")
    void TC2_2_1_resendSingle_UseOriginal_Success() throws Exception {
        // Given
        ResendRequest request = ResendRequest.builder()
                .userSeq(123)
                .callback("01012345678")
                .useOriginalContent(true)
                .build();

        // Mock service
        when(messageSendService.resendSurveyMessage(
                eq(123),
                isNull(),
                isNull(),
                eq("01012345678"),
                eq(true),
                anyString()
        )).thenReturn(1001);

        // When & Then
        mockMvc.perform(post("/api/message/send/resend")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(1001));

        // Verify
        verify(messageSendService, times(1)).resendSurveyMessage(
                eq(123), isNull(), isNull(), eq("01012345678"), eq(true), anyString());
    }

    // ==================== TC2.2.2: Single Resend - New Content ====================

    @Test
    @Order(2)
    @DisplayName("TC2.2.2: Single resend success - new content")
    void TC2_2_2_resendSingle_NewContent_Success() throws Exception {
        // Given
        ResendRequest request = ResendRequest.builder()
                .userSeq(123)
                .subject("New Subject")
                .text("New message content with #유저키#")
                .callback("01012345678")
                .useOriginalContent(false)
                .build();

        // Mock service
        when(messageSendService.resendSurveyMessage(
                eq(123),
                eq("New Subject"),
                eq("New message content with #유저키#"),
                eq("01012345678"),
                eq(false),
                anyString()
        )).thenReturn(1002);

        // When & Then
        mockMvc.perform(post("/api/message/send/resend")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(1002));

        // Verify
        verify(messageSendService, times(1)).resendSurveyMessage(
                eq(123),
                eq("New Subject"),
                eq("New message content with #유저키#"),
                eq("01012345678"),
                eq(false),
                anyString());
    }

    // ==================== TC2.2.3: Single Resend - User Not Found ====================

    @Test
    @Order(3)
    @DisplayName("TC2.2.3: Single resend failure - survey user not found")
    void TC2_2_3_resendSingle_UserNotFound_Returns404() throws Exception {
        // Given
        ResendRequest request = ResendRequest.builder()
                .userSeq(99999)  // Non-existent user
                .callback("01012345678")
                .useOriginalContent(true)
                .build();

        // Mock service to throw exception
        when(messageSendService.resendSurveyMessage(
                eq(99999),
                isNull(),
                isNull(),
                eq("01012345678"),
                eq(true),
                anyString()
        )).thenThrow(new kr.wisead.common.exception.BusinessException(
                kr.wisead.common.response.ErrorCode.RESOURCE_NOT_FOUND,
                "Survey user not found"));

        // When & Then
        mockMvc.perform(post("/api/message/send/resend")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("R001"));
    }

    // ==================== TC2.2.4: Batch Resend Success ====================

    @Test
    @Order(4)
    @DisplayName("TC2.2.4: Batch resend success - all succeed")
    void TC2_2_4_resendBatch_Success() throws Exception {
        // Given
        ResendRequest request = ResendRequest.builder()
                .userSeqList(List.of(123, 124, 125))
                .subject("Batch subject")
                .text("Batch message #유저키#")
                .callback("01012345678")
                .useOriginalContent(false)
                .build();

        // Mock service
        when(messageSendService.resendSurveyMessageBatch(
                eq(List.of(123, 124, 125)),
                eq("Batch subject"),
                eq("Batch message #유저키#"),
                eq("01012345678"),
                eq(false),
                anyString()
        )).thenReturn(ResendResponse.success(3));

        // When & Then
        mockMvc.perform(post("/api/message/send/resend/batch")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(1))
                .andExpect(jsonPath("$.data.successCount").value(3))
                .andExpect(jsonPath("$.data.message").value(containsString("3")));

        // Verify
        verify(messageSendService, times(1)).resendSurveyMessageBatch(
                eq(List.of(123, 124, 125)),
                eq("Batch subject"),
                eq("Batch message #유저키#"),
                eq("01012345678"),
                eq(false),
                anyString());
    }

    // ==================== TC2.2.5: Batch Resend Partial Success ====================

    @Test
    @Order(5)
    @DisplayName("TC2.2.5: Batch resend partial success - some failures")
    void TC2_2_5_resendBatch_PartialSuccess() throws Exception {
        // Given - One invalid userSeq in the list
        ResendRequest request = ResendRequest.builder()
                .userSeqList(List.of(123, 99999, 125))  // 99999 doesn't exist
                .subject("Batch subject")
                .text("Batch message #유저키#")
                .callback("01012345678")
                .useOriginalContent(false)
                .build();

        // Mock service - partial success
        when(messageSendService.resendSurveyMessageBatch(
                eq(List.of(123, 99999, 125)),
                eq("Batch subject"),
                eq("Batch message #유저키#"),
                eq("01012345678"),
                eq(false),
                anyString()
        )).thenReturn(ResendResponse.partial(2, 1, List.of("99999")));

        // When & Then
        mockMvc.perform(post("/api/message/send/resend/batch")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(2))  // Partial success
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failCount").value(1))
                .andExpect(jsonPath("$.data.failedNumbers[0]").value("99999"));
    }

    // ==================== TC2.2.6: Duplicate Resend Success ====================

    @Test
    @Order(6)
    @DisplayName("TC2.2.6: Duplicate resend success")
    void TC2_2_6_resendDuplicate_Success() throws Exception {
        // Given
        ResendRequest request = ResendRequest.builder()
                .eventSeq(100)
                .eventCode("TESTCODE123")
                .subject("Duplicate resend")
                .text("Duplicate message #유저키#")
                .callback("01012345678")
                .duplicateReceivers(List.of(
                        ResendRequest.DuplicateReceiver.builder()
                                .phone("01011112222")
                                .userSeq(123)
                                .userKey("userkey123")
                                .build(),
                        ResendRequest.DuplicateReceiver.builder()
                                .phone("01033334444")
                                .userSeq(124)
                                .userKey("userkey456")
                                .repChar01("Hong Gildong")
                                .build()
                ))
                .build();

        // Mock service
        when(messageSendService.resendToDuplicates(any(ResendRequest.class), anyString()))
                .thenReturn(ResendResponse.success(2));

        // When & Then
        mockMvc.perform(post("/api/message/send/resend/duplicate")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(1))
                .andExpect(jsonPath("$.data.successCount").value(2));

        // Verify
        verify(messageSendService, times(1)).resendToDuplicates(any(ResendRequest.class), anyString());
    }

    // ==================== TC2.2.7: Insufficient Balance (PENDING) ====================

    @Test
    @Order(7)
    @DisplayName("TC2.2.7: Resend with insufficient balance - 402 Payment Required")
    @Disabled("PENDING: Balance check not yet implemented in MessageSendService")
    void TC2_2_7_resend_InsufficientBalance_Returns402() throws Exception {
        // Given
        ResendRequest request = ResendRequest.builder()
                .userSeq(123)
                .callback("01012345678")
                .useOriginalContent(true)
                .build();

        // TODO: When balance integration is implemented:
        // Mock balance service to return insufficient balance
        // when(balanceService.hasSufficientBalance(anyString(), any(BigDecimal.class)))
        //         .thenReturn(false);

        // When & Then
        mockMvc.perform(post("/api/message/send/resend")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPaymentRequired())  // 402
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("P001"));
    }

    // ==================== TC2.2.8: No Authentication ====================

    @Test
    @Order(8)
    @DisplayName("TC2.2.8: Resend without authentication - 401 Unauthorized")
    void TC2_2_8_resend_NoAuth_Returns401() throws Exception {
        // Given
        ResendRequest request = ResendRequest.builder()
                .userSeq(123)
                .callback("01012345678")
                .useOriginalContent(true)
                .build();

        // When & Then - No Authorization header
        mockMvc.perform(post("/api/message/send/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        // Verify - Service should NOT be called
        verify(messageSendService, never()).resendSurveyMessage(
                anyInt(), anyString(), anyString(), anyString(), anyBoolean(), anyString());
    }

    // ==================== TC2.2.9: Send New to Duplicates ====================

    @Test
    @Order(9)
    @DisplayName("TC2.2.9: Send new content to duplicates success")
    void TC2_2_9_sendNewToDuplicates_Success() throws Exception {
        // Given
        ResendRequest request = ResendRequest.builder()
                .eventSeq(100)
                .eventCode("TESTCODE123")
                .subject("New content to duplicates")
                .text("Brand new message #유저키#")
                .callback("01012345678")
                .duplicateReceivers(List.of(
                        ResendRequest.DuplicateReceiver.builder()
                                .phone("01011112222")
                                .userSeq(123)
                                .userKey("userkey123")
                                .build()
                ))
                .build();

        // Mock service
        when(messageSendService.sendNewToDuplicates(any(ResendRequest.class), anyString()))
                .thenReturn(ResendResponse.success(1));

        // When & Then
        mockMvc.perform(post("/api/message/send/duplicate/new")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(1))
                .andExpect(jsonPath("$.data.successCount").value(1));

        // Verify
        verify(messageSendService, times(1)).sendNewToDuplicates(any(ResendRequest.class), anyString());
    }

    // ==================== TC2.2.10: Empty Receiver List ====================

    @Test
    @Order(10)
    @DisplayName("TC2.2.10: Duplicate resend with empty receivers - returns failure")
    void TC2_2_10_resendDuplicate_EmptyReceivers_ReturnsFailure() throws Exception {
        // Given - Empty duplicateReceivers list
        ResendRequest request = ResendRequest.builder()
                .eventSeq(100)
                .eventCode("TESTCODE123")
                .subject("Empty test")
                .text("Empty message #유저키#")
                .callback("01012345678")
                .duplicateReceivers(List.of())  // Empty list
                .build();

        // Mock service - returns fail response for empty list
        when(messageSendService.resendToDuplicates(any(ResendRequest.class), anyString()))
                .thenReturn(ResendResponse.fail("No receivers to resend"));

        // When & Then
        mockMvc.perform(post("/api/message/send/resend/duplicate")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))  // API call succeeds
                .andExpect(jsonPath("$.data.resultCode").value(-1))  // Business failure
                .andExpect(jsonPath("$.data.message").value(containsString("No receivers")));
    }

    // ==================== TC2.2.11: Replacement Characters ====================

    @Test
    @Order(11)
    @DisplayName("TC2.2.11: Duplicate resend with replacement characters")
    void TC2_2_11_resendDuplicate_WithReplacementChars_Success() throws Exception {
        // Given
        ResendRequest request = ResendRequest.builder()
                .eventSeq(100)
                .eventCode("TESTCODE123")
                .subject("Replacement test")
                .text("Dear #대치문자1#, your code is #대치문자2#. #유저키#")
                .callback("01012345678")
                .duplicateReceivers(List.of(
                        ResendRequest.DuplicateReceiver.builder()
                                .phone("01011112222")
                                .userSeq(123)
                                .userKey("userkey123")
                                .repChar01("Kim Cheolsu")
                                .repChar02("ABC123")
                                .build()
                ))
                .build();

        // Mock service
        when(messageSendService.resendToDuplicates(any(ResendRequest.class), anyString()))
                .thenReturn(ResendResponse.success(1));

        // When & Then
        mockMvc.perform(post("/api/message/send/resend/duplicate")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.successCount").value(1));

        // Verify - check that the service was called with request containing repChar values
        verify(messageSendService).resendToDuplicates(argThat(req ->
                req.getDuplicateReceivers() != null &&
                req.getDuplicateReceivers().size() == 1 &&
                "Kim Cheolsu".equals(req.getDuplicateReceivers().get(0).getRepChar01()) &&
                "ABC123".equals(req.getDuplicateReceivers().get(0).getRepChar02())
        ), anyString());
    }
}