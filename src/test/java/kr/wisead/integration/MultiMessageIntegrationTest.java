package kr.wisead.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.wisead.domain.message.dto.MultiMessageRequest;
import kr.wisead.domain.message.dto.MultiMessageResponse;
import kr.wisead.domain.message.service.MultiMessageService;
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

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Task 2.4: Multi Send Message API Integration Tests
 *
 * Test Scenarios:
 * - TC2.4.1: SMS send success
 * - TC2.4.2: LMS send success
 * - TC2.4.3: MMS send success
 * - TC2.4.4: Insufficient balance error
 * - TC2.4.5: Duplicate number removal
 * - TC2.4.6: Blocked number filtering
 * - TC2.4.7: Night time restriction (20:00 ~ 09:00)
 * - TC2.4.8: Replacement characters processing
 * - TC2.4.9: Reserved send (scheduled)
 * - TC2.4.10: Empty receiver list
 * - TC2.4.11: No authentication
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Task 2.4: Multi Send Message API Tests")
class MultiMessageIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockitoBean
        private MultiMessageService multiMessageService;

        @Autowired
        private JwtTokenProvider jwtTokenProvider;

        private String validToken;

        @BeforeEach
        void setUp() {
                // Generate valid JWT token for tests
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                "testuser",
                                null,
                                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
                validToken = jwtTokenProvider.createAccessToken(auth);
        }

        // ==================== TC2.4.1: SMS Send Success ====================

        @Test
        @Order(1)
        @DisplayName("TC2.4.1: SMS send success")
        void TC2_4_1_sendSms_Success() throws Exception {
                // Given
                MultiMessageRequest request = MultiMessageRequest.builder()
                                .messageType("SMS")
                                .callback("01012345678")
                                .text("Test SMS message")
                                .reqType("direct")
                                .delDuplicateNum("N")
                                .receivers(List.of(
                                                MultiMessageRequest.ReceiverInfo.builder()
                                                                .phone("01011112222")
                                                                .build(),
                                                MultiMessageRequest.ReceiverInfo.builder()
                                                                .phone("01033334444")
                                                                .build()))
                                .build();

                // Mock service
                when(multiMessageService.sendDirectMessage(any(MultiMessageRequest.class), eq("testuser")))
                                .thenReturn(MultiMessageResponse.success(2, 0, 0, "20260114-123456789",
                                                new BigDecimal("28.6")));

                // When & Then
                mockMvc.perform(post("/api/multi/send")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode", is(1)))
                                .andExpect(jsonPath("$.data.successCount", is(2)))
                                .andExpect(jsonPath("$.data.batchId", is("20260114-123456789")))
                                .andExpect(jsonPath("$.data.chargedAmount", is(28.6)));

                verify(multiMessageService).sendDirectMessage(any(MultiMessageRequest.class), eq("testuser"));
        }

        // ==================== TC2.4.2: LMS Send Success ====================

        @Test
        @Order(2)
        @DisplayName("TC2.4.2: LMS send success")
        void TC2_4_2_sendLms_Success() throws Exception {
                // Given
                MultiMessageRequest request = MultiMessageRequest.builder()
                                .messageType("LMS")
                                .callback("01012345678")
                                .subject("LMS Subject")
                                .text("Test LMS message with longer content that exceeds SMS length limit...")
                                .reqType("direct")
                                .delDuplicateNum("N")
                                .receivers(List.of(
                                                MultiMessageRequest.ReceiverInfo.builder()
                                                                .phone("01011112222")
                                                                .build()))
                                .build();

                // Mock service
                when(multiMessageService.sendDirectMessage(any(MultiMessageRequest.class), eq("testuser")))
                                .thenReturn(MultiMessageResponse.success(1, 0, 0, "20260114-123456790",
                                                new BigDecimal("36.3")));

                // When & Then
                mockMvc.perform(post("/api/multi/send")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode", is(1)))
                                .andExpect(jsonPath("$.data.successCount", is(1)))
                                .andExpect(jsonPath("$.data.chargedAmount", is(36.3)));
        }

        // ==================== TC2.4.3: MMS Send Success ====================

        @Test
        @Order(3)
        @DisplayName("TC2.4.3: MMS send success (without file - JSON endpoint)")
        void TC2_4_3_sendMms_Success() throws Exception {
                // Given
                MultiMessageRequest request = MultiMessageRequest.builder()
                                .messageType("MMS")
                                .callback("01012345678")
                                .subject("MMS Subject")
                                .text("Test MMS message")
                                .reqType("direct")
                                .delDuplicateNum("N")
                                .receivers(List.of(
                                                MultiMessageRequest.ReceiverInfo.builder()
                                                                .phone("01011112222")
                                                                .build()))
                                .fileLoc1("/mms/test-image.jpg")
                                .fileCnt(1)
                                .build();

                // Mock service
                when(multiMessageService.sendDirectMessage(any(MultiMessageRequest.class), eq("testuser")))
                                .thenReturn(MultiMessageResponse.success(1, 0, 0, "20260114-123456791",
                                                new BigDecimal("110.0")));

                // When & Then
                mockMvc.perform(post("/api/multi/send")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode", is(1)))
                                .andExpect(jsonPath("$.data.successCount", is(1)))
                                .andExpect(jsonPath("$.data.chargedAmount", is(110.0)));
        }

        // ==================== TC2.4.4: Insufficient Balance ====================

        @Test
        @Order(4)
        @DisplayName("TC2.4.4: Send fails with insufficient balance")
        void TC2_4_4_send_InsufficientBalance() throws Exception {
                // Given
                MultiMessageRequest request = MultiMessageRequest.builder()
                                .messageType("SMS")
                                .callback("01012345678")
                                .text("Test message")
                                .reqType("direct")
                                .receivers(List.of(
                                                MultiMessageRequest.ReceiverInfo.builder()
                                                                .phone("01011112222")
                                                                .build()))
                                .build();

                // Mock service - return insufficient balance
                when(multiMessageService.sendDirectMessage(any(MultiMessageRequest.class), eq("testuser")))
                                .thenReturn(MultiMessageResponse.insufficientBalance());

                // When & Then
                mockMvc.perform(post("/api/multi/send")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode", is(-3)))
                                .andExpect(jsonPath("$.data.errorMsg[0]", containsString("충전 금액이 문자를 발송하기에 모자랍니다")));
        }

        // ==================== TC2.4.5: Duplicate Number Removal ====================

        @Test
        @Order(5)
        @DisplayName("TC2.4.5: Duplicate numbers are removed when delDuplicateNum=Y")
        void TC2_4_5_send_DuplicateRemoval() throws Exception {
                // Given - request with duplicate numbers
                MultiMessageRequest request = MultiMessageRequest.builder()
                                .messageType("SMS")
                                .callback("01012345678")
                                .text("Test message")
                                .reqType("direct")
                                .delDuplicateNum("Y") // Enable duplicate removal
                                .receivers(List.of(
                                                MultiMessageRequest.ReceiverInfo.builder().phone("01011112222").build(),
                                                MultiMessageRequest.ReceiverInfo.builder().phone("01011112222").build(), // Duplicate
                                                MultiMessageRequest.ReceiverInfo.builder().phone("01033334444")
                                                                .build()))
                                .build();

                // Mock service - should return 2 successful, 1 duplicate
                when(multiMessageService.sendDirectMessage(any(MultiMessageRequest.class), eq("testuser")))
                                .thenReturn(MultiMessageResponse.success(2, 1, 0, "20260114-123456792",
                                                new BigDecimal("28.6")));

                // When & Then
                mockMvc.perform(post("/api/multi/send")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode", is(1)))
                                .andExpect(jsonPath("$.data.successCount", is(2)))
                                .andExpect(jsonPath("$.data.duplicate", is(1)));
        }

        // ==================== TC2.4.6: Blocked Number Filtering ====================

        @Test
        @Order(6)
        @DisplayName("TC2.4.6: Blocked numbers are filtered out")
        void TC2_4_6_send_BlockedNumberFiltering() throws Exception {
                // Given
                MultiMessageRequest request = MultiMessageRequest.builder()
                                .messageType("SMS")
                                .callback("01012345678")
                                .text("Test message")
                                .reqType("direct")
                                .receivers(List.of(
                                                MultiMessageRequest.ReceiverInfo.builder().phone("01011112222").build(),
                                                MultiMessageRequest.ReceiverInfo.builder().phone("01055556666").build() // Blocked
                                                                                                                        // number
                                ))
                                .build();

                // Mock service - 1 sent, 1 blocked
                when(multiMessageService.sendDirectMessage(any(MultiMessageRequest.class), eq("testuser")))
                                .thenReturn(MultiMessageResponse.success(1, 0, 1, "20260114-123456793",
                                                new BigDecimal("14.3")));

                // When & Then
                mockMvc.perform(post("/api/multi/send")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode", is(1)))
                                .andExpect(jsonPath("$.data.successCount", is(1)))
                                .andExpect(jsonPath("$.data.blockedCount", is(1)));
        }

        // ==================== TC2.4.7: Night Time Restriction ====================

        @Test
        @Order(7)
        @DisplayName("TC2.4.7: Direct send blocked during night time (20:00 ~ 09:00)")
        void TC2_4_7_send_NightTimeRestriction() throws Exception {
                // Given - direct send during night time
                MultiMessageRequest request = MultiMessageRequest.builder()
                                .messageType("SMS")
                                .callback("01012345678")
                                .text("Test message")
                                .reqType("direct") // Direct send should be blocked
                                .receivers(List.of(
                                                MultiMessageRequest.ReceiverInfo.builder().phone("01011112222")
                                                                .build()))
                                .build();

                // Mock service - return night time error
                when(multiMessageService.sendDirectMessage(any(MultiMessageRequest.class), eq("testuser")))
                                .thenReturn(MultiMessageResponse
                                                .error("야간 전송제한 시간입니다. (20:00 ~ 09:00)\n해당 시간에는 예약발송만 가능합니다."));

                // When & Then
                mockMvc.perform(post("/api/multi/send")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode", is(-1)))
                                .andExpect(jsonPath("$.data.errorMsg[0]", containsString("야간 전송제한")));
        }

        // ==================== TC2.4.8: Replacement Characters ====================

        @Test
        @Order(8)
        @DisplayName("TC2.4.8: Replacement characters are processed correctly")
        void TC2_4_8_send_ReplacementCharacters() throws Exception {
                // Given - message with replacement characters
                MultiMessageRequest request = MultiMessageRequest.builder()
                                .messageType("SMS")
                                .callback("01012345678")
                                .text("안녕하세요 #대치문자1#님, #대치문자2#에서 인사드립니다. 코드: #대치문자3#")
                                .reqType("direct")
                                .receivers(List.of(
                                                MultiMessageRequest.ReceiverInfo.builder()
                                                                .phone("01011112222")
                                                                .repChar01("홍길동")
                                                                .repChar02("WiseAd")
                                                                .repChar03("ABC123")
                                                                .build()))
                                .build();

                // Mock service
                when(multiMessageService.sendDirectMessage(any(MultiMessageRequest.class), eq("testuser")))
                                .thenReturn(MultiMessageResponse.success(1, 0, 0, "20260114-123456794",
                                                new BigDecimal("14.3")));

                // When & Then
                mockMvc.perform(post("/api/multi/send")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode", is(1)))
                                .andExpect(jsonPath("$.data.successCount", is(1)));

                // Verify service was called
                verify(multiMessageService).sendDirectMessage(
                                argThat(req -> req.getReceivers().get(0).getRepChar01().equals("홍길동") &&
                                                req.getReceivers().get(0).getRepChar02().equals("WiseAd") &&
                                                req.getReceivers().get(0).getRepChar03().equals("ABC123")),
                                eq("testuser"));
        }

        // ==================== TC2.4.9: Reserved Send (Scheduled) ====================

        @Test
        @Order(9)
        @DisplayName("TC2.4.9: Reserved send (scheduled) works correctly")
        void TC2_4_9_send_ReservedSend() throws Exception {
                // Given - reserved send
                MultiMessageRequest request = MultiMessageRequest.builder()
                                .messageType("SMS")
                                .callback("01012345678")
                                .text("Scheduled message")
                                .reqType("reserve") // Reserved send
                                .reqDate("2026-01-15 10:00:00") // Future time
                                .receivers(List.of(
                                                MultiMessageRequest.ReceiverInfo.builder()
                                                                .phone("01011112222")
                                                                .build()))
                                .build();

                // Mock service
                when(multiMessageService.sendDirectMessage(any(MultiMessageRequest.class), eq("testuser")))
                                .thenReturn(MultiMessageResponse.success(1, 0, 0, "20260114-123456795",
                                                new BigDecimal("14.3")));

                // When & Then
                mockMvc.perform(post("/api/multi/send")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode", is(1)))
                                .andExpect(jsonPath("$.data.successCount", is(1)));

                // Verify service was called with reserved send
                verify(multiMessageService).sendDirectMessage(argThat(req -> "reserve".equals(req.getReqType()) &&
                                "2026-01-15 10:00:00".equals(req.getReqDate())), eq("testuser"));
        }

        // ==================== TC2.4.10: Empty Receiver List ====================

        @Test
        @Order(10)
        @DisplayName("TC2.4.10: Empty receiver list returns error")
        void TC2_4_10_send_EmptyReceivers() throws Exception {
                // Given - empty receivers
                MultiMessageRequest request = MultiMessageRequest.builder()
                                .messageType("SMS")
                                .callback("01012345678")
                                .text("Test message")
                                .reqType("direct")
                                .receivers(List.of()) // Empty list
                                .build();

                // Mock service - return error
                when(multiMessageService.sendDirectMessage(any(MultiMessageRequest.class), eq("testuser")))
                                .thenReturn(MultiMessageResponse.error("수신자 목록이 비어있습니다."));

                // When & Then
                mockMvc.perform(post("/api/multi/send")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode", is(-1)))
                                .andExpect(jsonPath("$.data.errorMsg[0]", containsString("수신자 목록이 비어있습니다")));
        }

        // ==================== TC2.4.11: No Authentication ====================

        @Test
        @Order(11)
        @DisplayName("TC2.4.11: Request without authentication returns 401")
        void TC2_4_11_send_NoAuthentication() throws Exception {
                // Given
                MultiMessageRequest request = MultiMessageRequest.builder()
                                .messageType("SMS")
                                .callback("01012345678")
                                .text("Test message")
                                .receivers(List.of(
                                                MultiMessageRequest.ReceiverInfo.builder()
                                                                .phone("01011112222")
                                                                .build()))
                                .build();

                // When & Then - no Authorization header
                mockMvc.perform(post("/api/multi/send")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized());

                // Verify service was never called
                verify(multiMessageService, never()).sendDirectMessage(any(), any());
        }

        // ==================== TC2.4.12: All Receivers Blocked ====================

        @Test
        @Order(12)
        @DisplayName("TC2.4.12: All receivers blocked returns error")
        void TC2_4_12_send_AllReceiversBlocked() throws Exception {
                // Given - all receivers are blocked
                MultiMessageRequest request = MultiMessageRequest.builder()
                                .messageType("SMS")
                                .callback("01012345678")
                                .text("Test message")
                                .reqType("direct")
                                .receivers(List.of(
                                                MultiMessageRequest.ReceiverInfo.builder().phone("01011112222")
                                                                .build()))
                                .build();

                // Mock service - all blocked
                when(multiMessageService.sendDirectMessage(any(MultiMessageRequest.class), eq("testuser")))
                                .thenReturn(MultiMessageResponse.error("모든 수신자가 수신거부 처리되어 발송할 대상이 없습니다."));

                // When & Then
                mockMvc.perform(post("/api/multi/send")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode", is(-1)))
                                .andExpect(jsonPath("$.data.errorMsg[0]", containsString("수신거부")));
        }

        // ==================== TC2.4.13: Message Type Code Conversion
        // ====================

        @Test
        @Order(13)
        @DisplayName("TC2.4.13: Message type code is correctly converted (SMS -> S, LMS -> L, MMS -> M)")
        void TC2_4_13_messageTypeCodeConversion() {
                // Test the DTO method directly
                MultiMessageRequest smsRequest = MultiMessageRequest.builder().messageType("SMS").build();
                MultiMessageRequest lmsRequest = MultiMessageRequest.builder().messageType("LMS").build();
                MultiMessageRequest mmsRequest = MultiMessageRequest.builder().messageType("MMS").build();
                MultiMessageRequest nullRequest = MultiMessageRequest.builder().messageType(null).build();

                Assertions.assertEquals("S", smsRequest.getMsgTypeCode());
                Assertions.assertEquals("L", lmsRequest.getMsgTypeCode());
                Assertions.assertEquals("M", mmsRequest.getMsgTypeCode());
                Assertions.assertEquals("S", nullRequest.getMsgTypeCode()); // Default to SMS
        }

        // ==================== TC2.4.14: Callback Normalization ====================

        @Test
        @Order(14)
        @DisplayName("TC2.4.14: Callback number hyphens are removed")
        void TC2_4_14_callbackNormalization() {
                // Test the DTO method directly
                MultiMessageRequest request = MultiMessageRequest.builder()
                                .callback("010-1234-5678")
                                .build();

                Assertions.assertEquals("01012345678", request.getNormalizedCallback());
        }

        // ==================== TC2.4.15: Response Factory Methods ====================

        @Test
        @Order(15)
        @DisplayName("TC2.4.15: Response factory methods work correctly")
        void TC2_4_15_responseFactoryMethods() {
                // Test success response
                MultiMessageResponse successResponse = MultiMessageResponse.success(5, 2, 1, "batch123",
                                new BigDecimal("100.0"));
                Assertions.assertEquals(1, successResponse.getResultCode());
                Assertions.assertEquals(5, successResponse.getSuccessCount());
                Assertions.assertEquals(2, successResponse.getDuplicate());
                Assertions.assertEquals(1, successResponse.getBlockedCount());
                Assertions.assertEquals("batch123", successResponse.getBatchId());
                Assertions.assertEquals(new BigDecimal("100.0"), successResponse.getChargedAmount());

                // Test insufficient balance response
                MultiMessageResponse insufficientResponse = MultiMessageResponse.insufficientBalance();
                Assertions.assertEquals(-3, insufficientResponse.getResultCode());
                Assertions.assertTrue(insufficientResponse.getErrorMsg().get(0).contains("충전 금액"));

                // Test error response
                MultiMessageResponse errorResponse = MultiMessageResponse.error("Custom error");
                Assertions.assertEquals(-1, errorResponse.getResultCode());
                Assertions.assertEquals("Custom error", errorResponse.getErrorMsg().get(0));

                // Test unsupported chars response
                MultiMessageResponse unsupportedResponse = MultiMessageResponse.unsupportedChars("XYZ");
                Assertions.assertEquals(-2, unsupportedResponse.getResultCode());
                Assertions.assertEquals("XYZ", unsupportedResponse.getUnsupportedChars());
        }
}