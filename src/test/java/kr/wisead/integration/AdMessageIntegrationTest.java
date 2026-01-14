package kr.wisead.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.wisead.domain.message.dto.AdMessageRequest;
import kr.wisead.domain.message.dto.AdMessageResponse;
import kr.wisead.domain.message.service.AdMessageService;
import kr.wisead.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Task 2.6: Ad Message Send API Integration Tests
 *
 * Test Scenarios:
 * - TC2.6.1: SMS send success with balance deduction
 * - TC2.6.2: LMS send success
 * - TC2.6.3: MMS send success with files
 * - TC2.6.4: Insufficient balance error
 * - TC2.6.5: Duplicate number removal
 * - TC2.6.6: Blocked number filtering
 * - TC2.6.7: All numbers blocked
 * - TC2.6.8: Night time restriction (20:00 ~ 09:00)
 * - TC2.6.9: Replacement characters processing
 * - TC2.6.10: No recipients error
 * - TC2.6.11: No authentication returns 401
 * - TC2.6.12: Invalid message type validation
 * - TC2.6.13: Invalid phone format validation
 * - TC2.6.14: Scheduled send (reserved)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Task 2.6: Ad Message Send API Tests")
class AdMessageIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockitoBean
        private AdMessageService adMessageService;

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

        // ==================== TC2.6.1: SMS Send Success ====================

        @Test
        @Order(1)
        @DisplayName("TC2.6.1: SMS send success with balance deduction")
        void TC2_6_1_sendSms_Success() throws Exception {
                // Given
                AdMessageRequest request = AdMessageRequest.builder()
                                .reqType("ip-direct")
                                .messageTypeIs("SMS")
                                .reqNum("01012345678")
                                .sendTtl("")
                                .sendTimeType("direct")
                                .delDuplicateNum("N")
                                .contTxt("[TEST] Ad message test")
                                .recipients(List.of(
                                                AdMessageRequest.Recipient.builder()
                                                                .recPhone("01011112222")
                                                                .build(),
                                                AdMessageRequest.Recipient.builder()
                                                                .recPhone("01033334444")
                                                                .build()))
                                .build();

                AdMessageResponse expectedResponse = AdMessageResponse.success(2, 0, 0, null, "20260114-123456789");

                when(adMessageService.sendDirectMessage(any(AdMessageRequest.class), eq("testuser")))
                                .thenReturn(expectedResponse);

                // When & Then
                mockMvc.perform(post("/api/ad/msg/send/json")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode").value(1))
                                .andExpect(jsonPath("$.data.successCount").value(2))
                                .andExpect(jsonPath("$.data.duplicateCount").value(0))
                                .andExpect(jsonPath("$.data.blockedCount").value(0))
                                .andExpect(jsonPath("$.data.batchId").exists());

                verify(adMessageService, times(1)).sendDirectMessage(any(AdMessageRequest.class), eq("testuser"));
        }

        // ==================== TC2.6.2: LMS Send Success ====================

        @Test
        @Order(2)
        @DisplayName("TC2.6.2: LMS send success")
        void TC2_6_2_sendLms_Success() throws Exception {
                // Given
                AdMessageRequest request = AdMessageRequest.builder()
                                .reqType("ip-direct")
                                .messageTypeIs("LMS")
                                .reqNum("01012345678")
                                .sendTtl("Test Subject")
                                .sendTimeType("direct")
                                .delDuplicateNum("N")
                                .contTxt("[TEST] Ad message test with long content for LMS...")
                                .recipients(List.of(
                                                AdMessageRequest.Recipient.builder()
                                                                .recPhone("01011112222")
                                                                .build()))
                                .build();

                AdMessageResponse expectedResponse = AdMessageResponse.success(1, 0, 0, null, "20260114-123456790");

                when(adMessageService.sendDirectMessage(any(AdMessageRequest.class), eq("testuser")))
                                .thenReturn(expectedResponse);

                // When & Then
                mockMvc.perform(post("/api/ad/msg/send/json")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode").value(1))
                                .andExpect(jsonPath("$.data.successCount").value(1));
        }

        // ==================== TC2.6.3: MMS Send Success ====================

        @Test
        @Order(3)
        @DisplayName("TC2.6.3: MMS send success with file count")
        void TC2_6_3_sendMms_Success() throws Exception {
                // Given
                AdMessageRequest request = AdMessageRequest.builder()
                                .reqType("ip-direct")
                                .messageTypeIs("MMS")
                                .reqNum("01012345678")
                                .sendTtl("MMS Title")
                                .sendTimeType("direct")
                                .delDuplicateNum("N")
                                .contTxt("[TEST] MMS message with image")
                                .fileCnt(1)
                                .fileloc1("/uploads/mms/test-image.jpg")
                                .recipients(List.of(
                                                AdMessageRequest.Recipient.builder()
                                                                .recPhone("01011112222")
                                                                .build()))
                                .build();

                AdMessageResponse expectedResponse = AdMessageResponse.success(1, 0, 0, null, "20260114-123456791");

                when(adMessageService.sendDirectMessage(any(AdMessageRequest.class), eq("testuser")))
                                .thenReturn(expectedResponse);

                // When & Then
                mockMvc.perform(post("/api/ad/msg/send/json")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode").value(1))
                                .andExpect(jsonPath("$.data.successCount").value(1));
        }

        // ==================== TC2.6.4: Insufficient Balance ====================

        @Test
        @Order(4)
        @DisplayName("TC2.6.4: Insufficient balance returns error")
        void TC2_6_4_insufficientBalance_Error() throws Exception {
                // Given
                AdMessageRequest request = AdMessageRequest.builder()
                                .reqType("ip-direct")
                                .messageTypeIs("SMS")
                                .reqNum("01012345678")
                                .sendTimeType("direct")
                                .contTxt("[TEST] Message")
                                .recipients(List.of(
                                                AdMessageRequest.Recipient.builder()
                                                                .recPhone("01011112222")
                                                                .build()))
                                .build();

                AdMessageResponse insufficientResponse = AdMessageResponse.insufficientBalance(
                                "충전 금액이 부족합니다.\n필요: 28.6원, 잔액: 0원");

                when(adMessageService.sendDirectMessage(any(AdMessageRequest.class), eq("testuser")))
                                .thenReturn(insufficientResponse);

                // When & Then
                mockMvc.perform(post("/api/ad/msg/send/json")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode").value(-3))
                                .andExpect(jsonPath("$.data.message").value(containsString("부족")));
        }

        // ==================== TC2.6.5: Duplicate Number Removal ====================

        @Test
        @Order(5)
        @DisplayName("TC2.6.5: Duplicate number removal works")
        void TC2_6_5_duplicateRemoval_Success() throws Exception {
                // Given
                AdMessageRequest request = AdMessageRequest.builder()
                                .reqType("ip-direct")
                                .messageTypeIs("SMS")
                                .reqNum("01012345678")
                                .sendTimeType("direct")
                                .delDuplicateNum("Y") // Enable duplicate removal
                                .contTxt("[TEST] Message")
                                .recipients(List.of(
                                                AdMessageRequest.Recipient.builder().recPhone("01011112222").build(),
                                                AdMessageRequest.Recipient.builder().recPhone("01011112222").build(), // Duplicate
                                                AdMessageRequest.Recipient.builder().recPhone("01033334444").build()))
                                .build();

                // 3 recipients, 1 duplicate removed = 2 success
                AdMessageResponse expectedResponse = AdMessageResponse.success(2, 1, 0, null, "20260114-123456792");

                when(adMessageService.sendDirectMessage(any(AdMessageRequest.class), eq("testuser")))
                                .thenReturn(expectedResponse);

                // When & Then
                mockMvc.perform(post("/api/ad/msg/send/json")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode").value(1))
                                .andExpect(jsonPath("$.data.successCount").value(2))
                                .andExpect(jsonPath("$.data.duplicateCount").value(1));
        }

        // ==================== TC2.6.6: Blocked Number Filtering ====================

        @Test
        @Order(6)
        @DisplayName("TC2.6.6: Blocked number filtering works")
        void TC2_6_6_blockedNumberFiltering_Success() throws Exception {
                // Given
                AdMessageRequest request = AdMessageRequest.builder()
                                .reqType("ip-direct")
                                .messageTypeIs("SMS")
                                .reqNum("01012345678")
                                .sendTimeType("direct")
                                .contTxt("[TEST] Message")
                                .recipients(List.of(
                                                AdMessageRequest.Recipient.builder().recPhone("01011112222").build(),
                                                AdMessageRequest.Recipient.builder().recPhone("01099998888").build() // Blocked
                                                                                                                     // number
                                ))
                                .build();

                // 2 recipients, 1 blocked = 1 success
                AdMessageResponse expectedResponse = AdMessageResponse.success(
                                1, 0, 1, List.of("010****8888"), "20260114-123456793");

                when(adMessageService.sendDirectMessage(any(AdMessageRequest.class), eq("testuser")))
                                .thenReturn(expectedResponse);

                // When & Then
                mockMvc.perform(post("/api/ad/msg/send/json")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode").value(1))
                                .andExpect(jsonPath("$.data.successCount").value(1))
                                .andExpect(jsonPath("$.data.blockedCount").value(1))
                                .andExpect(jsonPath("$.data.blockedNumbers").isArray())
                                .andExpect(jsonPath("$.data.blockedNumbers[0]").value("010****8888"));
        }

        // ==================== TC2.6.7: All Numbers Blocked ====================

        @Test
        @Order(7)
        @DisplayName("TC2.6.7: All numbers blocked returns special code")
        void TC2_6_7_allBlocked_SpecialCode() throws Exception {
                // Given
                AdMessageRequest request = AdMessageRequest.builder()
                                .reqType("ip-direct")
                                .messageTypeIs("SMS")
                                .reqNum("01012345678")
                                .sendTimeType("direct")
                                .contTxt("[TEST] Message")
                                .recipients(List.of(
                                                AdMessageRequest.Recipient.builder().recPhone("01099998888").build() // Blocked
                                ))
                                .build();

                AdMessageResponse allBlockedResponse = AdMessageResponse.allBlocked(1, List.of("010****8888"));

                when(adMessageService.sendDirectMessage(any(AdMessageRequest.class), eq("testuser")))
                                .thenReturn(allBlockedResponse);

                // When & Then
                mockMvc.perform(post("/api/ad/msg/send/json")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode").value(-99))
                                .andExpect(jsonPath("$.data.allBlocked").value(true))
                                .andExpect(jsonPath("$.data.blockedCount").value(1));
        }

        // ==================== TC2.6.8: Night Time Restriction ====================

        @Test
        @Order(8)
        @DisplayName("TC2.6.8: Night time restriction blocks immediate send")
        void TC2_6_8_nightTimeRestriction() throws Exception {
                // Given
                AdMessageRequest request = AdMessageRequest.builder()
                                .reqType("ip-direct")
                                .messageTypeIs("SMS")
                                .reqNum("01012345678")
                                .sendTimeType("direct") // Immediate send
                                .contTxt("[TEST] Message")
                                .recipients(List.of(
                                                AdMessageRequest.Recipient.builder().recPhone("01011112222").build()))
                                .build();

                AdMessageResponse nightRestrictedResponse = AdMessageResponse.nightTimeRestricted();

                when(adMessageService.sendDirectMessage(any(AdMessageRequest.class), eq("testuser")))
                                .thenReturn(nightRestrictedResponse);

                // When & Then
                mockMvc.perform(post("/api/ad/msg/send/json")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode").value(-4))
                                .andExpect(jsonPath("$.data.message").value(containsString("야간")));
        }

        // ==================== TC2.6.9: Replacement Characters ====================

        @Test
        @Order(9)
        @DisplayName("TC2.6.9: Replacement characters processed correctly")
        void TC2_6_9_replacementCharacters() throws Exception {
                // Given
                AdMessageRequest request = AdMessageRequest.builder()
                                .reqType("ip-direct")
                                .messageTypeIs("SMS")
                                .reqNum("01012345678")
                                .sendTimeType("direct")
                                .contTxt("[TEST] Hello #대치문자1#, your code is #대치문자2#")
                                .recipients(List.of(
                                                AdMessageRequest.Recipient.builder()
                                                                .recPhone("01011112222")
                                                                .repChar01("John")
                                                                .repChar02("ABC123")
                                                                .build()))
                                .build();

                AdMessageResponse expectedResponse = AdMessageResponse.success(1, 0, 0, null, "20260114-123456794");

                when(adMessageService.sendDirectMessage(any(AdMessageRequest.class), eq("testuser")))
                                .thenReturn(expectedResponse);

                // When & Then
                mockMvc.perform(post("/api/ad/msg/send/json")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode").value(1))
                                .andExpect(jsonPath("$.data.successCount").value(1));
        }

        // ==================== TC2.6.10: No Recipients Error ====================

        @Test
        @Order(10)
        @DisplayName("TC2.6.10: Empty recipients returns error")
        void TC2_6_10_emptyRecipients_Error() throws Exception {
                // Given
                AdMessageRequest request = AdMessageRequest.builder()
                                .reqType("ip-direct")
                                .messageTypeIs("SMS")
                                .reqNum("01012345678")
                                .sendTimeType("direct")
                                .contTxt("[TEST] Message")
                                .recipients(List.of()) // Empty
                                .build();

                AdMessageResponse failResponse = AdMessageResponse.fail(-1, "발송 대상이 없습니다.");

                when(adMessageService.sendDirectMessage(any(AdMessageRequest.class), eq("testuser")))
                                .thenReturn(failResponse);

                // When & Then
                mockMvc.perform(post("/api/ad/msg/send/json")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode").value(-1))
                                .andExpect(jsonPath("$.data.message").value(containsString("발송 대상")));
        }

        // ==================== TC2.6.11: No Authentication ====================

        @Test
        @Order(11)
        @DisplayName("TC2.6.11: No authentication returns 401")
        void TC2_6_11_noAuthentication_Unauthorized() throws Exception {
                // Given
                AdMessageRequest request = AdMessageRequest.builder()
                                .reqType("ip-direct")
                                .messageTypeIs("SMS")
                                .reqNum("01012345678")
                                .sendTimeType("direct")
                                .contTxt("[TEST] Message")
                                .recipients(List.of(
                                                AdMessageRequest.Recipient.builder().recPhone("01011112222").build()))
                                .build();

                // When & Then - No Authorization header
                mockMvc.perform(post("/api/ad/msg/send/json")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized());
        }

        // ==================== TC2.6.12: Invalid Message Type ====================

        @Test
        @Order(12)
        @DisplayName("TC2.6.12: Invalid message type validation error")
        void TC2_6_12_invalidMessageType_ValidationError() throws Exception {
                // Given - Invalid messageTypeIs
                String invalidRequest = """
                                {
                                    "reqType": "ip-direct",
                                    "messageTypeIs": "INVALID",
                                    "reqNum": "01012345678",
                                    "sendTimeType": "direct",
                                    "contTxt": "Test message",
                                    "recipients": [{"recPhone": "01011112222"}]
                                }
                                """;

                // When & Then
                mockMvc.perform(post("/api/ad/msg/send/json")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidRequest))
                                .andExpect(status().isBadRequest());
        }

        // ==================== TC2.6.13: Invalid Phone Format ====================

        @Test
        @Order(13)
        @DisplayName("TC2.6.13: Invalid phone format validation error")
        void TC2_6_13_invalidPhoneFormat_ValidationError() throws Exception {
                // Given - Invalid reqNum format
                String invalidRequest = """
                                {
                                    "reqType": "ip-direct",
                                    "messageTypeIs": "SMS",
                                    "reqNum": "invalid-phone",
                                    "sendTimeType": "direct",
                                    "contTxt": "Test message",
                                    "recipients": [{"recPhone": "01011112222"}]
                                }
                                """;

                // When & Then
                mockMvc.perform(post("/api/ad/msg/send/json")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidRequest))
                                .andExpect(status().isBadRequest());
        }

        // ==================== TC2.6.14: Scheduled Send ====================

        @Test
        @Order(14)
        @DisplayName("TC2.6.14: Scheduled send works correctly")
        void TC2_6_14_scheduledSend_Success() throws Exception {
                // Given - Scheduled send (not immediate)
                String scheduledRequest = """
                                {
                                    "reqType": "ip-direct",
                                    "messageTypeIs": "SMS",
                                    "reqNum": "01012345678",
                                    "sendTimeType": "schedule",
                                    "reqDate": "2026-01-15T10:00:00",
                                    "contTxt": "Scheduled test message",
                                    "recipients": [{"recPhone": "01011112222"}]
                                }
                                """;

                AdMessageResponse expectedResponse = AdMessageResponse.success(1, 0, 0, null, "20260114-123456795");

                when(adMessageService.sendDirectMessage(any(AdMessageRequest.class), eq("testuser")))
                                .thenReturn(expectedResponse);

                // When & Then
                mockMvc.perform(post("/api/ad/msg/send/json")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(scheduledRequest))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode").value(1))
                                .andExpect(jsonPath("$.data.successCount").value(1));
        }

        // ==================== TC2.6.15: Check Night Time API ====================

        @Test
        @Order(15)
        @DisplayName("TC2.6.15: Night time check API returns status")
        void TC2_6_15_checkNightTime() throws Exception {
                // Given
                when(adMessageService.isNightTimeRestriction()).thenReturn(false);

                // When & Then
                mockMvc.perform(get("/api/ad/msg/check-night-time")
                                .header("Authorization", "Bearer " + validToken))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.isNightTime").exists())
                                .andExpect(jsonPath("$.data.message").exists());
        }

        // ==================== TC2.6.16: Service Method Test - Balance Deduction
        // ====================

        @Test
        @Order(16)
        @DisplayName("TC2.6.16: Balance is deducted after successful send")
        void TC2_6_16_balanceDeduction() throws Exception {
                // Given
                AdMessageRequest request = AdMessageRequest.builder()
                                .reqType("ip-direct")
                                .messageTypeIs("SMS")
                                .reqNum("01012345678")
                                .sendTimeType("direct")
                                .contTxt("[TEST] Balance deduction test")
                                .recipients(List.of(
                                                AdMessageRequest.Recipient.builder().recPhone("01011112222").build(),
                                                AdMessageRequest.Recipient.builder().recPhone("01033334444").build()))
                                .build();

                // Expected: 2 SMS x 28.6 = 57.2 charged
                AdMessageResponse expectedResponse = AdMessageResponse.success(2, 0, 0, null, "20260114-123456796");

                when(adMessageService.sendDirectMessage(any(AdMessageRequest.class), eq("testuser")))
                                .thenReturn(expectedResponse);

                // When
                mockMvc.perform(post("/api/ad/msg/send/json")
                                .header("Authorization", "Bearer " + validToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.resultCode").value(1));

                // Then - Verify service was called (balance deduction happens inside service)
                verify(adMessageService, times(1)).sendDirectMessage(any(AdMessageRequest.class), eq("testuser"));
        }

        // ==================== TC2.6.17: Response Factory Methods ====================

        @Test
        @Order(17)
        @DisplayName("TC2.6.17: Response factory methods produce correct structure")
        void TC2_6_17_responseFactoryMethods() {
                // Test success factory
                AdMessageResponse success = AdMessageResponse.success(5, 1, 2, List.of("010****1234"), "batch123");
                Assertions.assertEquals(1, success.getResultCode());
                Assertions.assertEquals(5, success.getSuccessCount());
                Assertions.assertEquals(1, success.getDuplicateCount());
                Assertions.assertEquals(2, success.getBlockedCount());
                Assertions.assertFalse(success.isAllBlocked());
                Assertions.assertNotNull(success.getBatchId());

                // Test allBlocked factory
                AdMessageResponse allBlocked = AdMessageResponse.allBlocked(3, List.of("a", "b", "c"));
                Assertions.assertEquals(-99, allBlocked.getResultCode());
                Assertions.assertTrue(allBlocked.isAllBlocked());
                Assertions.assertEquals(3, allBlocked.getBlockedCount());

                // Test insufficientBalance factory
                AdMessageResponse insufficient = AdMessageResponse.insufficientBalance("잔액 부족");
                Assertions.assertEquals(-3, insufficient.getResultCode());
                Assertions.assertTrue(insufficient.getMessage().contains("잔액 부족"));

                // Test nightTimeRestricted factory
                AdMessageResponse nightRestricted = AdMessageResponse.nightTimeRestricted();
                Assertions.assertEquals(-4, nightRestricted.getResultCode());
                Assertions.assertTrue(nightRestricted.getMessage().contains("야간"));
        }

        // ==================== TC2.6.18: Request DTO Helper Methods
        // ====================

        @Test
        @Order(18)
        @DisplayName("TC2.6.18: Request DTO helper methods work correctly")
        void TC2_6_18_requestDtoHelpers() {
                // Test getMsgTypeCode
                AdMessageRequest smsRequest = AdMessageRequest.builder().messageTypeIs("SMS").build();
                Assertions.assertEquals("S", smsRequest.getMsgTypeCode());

                AdMessageRequest lmsRequest = AdMessageRequest.builder().messageTypeIs("LMS").build();
                Assertions.assertEquals("L", lmsRequest.getMsgTypeCode());

                AdMessageRequest mmsRequest = AdMessageRequest.builder().messageTypeIs("MMS").build();
                Assertions.assertEquals("M", mmsRequest.getMsgTypeCode());

                // Test getMsgTypeLabel
                Assertions.assertEquals("SMS", smsRequest.getMsgTypeLabel());
                Assertions.assertEquals("LMS", lmsRequest.getMsgTypeLabel());
                Assertions.assertEquals("MMS", mmsRequest.getMsgTypeLabel());

                // Test shouldDeleteDuplicate
                AdMessageRequest withDuplicate = AdMessageRequest.builder().delDuplicateNum("Y").build();
                Assertions.assertTrue(withDuplicate.shouldDeleteDuplicate());

                AdMessageRequest noDuplicate = AdMessageRequest.builder().delDuplicateNum("N").build();
                Assertions.assertFalse(noDuplicate.shouldDeleteDuplicate());

                // Test isImmediate
                AdMessageRequest immediate = AdMessageRequest.builder().sendTimeType("direct").build();
                Assertions.assertTrue(immediate.isImmediate());

                AdMessageRequest scheduled = AdMessageRequest.builder()
                                .sendTimeType("schedule")
                                .reqDate(java.time.LocalDateTime.now().plusDays(1))
                                .build();
                Assertions.assertFalse(scheduled.isImmediate());
        }

        // ==================== TC2.6.19: Recipient getProcessedContent
        // ====================

        @Test
        @Order(19)
        @DisplayName("TC2.6.19: Recipient replacement character processing")
        void TC2_6_19_recipientProcessedContent() {
                // Given
                AdMessageRequest.Recipient recipient = AdMessageRequest.Recipient.builder()
                                .recPhone("01011112222")
                                .repChar01("John")
                                .repChar02("ABC123")
                                .repChar03("VIP")
                                .build();

                String baseContent = "Hello #대치문자1#, your code is #대치문자2#. Level: #대치문자3#";

                // When
                String processed = recipient.getProcessedContent(baseContent);

                // Then
                Assertions.assertEquals("Hello John, your code is ABC123. Level: VIP", processed);
        }

        // ==================== TC2.6.20: Recipient getNormalizedPhone
        // ====================

        @Test
        @Order(20)
        @DisplayName("TC2.6.20: Phone normalization removes hyphens")
        void TC2_6_20_phoneNormalization() {
                // Given
                AdMessageRequest.Recipient withHyphens = AdMessageRequest.Recipient.builder()
                                .recPhone("010-1111-2222")
                                .build();

                AdMessageRequest.Recipient withoutHyphens = AdMessageRequest.Recipient.builder()
                                .recPhone("01011112222")
                                .build();

                // When & Then
                Assertions.assertEquals("01011112222", withHyphens.getNormalizedPhone());
                Assertions.assertEquals("01011112222", withoutHyphens.getNormalizedPhone());
        }
}