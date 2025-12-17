package kr.wisead.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.wisead.domain.user.dto.LoginRequest;
import kr.wisead.domain.user.dto.LoginResponse;
import kr.wisead.domain.user.dto.SignUpRequest;
import kr.wisead.domain.user.service.AuthService;
import kr.wisead.domain.user.service.UserService;
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
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 회원가입 및 관리자 승인 통합 테스트
 *
 * 테스트 시나리오:
 * 1. 사용자가 회원가입을 신청한다 (상태: 미승인)
 * 2. 관리자가 회원을 승인한다 (상태: 미승인 -> 승인)
 * 3. 미승인 상태에서 로그인 시도 시 실패
 * 4. 승인 후 로그인 성공
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("회원가입 및 승인 통합 테스트")
class UserAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private UserService userService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private static final String TEST_USER_ID = "testuser01";
    private static final String TEST_PASSWORD = "Test1234!@";
    private static final String TEST_EMAIL = "test@example.com";

    @Test
    @Order(1)
    @DisplayName("1. 회원가입 - 신규 사용자가 회원가입을 신청한다")
    void signUp_NewUser_Success() throws Exception {
        // Given: 회원가입 요청 데이터
        SignUpRequest request = SignUpRequest.builder()
                .userId(TEST_USER_ID)
                .userPass(TEST_PASSWORD)
                .userPassConfirm(TEST_PASSWORD)
                .corpName("테스트기업")
                .corpAddr("서울시 강남구")
                .bizNum("123-45-67890")
                .bizTel("02-1234-5678")
                .person("홍길동")
                .phone("010-1234-5678")
                .email(TEST_EMAIL)
                .build();

        // Mock: 회원가입 처리
        doNothing().when(authService).signUp(any(SignUpRequest.class));

        // When & Then: 회원가입 API 호출
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다. 관리자 승인 후 로그인 가능합니다."));

        // Verify: AuthService.signUp()이 호출되었는지 확인
        verify(authService, times(1)).signUp(any(SignUpRequest.class));
    }

    @Test
    @Order(2)
    @DisplayName("2. 아이디 중복 확인 - 이미 등록된 아이디")
    void checkUserId_Duplicate_ReturnTrue() throws Exception {
        // Given: 중복된 아이디
        when(authService.checkUserIdDuplicate(TEST_USER_ID)).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/api/auth/check-userid")
                        .param("userId", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.duplicate").value(true));
    }

    @Test
    @Order(3)
    @DisplayName("3. 미승인 상태에서 로그인 시도 - 실패")
    void login_NotApproved_Fail() throws Exception {
        // Given: 미승인 상태 사용자 로그인 시도
        LoginRequest request = LoginRequest.builder()
                .userId(TEST_USER_ID)
                .userPass(TEST_PASSWORD)
                .build();

        // Mock: 미승인 상태 예외 발생
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new kr.wisead.common.exception.BusinessException(
                        kr.wisead.common.response.ErrorCode.ACCOUNT_DISABLED,
                        "승인 대기 중인 계정입니다."));

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(4)
    @DisplayName("4. 관리자가 회원 상태를 승인으로 변경")
    void updateStatus_AdminApprove_Success() throws Exception {
        // Given: 관리자 토큰 생성
        var adminAuth = new UsernamePasswordAuthenticationToken(
                "admin", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        String adminToken = jwtTokenProvider.createAccessToken(adminAuth, "관리자");

        // Mock: 상태 변경 처리
        doNothing().when(userService).updateStatus(eq(TEST_USER_ID), eq("승인"));

        // 상태 변경 요청 (JSON body)
        Map<String, String> statusRequest = Map.of("status", "승인");

        // When & Then
        mockMvc.perform(put("/api/users/{userId}/status", TEST_USER_ID)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService, times(1)).updateStatus(TEST_USER_ID, "승인");
    }

    @Test
    @Order(5)
    @DisplayName("5. 승인 후 로그인 성공")
    void login_Approved_Success() throws Exception {
        // Given: 승인된 사용자 로그인 요청
        LoginRequest request = LoginRequest.builder()
                .userId(TEST_USER_ID)
                .userPass(TEST_PASSWORD)
                .build();

        // Mock: 로그인 성공 응답
        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken("test-access-token")
                .refreshToken("test-refresh-token")
                .expiresIn(3600L)
                .user(LoginResponse.UserInfo.builder()
                        .seq(1L)
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
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.user.status").value("승인"));
    }

    @Test
    @Order(6)
    @DisplayName("6. 잘못된 비밀번호로 로그인 시도 - 실패")
    void login_WrongPassword_Fail() throws Exception {
        // Given: 잘못된 비밀번호
        LoginRequest request = LoginRequest.builder()
                .userId(TEST_USER_ID)
                .userPass("wrongPassword1!")
                .build();

        // Mock: 로그인 실패 예외
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new kr.wisead.common.exception.BusinessException(
                        kr.wisead.common.response.ErrorCode.LOGIN_FAILED,
                        "아이디 또는 비밀번호가 일치하지 않습니다."));

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(7)
    @DisplayName("7. 계정 잠금 상태에서 로그인 시도 - 실패")
    void login_LockedAccount_Fail() throws Exception {
        // Given: 계정 잠금 상태
        LoginRequest request = LoginRequest.builder()
                .userId(TEST_USER_ID)
                .userPass(TEST_PASSWORD)
                .build();

        // Mock: 계정 잠금 예외
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new kr.wisead.common.exception.BusinessException(
                        kr.wisead.common.response.ErrorCode.ACCOUNT_LOCKED,
                        "로그인 실패 횟수 초과로 계정이 잠겼습니다."));

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(8)
    @DisplayName("8. 관리자가 계정 잠금 해제")
    void unlockAccount_Admin_Success() throws Exception {
        // Given: 관리자 토큰
        var adminAuth = new UsernamePasswordAuthenticationToken(
                "admin", null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        String adminToken = jwtTokenProvider.createAccessToken(adminAuth, "관리자");

        // Mock: 잠금 해제 처리
        when(userService.unlockAccount(TEST_USER_ID)).thenReturn(1);

        // When & Then
        mockMvc.perform(put("/api/users/{userId}/unlock", TEST_USER_ID)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService, times(1)).unlockAccount(TEST_USER_ID);
    }
}
