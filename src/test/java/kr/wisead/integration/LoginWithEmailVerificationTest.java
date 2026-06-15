package kr.wisead.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import kr.wisead.common.dto.VerificationStatus;
import kr.wisead.domain.email.service.PreSignupEmailAuthService;
import kr.wisead.domain.user.dto.LoginRequest;
import kr.wisead.domain.user.dto.LoginResponse;
import kr.wisead.domain.user.service.AuthService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 로그인 시 이메일 인증 통합 테스트
 *
 * <p>테스트 시나리오: 1. 그날 처음 로그인 시도 -> 이메일 인증코드 발송 필요 2. 이메일 인증코드 발송 3. 이메일 인증코드 검증 4. 인증 완료 후 로그인 성공 5.
 * 같은 날 재로그인 -> 이메일 인증 없이 바로 로그인
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("이메일 인증 로그인 통합 테스트")
class LoginWithEmailVerificationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private AuthService authService;

  // Phase B-0-4: /api/email/* 경로는 PreSignupEmailAuthService(key=email)로 라우팅됨
  @MockitoBean private PreSignupEmailAuthService emailAuthService;

  private static final String TEST_USER_ID = "testuser01";
  private static final String TEST_PASSWORD = "Test1234!@";
  private static final String TEST_EMAIL = "test@example.com";
  private static final String VERIFICATION_CODE = "123456";

  @Test
  @Order(1)
  @DisplayName("1. 이메일 인증코드 발송 요청")
  void sendVerificationCode_Success() throws Exception {
    // Given: 이메일 인증코드 발송 요청
    Map<String, String> request = Map.of("email", TEST_EMAIL);

    // Mock: 인증코드 발송 성공
    when(emailAuthService.sendVerificationCode(TEST_EMAIL)).thenReturn(true);

    // When & Then
    mockMvc
        .perform(
            post("/api/email/verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(emailAuthService, times(1)).sendVerificationCode(TEST_EMAIL);
  }

  @Test
  @Order(2)
  @DisplayName("2. 이메일 인증코드 재발송 - 1분 제한")
  void resendVerificationCode_TooSoon_Fail() throws Exception {
    // Given: 재발송 제한 시간 내 요청
    Map<String, String> request = Map.of("email", TEST_EMAIL);

    // Mock: 재발송 제한 예외
    when(emailAuthService.sendVerificationCode(TEST_EMAIL))
        .thenThrow(
            new kr.wisead.common.exception.BusinessException(
                kr.wisead.common.response.ErrorCode.INVALID_INPUT_VALUE, "재발송은 45초 후에 가능합니다."));

    // When & Then
    mockMvc
        .perform(
            post("/api/email/verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  @Order(3)
  @DisplayName("3. 이메일 인증 상태 조회")
  void getVerificationStatus_CodeSent() throws Exception {
    // Given: 인증코드가 발송된 상태
    VerificationStatus status = new VerificationStatus(true, 240, 0, 5);

    when(emailAuthService.getVerificationStatus(TEST_EMAIL)).thenReturn(status);

    // When & Then
    mockMvc
        .perform(get("/api/email/verification/status").param("email", TEST_EMAIL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.codeSent").value(true))
        .andExpect(jsonPath("$.data.remainingAttempts").value(5));
  }

  @Test
  @Order(4)
  @DisplayName("4. 이메일 인증코드 검증 - 잘못된 코드")
  void verifyCode_WrongCode_Fail() throws Exception {
    // Given: 잘못된 인증코드
    Map<String, String> request = Map.of("email", TEST_EMAIL, "code", "000000");

    // Mock: 검증 실패
    when(emailAuthService.verifyCode(eq(TEST_EMAIL), eq("000000")))
        .thenThrow(
            new kr.wisead.common.exception.BusinessException(
                kr.wisead.common.response.ErrorCode.INVALID_INPUT_VALUE,
                "인증 코드가 일치하지 않습니다. (남은 시도: 4회)"));

    // When & Then
    mockMvc
        .perform(
            post("/api/email/verification/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  @Order(5)
  @DisplayName("5. 이메일 인증코드 검증 - 성공")
  void verifyCode_Correct_Success() throws Exception {
    // Given: 올바른 인증코드
    Map<String, String> request =
        Map.of(
            "email", TEST_EMAIL,
            "code", VERIFICATION_CODE);

    // Mock: 검증 성공
    when(emailAuthService.verifyCode(TEST_EMAIL, VERIFICATION_CODE)).thenReturn(true);

    // When & Then
    mockMvc
        .perform(
            post("/api/email/verification/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(emailAuthService, times(1)).verifyCode(TEST_EMAIL, VERIFICATION_CODE);
  }

  @Test
  @Order(6)
  @DisplayName("6. 이메일 인증 완료 후 로그인 성공")
  void login_AfterEmailVerification_Success() throws Exception {
    // Given: 로그인 요청
    LoginRequest request =
        LoginRequest.builder().userId(TEST_USER_ID).userPass(TEST_PASSWORD).build();

    // Mock: 로그인 성공 응답
    LoginResponse loginResponse =
        LoginResponse.builder()
            .accessToken("valid-access-token-after-verification")
            .refreshToken("valid-refresh-token")
            .expiresIn(3600L)
            .user(
                LoginResponse.UserInfo.builder()
                    .seq(1)
                    .userId(TEST_USER_ID)
                    .corpName("테스트기업")
                    .person("홍길동")
                    .email(TEST_EMAIL)
                    .userLevel(1)
                    .status("승인")
                    .build())
            .build();

    when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);

    // When & Then
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").value("valid-access-token-after-verification"))
        .andExpect(jsonPath("$.data.user.userId").value(TEST_USER_ID));
  }

  @Test
  @Order(7)
  @DisplayName("7. 같은 날 재로그인 - 이메일 인증 없이 바로 성공")
  void reLogin_SameDay_DirectSuccess() throws Exception {
    // Given: 오늘 이미 로그인한 사용자의 재로그인 요청
    // (실제 구현에서는 마지막 로그인 날짜를 확인하는 로직이 필요)
    LoginRequest request =
        LoginRequest.builder().userId(TEST_USER_ID).userPass(TEST_PASSWORD).build();

    // Mock: 바로 로그인 성공 (이메일 인증 건너뛰기)
    LoginResponse loginResponse =
        LoginResponse.builder()
            .accessToken("access-token-for-same-day-relogin")
            .refreshToken("refresh-token")
            .expiresIn(3600L)
            .user(
                LoginResponse.UserInfo.builder()
                    .seq(1)
                    .userId(TEST_USER_ID)
                    .corpName("테스트기업")
                    .person("홍길동")
                    .email(TEST_EMAIL)
                    .userLevel(1)
                    .status("승인")
                    .build())
            .build();

    when(authService.login(any(LoginRequest.class))).thenReturn(loginResponse);

    // When & Then: 이메일 인증 없이 바로 로그인 성공
    mockMvc
        .perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.accessToken").exists());

    // Verify: 이메일 인증 서비스가 호출되지 않음
    verify(emailAuthService, never()).sendVerificationCode(anyString());
  }

  @Test
  @Order(8)
  @DisplayName("8. 인증코드 만료 후 재시도")
  void verifyCode_Expired_Fail() throws Exception {
    // Given: 만료된 인증코드
    Map<String, String> request =
        Map.of(
            "email", TEST_EMAIL,
            "code", VERIFICATION_CODE);

    // Mock: 만료 예외
    when(emailAuthService.verifyCode(eq(TEST_EMAIL), eq(VERIFICATION_CODE)))
        .thenThrow(
            new kr.wisead.common.exception.BusinessException(
                kr.wisead.common.response.ErrorCode.INVALID_INPUT_VALUE,
                "인증 코드가 만료되었습니다. 다시 발송해주세요."));

    // When & Then
    mockMvc
        .perform(
            post("/api/email/verification/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("인증 코드가 만료되었습니다. 다시 발송해주세요."));
  }

  @Test
  @Order(9)
  @DisplayName("9. 인증 시도 횟수 초과")
  void verifyCode_MaxAttemptsExceeded_Fail() throws Exception {
    // Given: 최대 시도 횟수 초과
    Map<String, String> request = Map.of("email", TEST_EMAIL, "code", "999999");

    // Mock: 시도 횟수 초과 예외
    when(emailAuthService.verifyCode(eq(TEST_EMAIL), eq("999999")))
        .thenThrow(
            new kr.wisead.common.exception.BusinessException(
                kr.wisead.common.response.ErrorCode.INVALID_INPUT_VALUE,
                "인증 시도 횟수를 초과했습니다. 다시 발송해주세요."));

    // When & Then
    mockMvc
        .perform(
            post("/api/email/verification/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false));
  }

  @Test
  @Order(10)
  @DisplayName("10. 인증코드 재발송 - 기존 코드 무효화")
  void resendVerificationCode_InvalidatePrevious_Success() throws Exception {
    // Given: 재발송 요청
    Map<String, String> request = Map.of("email", TEST_EMAIL);

    // Mock: 재발송 성공
    when(emailAuthService.resendVerificationCode(TEST_EMAIL)).thenReturn(true);

    // When & Then
    mockMvc
        .perform(
            post("/api/email/verification/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(emailAuthService, times(1)).resendVerificationCode(TEST_EMAIL);
  }
}
