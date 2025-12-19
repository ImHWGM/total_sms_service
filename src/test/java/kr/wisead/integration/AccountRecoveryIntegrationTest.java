package kr.wisead.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.email.dto.EmailVerificationRequest;
import kr.wisead.domain.user.dto.FindIdRequest;
import kr.wisead.domain.user.dto.FindIdResponse;
import kr.wisead.domain.user.dto.FindPasswordRequest;
import kr.wisead.domain.user.dto.FindPasswordResponse;
import kr.wisead.domain.user.dto.PasswordResetConfirmRequest;
import kr.wisead.domain.user.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 계정 복구 (아이디 찾기 / 비밀번호 찾기) 통합 테스트
 *
 * 테스트 시나리오:
 * 1. 아이디 찾기 1단계 - 정보 검증 및 이메일 인증코드 발송
 * 2. 아이디 찾기 2단계 - 인증코드 검증 및 아이디 반환
 * 3. 아이디 찾기 실패 - 계정 정보 없음
 * 4. 비밀번호 찾기 - 힌트 검증 후 재설정 링크 발송
 * 5. 비밀번호 찾기 실패 - 힌트 불일치
 * 6. 비밀번호 재설정 토큰 검증
 * 7. 비밀번호 재설정 확인
 * 8. 비밀번호 재설정 실패 - 만료된 토큰
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("계정 복구 통합 테스트")
class AccountRecoveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    private static final String TEST_USER_ID = "testuser01";
    private static final String TEST_CORP_NAME = "테스트기업";
    private static final String TEST_PERSON = "홍길동";
    private static final String TEST_PHONE = "010-2345-6789";
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_MASKED_EMAIL = "te***@example.com";
    private static final String TEST_MASKED_USER_ID = "tes*******";
    private static final String TEST_RESET_TOKEN = "550e8400-e29b-41d4-a716-446655440000";

    // ==================== 아이디 찾기 테스트 ====================

    @Test
    @Order(1)
    @DisplayName("1. 아이디 찾기 1단계 - 정보 검증 및 이메일 인증코드 발송 성공")
    void findId_Step1_RequestSuccess() throws Exception {
        // Given: 아이디 찾기 요청 데이터
        FindIdRequest request = FindIdRequest.builder()
                .corpName(TEST_CORP_NAME)
                .person(TEST_PERSON)
                .phone(TEST_PHONE)
                .build();

        // Mock: 성공 응답
        FindIdResponse mockResponse = FindIdResponse.requestSuccess(TEST_MASKED_EMAIL);
        when(userService.requestFindId(any(FindIdRequest.class))).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/users/find-id/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(1))
                .andExpect(jsonPath("$.data.maskedEmail").value(TEST_MASKED_EMAIL))
                .andExpect(jsonPath("$.data.message").value("인증 코드가 발송되었습니다."));

        verify(userService, times(1)).requestFindId(any(FindIdRequest.class));
    }

    @Test
    @Order(2)
    @DisplayName("2. 아이디 찾기 2단계 - 인증코드 검증 및 아이디 반환 성공")
    void findId_Step2_VerifySuccess() throws Exception {
        // Given: 인증 검증 요청
        EmailVerificationRequest request = EmailVerificationRequest.builder()
                .email(TEST_EMAIL)
                .code("ABC12345")
                .build();

        // Mock: 성공 응답
        FindIdResponse mockResponse = FindIdResponse.verifySuccess(TEST_MASKED_USER_ID);
        when(userService.verifyAndGetUserId(eq(TEST_EMAIL), eq("ABC12345"))).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/users/find-id/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(1))
                .andExpect(jsonPath("$.data.maskedUserId").value(TEST_MASKED_USER_ID))
                .andExpect(jsonPath("$.data.message").value("아이디 조회가 완료되었습니다."));

        verify(userService, times(1)).verifyAndGetUserId(TEST_EMAIL, "ABC12345");
    }

    @Test
    @Order(3)
    @DisplayName("3. 아이디 찾기 실패 - 계정 정보 없음")
    void findId_AccountNotFound() throws Exception {
        // Given: 아이디 찾기 요청
        FindIdRequest request = FindIdRequest.builder()
                .corpName("없는기업")
                .person("없는사람")
                .phone("010-0000-0000")
                .build();

        // Mock: 계정 정보 없음 응답
        FindIdResponse mockResponse = FindIdResponse.accountNotFound();
        when(userService.requestFindId(any(FindIdRequest.class))).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/users/find-id/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(2))
                .andExpect(jsonPath("$.data.message").value("입력하신 정보와 일치하는 계정이 없습니다."));
    }

    @Test
    @Order(4)
    @DisplayName("4. 아이디 찾기 실패 - 인증코드 불일치")
    void findId_VerificationFailed() throws Exception {
        // Given: 잘못된 인증코드
        EmailVerificationRequest request = EmailVerificationRequest.builder()
                .email(TEST_EMAIL)
                .code("WRONGCODE")
                .build();

        // Mock: 인증 실패 응답
        FindIdResponse mockResponse = FindIdResponse.verificationFailed();
        when(userService.verifyAndGetUserId(eq(TEST_EMAIL), eq("WRONGCODE"))).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/users/find-id/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(3))
                .andExpect(jsonPath("$.data.message").value("인증 코드가 일치하지 않습니다."));
    }

    // ==================== 비밀번호 찾기 테스트 ====================

    @Test
    @Order(5)
    @DisplayName("5. 비밀번호 찾기 - 힌트 검증 후 재설정 링크 발송 성공")
    void findPassword_Success() throws Exception {
        // Given: 비밀번호 찾기 요청
        FindPasswordRequest request = new FindPasswordRequest();
        request.setUserId(TEST_USER_ID);
        request.setCorpName(TEST_CORP_NAME);
        request.setPerson(TEST_PERSON);
        request.setPhone(TEST_PHONE);
        request.setHintQuestion("첫 번째 애완동물 이름은?");
        request.setHintAnswer("뽀삐");

        // Mock: 성공 응답
        FindPasswordResponse mockResponse = FindPasswordResponse.success(TEST_MASKED_EMAIL);
        when(userService.findPassword(any(FindPasswordRequest.class))).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/users/find-pw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(1))
                .andExpect(jsonPath("$.data.maskedEmail").value(TEST_MASKED_EMAIL))
                .andExpect(jsonPath("$.data.message").value("비밀번호 재설정 링크가 이메일로 발송되었습니다."));

        verify(userService, times(1)).findPassword(any(FindPasswordRequest.class));
    }

    @Test
    @Order(6)
    @DisplayName("6. 비밀번호 찾기 실패 - 계정 정보 없음")
    void findPassword_AccountNotFound() throws Exception {
        // Given: 비밀번호 찾기 요청
        FindPasswordRequest request = new FindPasswordRequest();
        request.setUserId("nonexistent");
        request.setCorpName("없는기업");
        request.setPerson("없는사람");
        request.setPhone("010-0000-0000");
        request.setHintQuestion("질문");
        request.setHintAnswer("답변");

        // Mock: 계정 정보 없음 응답
        FindPasswordResponse mockResponse = FindPasswordResponse.accountNotFound();
        when(userService.findPassword(any(FindPasswordRequest.class))).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/users/find-pw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(2))
                .andExpect(jsonPath("$.data.message").value("입력하신 정보와 일치하는 계정이 없습니다."));
    }

    @Test
    @Order(7)
    @DisplayName("7. 비밀번호 찾기 실패 - 힌트 불일치")
    void findPassword_HintMismatch() throws Exception {
        // Given: 비밀번호 찾기 요청 (힌트 오류)
        FindPasswordRequest request = new FindPasswordRequest();
        request.setUserId(TEST_USER_ID);
        request.setCorpName(TEST_CORP_NAME);
        request.setPerson(TEST_PERSON);
        request.setPhone(TEST_PHONE);
        request.setHintQuestion("첫 번째 애완동물 이름은?");
        request.setHintAnswer("잘못된답변");

        // Mock: 힌트 불일치 응답
        FindPasswordResponse mockResponse = FindPasswordResponse.hintMismatch();
        when(userService.findPassword(any(FindPasswordRequest.class))).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(post("/api/users/find-pw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(3))
                .andExpect(jsonPath("$.data.message").value("비밀번호 힌트가 일치하지 않습니다."));
    }

    // ==================== 비밀번호 재설정 테스트 ====================

    @Test
    @Order(8)
    @DisplayName("8. 비밀번호 재설정 토큰 유효성 검증 - 성공")
    void validateResetToken_Success() throws Exception {
        // Mock: 유효한 토큰 응답
        Map<String, Object> mockResponse = Map.of(
                "valid", true,
                "userId", TEST_MASKED_USER_ID
        );
        when(userService.validateResetToken(TEST_RESET_TOKEN)).thenReturn(mockResponse);

        // When & Then
        mockMvc.perform(get("/api/users/password/reset-validate")
                        .param("token", TEST_RESET_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.userId").value(TEST_MASKED_USER_ID));

        verify(userService, times(1)).validateResetToken(TEST_RESET_TOKEN);
    }

    @Test
    @Order(9)
    @DisplayName("9. 비밀번호 재설정 토큰 검증 실패 - 만료된 토큰")
    void validateResetToken_Expired() throws Exception {
        // Mock: 만료된 토큰 예외
        when(userService.validateResetToken("expired-token"))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "만료된 토큰입니다."));

        // When & Then
        mockMvc.perform(get("/api/users/password/reset-validate")
                        .param("token", "expired-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(10)
    @DisplayName("10. 비밀번호 재설정 확인 - 성공")
    void resetPasswordWithToken_Success() throws Exception {
        // Given: 비밀번호 재설정 요청
        PasswordResetConfirmRequest request = PasswordResetConfirmRequest.builder()
                .token(TEST_RESET_TOKEN)
                .newPassword("NewPass123!@")
                .build();

        // Mock: 성공 처리
        doNothing().when(userService).resetPasswordWithToken(eq(TEST_RESET_TOKEN), eq("NewPass123!@"));

        // When & Then
        mockMvc.perform(post("/api/users/password/reset-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("비밀번호가 성공적으로 변경되었습니다."));

        verify(userService, times(1)).resetPasswordWithToken(TEST_RESET_TOKEN, "NewPass123!@");
    }

    @Test
    @Order(11)
    @DisplayName("11. 비밀번호 재설정 실패 - 이미 사용된 토큰")
    void resetPasswordWithToken_AlreadyUsed() throws Exception {
        // Given: 비밀번호 재설정 요청
        PasswordResetConfirmRequest request = PasswordResetConfirmRequest.builder()
                .token("used-token")
                .newPassword("NewPass123!@")
                .build();

        // Mock: 이미 사용된 토큰 예외
        doThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "이미 사용된 토큰입니다."))
                .when(userService).resetPasswordWithToken(eq("used-token"), any());

        // When & Then
        mockMvc.perform(post("/api/users/password/reset-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(12)
    @DisplayName("12. 비밀번호 재설정 실패 - 비밀번호 유효성 검증 실패")
    void resetPasswordWithToken_InvalidPassword() throws Exception {
        // Given: 약한 비밀번호로 재설정 요청
        PasswordResetConfirmRequest request = PasswordResetConfirmRequest.builder()
                .token(TEST_RESET_TOKEN)
                .newPassword("weak")  // 유효성 검증 실패
                .build();

        // When & Then: @Valid 검증 실패
        mockMvc.perform(post("/api/users/password/reset-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
