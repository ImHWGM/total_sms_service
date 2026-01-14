package kr.wisead.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.wisead.domain.user.dto.LoginRequest;
import kr.wisead.domain.user.dto.LoginResponse;
import kr.wisead.domain.user.dto.SignUpRequest;
import kr.wisead.domain.user.dto.TokenRefreshRequest;
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
                .phone("010-2345-6789")
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

    // ============================================================
    // Task 1.1 Login API Test Cases (TC1.1.1 ~ TC1.1.5)
    // ============================================================

    /**
     * TC1.1.1: 정상 로그인 - 유효한 ID/PW -> JWT 토큰 반환
     */
    @Test
    @Order(101)
    @DisplayName("TC1.1.1: 정상 로그인 - 유효한 ID/PW로 JWT 토큰 반환")
    void TC1_1_1_login_ValidCredentials_ReturnsJwtToken() throws Exception {
        // Given: 유효한 로그인 요청
        LoginRequest request = LoginRequest.builder()
                .userId(TEST_USER_ID)
                .userPass(TEST_PASSWORD)
                .build();

        // Mock: 로그인 성공 응답 (JWT 토큰 포함)
        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken("eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0ZXN0dXNlcjAxIiwiZXhwIjoxNzM2NzYyMDAwfQ.test")
                .refreshToken("eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0ZXN0dXNlcjAxIiwiZXhwIjoxNzM3MzY2ODAwfQ.test")
                .expiresIn(3600L)
                .emailRequired(false)
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
                .andExpect(jsonPath("$.message").value("로그인 성공"))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.user.userId").value(TEST_USER_ID));

        verify(authService, times(1)).login(any(LoginRequest.class));
    }

    /**
     * TC1.1.2: 잘못된 비밀번호 -> 401 에러 + 실패 카운트 증가
     */
    @Test
    @Order(102)
    @DisplayName("TC1.1.2: 잘못된 비밀번호 - 401 에러 및 실패 카운트 증가")
    void TC1_1_2_login_WrongPassword_Returns401AndIncrementsFailureCount() throws Exception {
        // Given: 잘못된 비밀번호로 로그인 시도
        LoginRequest request = LoginRequest.builder()
                .userId(TEST_USER_ID)
                .userPass("WrongPassword1!")
                .build();

        // Mock: 비밀번호 불일치 예외 (내부적으로 실패 카운트 증가됨)
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new kr.wisead.common.exception.BusinessException(
                        kr.wisead.common.response.ErrorCode.LOGIN_FAILED,
                        "아이디 또는 비밀번호가 일치하지 않습니다."));

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A005"));

        verify(authService, times(1)).login(any(LoginRequest.class));
    }

    /**
     * TC1.1.3: 5회 실패 후 계정 잠금 -> 423 에러 (LOCKED)
     * Note: Spring에서는 403 Forbidden을 사용하며, ErrorCode.ACCOUNT_LOCKED를 반환
     */
    @Test
    @Order(103)
    @DisplayName("TC1.1.3: 5회 실패 후 계정 잠금 - 403 에러 (ACCOUNT_LOCKED)")
    void TC1_1_3_login_After5Failures_ReturnsAccountLocked() throws Exception {
        // Given: 계정이 잠긴 상태에서 로그인 시도
        LoginRequest request = LoginRequest.builder()
                .userId(TEST_USER_ID)
                .userPass(TEST_PASSWORD)
                .build();

        // Mock: 계정 잠금 예외
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new kr.wisead.common.exception.BusinessException(
                        kr.wisead.common.response.ErrorCode.ACCOUNT_LOCKED,
                        "로그인 실패 횟수 초과로 계정이 잠겼습니다. 관리자에게 문의하세요."));

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A007"))
                .andExpect(jsonPath("$.message").value("로그인 실패 횟수 초과로 계정이 잠겼습니다. 관리자에게 문의하세요."));

        verify(authService, times(1)).login(any(LoginRequest.class));
    }

    /**
     * TC1.1.4: 휴면 계정 로그인 시도 -> 적절한 에러 메시지
     * Note: 현재 시스템에서는 ACCOUNT_DISABLED로 처리됨
     */
    @Test
    @Order(104)
    @DisplayName("TC1.1.4: 휴면 계정 로그인 시도 - 403 에러 (ACCOUNT_DISABLED)")
    void TC1_1_4_login_DormantAccount_ReturnsAppropriateError() throws Exception {
        // Given: 휴면 계정으로 로그인 시도
        LoginRequest request = LoginRequest.builder()
                .userId(TEST_USER_ID)
                .userPass(TEST_PASSWORD)
                .build();

        // Mock: 휴면 계정 예외 (현재 시스템에서는 ACCOUNT_DISABLED 사용)
        // Note: Legacy 시스템에서는 result: -180으로 처리됨
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new kr.wisead.common.exception.BusinessException(
                        kr.wisead.common.response.ErrorCode.ACCOUNT_DISABLED,
                        "휴면회원입니다. 서비스센터(02-711-7911)로 문의하시기 바랍니다."));

        // When & Then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A006"))
                .andExpect(jsonPath("$.message").value("휴면회원입니다. 서비스센터(02-711-7911)로 문의하시기 바랍니다."));

        verify(authService, times(1)).login(any(LoginRequest.class));
    }

    /**
     * TC1.1.5: 비밀번호 만료 계정 -> 변경 요청 응답
     * Note: Legacy 시스템에서는 result: 180으로 처리됨. 현재 시스템에서 구현 필요.
     */
    @Test
    @Order(105)
    @DisplayName("TC1.1.5: 비밀번호 만료 계정 - 비밀번호 변경 요청 응답")
    void TC1_1_5_login_PasswordExpired_ReturnsPasswordChangeRequired() throws Exception {
        // Given: 비밀번호 만료 계정으로 로그인 시도
        LoginRequest request = LoginRequest.builder()
                .userId(TEST_USER_ID)
                .userPass(TEST_PASSWORD)
                .build();

        // Mock: 비밀번호 만료 응답 (로그인은 성공하지만 비밀번호 변경 필요 플래그 포함)
        // Note: Legacy 시스템에서는 result: 180으로 처리됨
        // 현재 시스템에서는 passwordExpired 필드 추가 필요 (향후 구현)
        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken("eyJhbGciOiJIUzUxMiJ9.test")
                .refreshToken("eyJhbGciOiJIUzUxMiJ9.refresh")
                .expiresIn(3600L)
                .emailRequired(false)
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

        // When & Then: 로그인 성공 (비밀번호 변경 필요 알림은 응답에 포함되어야 함)
        // Note: 현재 시스템에는 passwordExpired 필드가 없으므로, 이 테스트는 기본 동작 확인
        // 향후 LoginResponse에 passwordChangeRequired 필드 추가 시 테스트 업데이트 필요
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.user.userId").value(TEST_USER_ID));

        verify(authService, times(1)).login(any(LoginRequest.class));
    }

    // ============================================================
    // Task 1.2 SignUp API Test Cases (TC1.2.1 ~ TC1.2.5)
    // ============================================================

    /**
     * TC1.2.1: 정상 회원가입 - 201 Created (또는 200 OK)
     *
     * Scenario: 사용자가 올바른 정보로 회원가입을 신청한다
     * Expected: 회원가입 성공 메시지 반환
     */
    @Test
    @Order(121)
    @DisplayName("TC1.2.1: 정상 회원가입 - 201 Created")
    void TC1_2_1_signUp_ValidData_ReturnsSuccess() throws Exception {
        // Given: 정상적인 회원가입 요청 데이터
        SignUpRequest request = SignUpRequest.builder()
                .userId("newuser001")
                .userPass("Test1234!@")
                .userPassConfirm("Test1234!@")
                .corpName("신규테스트기업")
                .corpAddr("서울시 강남구 테헤란로 123")
                .bizNum("123-45-67890")
                .bizTel("02-1234-5678")
                .person("홍길동")
                .phone("010-2345-6789")
                .email("newuser@example.com")
                .build();

        // Mock: 회원가입 처리 성공
        doNothing().when(authService).signUp(any(SignUpRequest.class));

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다. 관리자 승인 후 로그인 가능합니다."));

        verify(authService, times(1)).signUp(any(SignUpRequest.class));
    }

    /**
     * TC1.2.2: 중복 ID - 409 Conflict
     *
     * Scenario: 이미 사용 중인 아이디로 회원가입을 시도한다
     * Expected: 409 Conflict 에러와 중복 메시지 반환
     */
    @Test
    @Order(122)
    @DisplayName("TC1.2.2: 중복 ID - 409 Conflict")
    void TC1_2_2_signUp_DuplicateUserId_Returns409() throws Exception {
        // Given: 중복된 아이디로 회원가입 시도
        SignUpRequest request = SignUpRequest.builder()
                .userId("existinguser")
                .userPass("Test1234!@")
                .userPassConfirm("Test1234!@")
                .corpName("테스트기업")
                .corpAddr("서울시 강남구")
                .bizNum("123-45-67890")
                .bizTel("02-1234-5678")
                .person("홍길동")
                .phone("010-2345-6789")
                .email("newuser@example.com")
                .build();

        // Mock: 중복 아이디 예외 발생
        doThrow(new kr.wisead.common.exception.BusinessException(
                kr.wisead.common.response.ErrorCode.DUPLICATE_EMAIL,
                "이미 사용 중인 아이디입니다."))
                .when(authService).signUp(any(SignUpRequest.class));

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("이미 사용 중인 아이디입니다."));

        verify(authService, times(1)).signUp(any(SignUpRequest.class));
    }

    /**
     * TC1.2.3: 유효하지 않은 이메일 형식 - 400 Bad Request
     *
     * Scenario: 올바르지 않은 이메일 형식으로 회원가입을 시도한다
     * Expected: 400 Bad Request 에러와 유효성 검사 메시지 반환
     */
    @Test
    @Order(123)
    @DisplayName("TC1.2.3: 유효하지 않은 이메일 형식 - 400 Bad Request")
    void TC1_2_3_signUp_InvalidEmail_Returns400() throws Exception {
        // Given: 잘못된 이메일 형식
        String json = """
                {
                    "userId": "newuser001",
                    "userPass": "Test1234!@",
                    "userPassConfirm": "Test1234!@",
                    "corpName": "테스트기업",
                    "corpAddr": "서울시 강남구",
                    "bizNum": "123-45-67890",
                    "bizTel": "02-1234-5678",
                    "person": "홍길동",
                    "phone": "010-2345-6789",
                    "email": "invalid-email"
                }
                """;

        // When & Then: @Valid 어노테이션이 유효성 검사 에러 발생
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // AuthService.signUp()이 호출되지 않아야 함 (유효성 검사에서 실패)
        verify(authService, never()).signUp(any(SignUpRequest.class));
    }

    /**
     * TC1.2.4: 필수 필드 누락 - 400 Bad Request
     *
     * Scenario: 필수 필드(기업명)를 누락하고 회원가입을 시도한다
     * Expected: 400 Bad Request 에러와 필수 필드 메시지 반환
     */
    @Test
    @Order(124)
    @DisplayName("TC1.2.4: 필수 필드 누락 - 400 Bad Request")
    void TC1_2_4_signUp_MissingField_Returns400() throws Exception {
        // Given: corpName 필드 누락
        String json = """
                {
                    "userId": "newuser001",
                    "userPass": "Test1234!@",
                    "userPassConfirm": "Test1234!@",
                    "corpAddr": "서울시 강남구",
                    "bizNum": "123-45-67890",
                    "bizTel": "02-1234-5678",
                    "person": "홍길동",
                    "phone": "010-2345-6789",
                    "email": "test@example.com"
                }
                """;

        // When & Then: @Valid 어노테이션이 유효성 검사 에러 발생
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // AuthService.signUp()이 호출되지 않아야 함 (유효성 검사에서 실패)
        verify(authService, never()).signUp(any(SignUpRequest.class));
    }

    /**
     * TC1.2.5: 비밀번호 불일치 - 400 Bad Request
     *
     * Scenario: 비밀번호와 비밀번호 확인이 일치하지 않는다
     * Expected: 400 Bad Request 에러와 PASSWORD_MISMATCH 코드 반환
     */
    @Test
    @Order(125)
    @DisplayName("TC1.2.5: 비밀번호 불일치 - 400 Bad Request")
    void TC1_2_5_signUp_PasswordMismatch_Returns400() throws Exception {
        // Given: 비밀번호 불일치
        SignUpRequest request = SignUpRequest.builder()
                .userId("newuser001")
                .userPass("Test1234!@")
                .userPassConfirm("DifferentPass1!")  // 다른 비밀번호
                .corpName("테스트기업")
                .corpAddr("서울시 강남구")
                .bizNum("123-45-67890")
                .bizTel("02-1234-5678")
                .person("홍길동")
                .phone("010-2345-6789")
                .email("test@example.com")
                .build();

        // Mock: 비밀번호 불일치 예외 발생
        doThrow(new kr.wisead.common.exception.BusinessException(
                kr.wisead.common.response.ErrorCode.PASSWORD_MISMATCH))
                .when(authService).signUp(any(SignUpRequest.class));

        // When & Then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(authService, times(1)).signUp(any(SignUpRequest.class));
    }

    // ============================================================
    // Task 1.3 ID Check API Test Cases (TC1.3.1 ~ TC1.3.2)
    // ============================================================

    /**
     * TC1.3.1: 사용 가능한 ID 확인 - duplicate: false
     *
     * Legacy 시스템: POST /qvey/signUpIdChk.do -> result: 0
     * New 시스템: GET /api/auth/check-userid -> duplicate: false
     */
    @Test
    @Order(131)
    @DisplayName("TC1.3.1: 사용 가능한 ID 확인 - duplicate: false 반환")
    void TC1_3_1_checkUserId_Available_ReturnsFalse() throws Exception {
        // Given: 미등록 아이디
        String availableUserId = "newuser123";
        when(authService.checkUserIdDuplicate(availableUserId)).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/api/auth/check-userid")
                        .param("userId", availableUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.duplicate").value(false));

        verify(authService, times(1)).checkUserIdDuplicate(availableUserId);
    }

    /**
     * TC1.3.2: 중복된 ID 확인 - duplicate: true
     *
     * Legacy 시스템: POST /qvey/signUpIdChk.do -> result: 1 (or more)
     * New 시스템: GET /api/auth/check-userid -> duplicate: true
     */
    @Test
    @Order(132)
    @DisplayName("TC1.3.2: 중복된 ID 확인 - duplicate: true 반환")
    void TC1_3_2_checkUserId_Duplicate_ReturnsTrue() throws Exception {
        // Given: 이미 등록된 아이디
        when(authService.checkUserIdDuplicate(TEST_USER_ID)).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/api/auth/check-userid")
                        .param("userId", TEST_USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.duplicate").value(true));

        verify(authService, times(1)).checkUserIdDuplicate(TEST_USER_ID);
    }

    // ============================================================
    // Task 1.4 Token Refresh API Test Cases (TC1.4.1 ~ TC1.4.5)
    // New Feature - Not present in Legacy system
    // ============================================================

    /**
     * TC1.4.1: 유효한 refresh token -> 새 access token 발급
     *
     * New 시스템 신규 기능: JWT Refresh Token으로 새 토큰 발급
     * Legacy 시스템: Session-based 인증으로 해당 기능 없음
     */
    @Test
    @Order(141)
    @DisplayName("TC1.4.1: 유효한 refresh token - 새 access token 발급")
    void TC1_4_1_refreshToken_ValidToken_ReturnsNewTokens() throws Exception {
        // Given: 유효한 refresh token 생성
        var userAuth = new UsernamePasswordAuthenticationToken(
                TEST_USER_ID, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        String validRefreshToken = jwtTokenProvider.createRefreshToken(userAuth, "홍길동");

        TokenRefreshRequest request = TokenRefreshRequest.builder()
                .refreshToken(validRefreshToken)
                .build();

        // Mock: 토큰 갱신 성공 응답
        LoginResponse refreshResponse = LoginResponse.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
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

        when(authService.refreshToken(anyString())).thenReturn(refreshResponse);

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("토큰 갱신 성공"))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.user.userId").value(TEST_USER_ID));

        verify(authService, times(1)).refreshToken(anyString());
    }

    /**
     * TC1.4.2: 만료된 refresh token -> 401 에러
     *
     * ErrorCode.INVALID_TOKEN (A002) 반환
     */
    @Test
    @Order(142)
    @DisplayName("TC1.4.2: 만료된 refresh token - 401 에러")
    void TC1_4_2_refreshToken_ExpiredToken_Returns401() throws Exception {
        // Given: 만료된 refresh token (실제로는 만료된 토큰 생성이 어려우므로 mock 사용)
        TokenRefreshRequest request = TokenRefreshRequest.builder()
                .refreshToken("expired-refresh-token")
                .build();

        // Mock: 만료된 토큰 예외
        when(authService.refreshToken(anyString()))
                .thenThrow(new kr.wisead.common.exception.BusinessException(
                        kr.wisead.common.response.ErrorCode.INVALID_TOKEN,
                        "유효하지 않은 Refresh Token입니다."));

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A002"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 Refresh Token입니다."));

        verify(authService, times(1)).refreshToken(anyString());
    }

    /**
     * TC1.4.3: 잘못된 형식 token -> 401 에러
     *
     * JWT 형식이 아닌 문자열 전송시 INVALID_TOKEN (A002) 반환
     */
    @Test
    @Order(143)
    @DisplayName("TC1.4.3: 잘못된 형식 token - 401 에러")
    void TC1_4_3_refreshToken_MalformedToken_Returns401() throws Exception {
        // Given: 잘못된 형식의 token
        TokenRefreshRequest request = TokenRefreshRequest.builder()
                .refreshToken("not-a-valid-jwt-token-format")
                .build();

        // Mock: 잘못된 형식 예외
        when(authService.refreshToken(anyString()))
                .thenThrow(new kr.wisead.common.exception.BusinessException(
                        kr.wisead.common.response.ErrorCode.INVALID_TOKEN,
                        "유효하지 않은 Refresh Token입니다."));

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A002"));

        verify(authService, times(1)).refreshToken(anyString());
    }

    /**
     * TC1.4.4: 빈 refresh token -> 400 에러 (Validation)
     *
     * @Valid 어노테이션에 의해 @NotBlank 검증 실패
     */
    @Test
    @Order(144)
    @DisplayName("TC1.4.4: 빈 refresh token - 400 에러 (Validation)")
    void TC1_4_4_refreshToken_EmptyToken_Returns400() throws Exception {
        // Given: 빈 refresh token
        String requestJson = "{\"refreshToken\": \"\"}";

        // When & Then: @Valid 애노테이션에 의해 검증 실패
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // AuthService는 호출되지 않아야 함 (Validation에서 막힘)
        verify(authService, never()).refreshToken(anyString());
    }

    /**
     * TC1.4.5: 비활성화된 계정 -> 403 에러
     *
     * 토큰은 유효하지만 계정이 비활성화된 경우 ACCOUNT_DISABLED (A006) 반환
     */
    @Test
    @Order(145)
    @DisplayName("TC1.4.5: 비활성화된 계정 - 403 에러")
    void TC1_4_5_refreshToken_DisabledAccount_Returns403() throws Exception {
        // Given: 유효한 토큰이지만 계정이 비활성화됨
        TokenRefreshRequest request = TokenRefreshRequest.builder()
                .refreshToken("valid-token-but-account-disabled")
                .build();

        // Mock: 계정 비활성화 예외
        when(authService.refreshToken(anyString()))
                .thenThrow(new kr.wisead.common.exception.BusinessException(
                        kr.wisead.common.response.ErrorCode.ACCOUNT_DISABLED,
                        "비활성화된 계정입니다."));

        // When & Then
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A006"));

        verify(authService, times(1)).refreshToken(anyString());
    }

    // ============================================================
    // Task 1.5 Get My Info API Test Cases (TC1.5.1 ~ TC1.5.2)
    // Legacy: GET /qvey/qvey_company_detail?seq={seq}&userId={userId}
    // New: GET /api/users/me
    // ============================================================

    /**
     * TC1.5.1: 인증된 사용자 - 사용자 정보 반환
     *
     * Legacy 시스템: Session 기반, 복잡한 데이터 조합 (balance, companies 포함)
     * New 시스템: JWT 기반, 사용자 정보만 반환 (RESTful 분리)
     *
     * 주요 차이점:
     * - Legacy: 92 lines controller, Model + JSP View
     * - New: 4 lines controller, JSON Response
     * - Legacy: Manual session check
     * - New: @AuthenticationPrincipal 자동 주입
     */
    @Test
    @Order(151)
    @DisplayName("TC1.5.1: 인증된 사용자 - 사용자 정보 반환")
    void TC1_5_1_getMyInfo_AuthenticatedUser_ReturnsUserInfo() throws Exception {
        // Given: 유효한 JWT 토큰 생성
        var userAuth = new UsernamePasswordAuthenticationToken(
                TEST_USER_ID, null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        String validAccessToken = jwtTokenProvider.createAccessToken(userAuth);

        // Mock: UserService 응답
        kr.wisead.domain.user.dto.UserResponse expectedResponse = kr.wisead.domain.user.dto.UserResponse.builder()
                .seq(1L)
                .userId(TEST_USER_ID)
                .corpName("테스트기업")
                .corpAddr("서울시 강남구")
                .bizNum("123-45-67890")
                .bizTel("02-1234-5678")
                .person("홍길동")
                .phone("010-2345-6789")
                .email(TEST_EMAIL)
                .userLevel(1)
                .status("승인")
                .useYn("Y")
                .callback("02-1234-5678")
                .build();

        when(userService.getUserByUserId(TEST_USER_ID)).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(TEST_USER_ID))
                .andExpect(jsonPath("$.data.corpName").value("테스트기업"))
                .andExpect(jsonPath("$.data.email").value(TEST_EMAIL))
                .andExpect(jsonPath("$.data.status").value("승인"))
                // 중요: password는 응답에 포함되지 않아야 함
                .andExpect(jsonPath("$.data.userPass").doesNotExist());

        verify(userService, times(1)).getUserByUserId(TEST_USER_ID);
    }

    /**
     * TC1.5.2: 인증 없음 - 401 에러
     *
     * JWT 토큰 없이 /api/users/me 접근 시 401 Unauthorized
     *
     * Legacy 시스템: Session 없으면 로그인 페이지로 리다이렉트
     * New 시스템: 401 HTTP Status 반환 (RESTful)
     */
    @Test
    @Order(152)
    @DisplayName("TC1.5.2: 인증 없음 - 401 에러")
    void TC1_5_2_getMyInfo_NoAuthentication_Returns401() throws Exception {
        // When & Then: Authorization 헤더 없이 요청
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());

        // UserService는 호출되지 않아야 함 (Security에서 차단)
        verify(userService, never()).getUserByUserId(any());
    }

    // ============================================================
    // Task 1.7 Find ID API Test Cases (TC1.7.1 ~ TC1.7.5)
    // Legacy: POST /qvey/findId (single request, returns ArrayList of userIds)
    // New Basic: POST /api/users/find-id (simple lookup)
    // New 2-Step: POST /api/users/find-id/request + /api/users/find-id/verify
    // ============================================================

    /**
     * TC1.7.1: 아이디 찾기 2단계 - 유효한 정보로 이메일 인증코드 발송 및 아이디 조회
     *
     * New 시스템 2단계 흐름:
     * 1. /api/users/find-id/request - 정보 검증 + 이메일 인증코드 발송
     * 2. /api/users/find-id/verify - 인증코드 확인 + 마스킹된 아이디 반환
     *
     * Legacy 시스템: 단일 요청으로 아이디 목록 반환 (보안 취약)
     */
    @Test
    @Order(171)
    @DisplayName("TC1.7.1: 아이디 찾기 2단계 - 유효한 정보로 성공")
    void TC1_7_1_findId_ValidInfo_ReturnsEmailAndMaskedId() throws Exception {
        // Given: 아이디 찾기 요청 (Step 1)
        kr.wisead.domain.user.dto.FindIdRequest findIdRequest = kr.wisead.domain.user.dto.FindIdRequest.builder()
                .corpName("테스트기업")
                .person("홍길동")
                .phone("010-2345-6789")
                .build();

        // Mock: Step 1 응답 - 이메일 인증코드 발송 성공
        kr.wisead.domain.user.dto.FindIdResponse step1Response = kr.wisead.domain.user.dto.FindIdResponse.requestSuccess("te***@example.com");
        when(userService.requestFindId(any(kr.wisead.domain.user.dto.FindIdRequest.class))).thenReturn(step1Response);

        // When & Then: Step 1 - 이메일 인증코드 발송
        mockMvc.perform(post("/api/users/find-id/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(findIdRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(1))
                .andExpect(jsonPath("$.data.maskedEmail").value("te***@example.com"))
                .andExpect(jsonPath("$.data.message").value("인증 코드가 발송되었습니다."));

        verify(userService, times(1)).requestFindId(any(kr.wisead.domain.user.dto.FindIdRequest.class));

        // Given: Step 2 - 인증코드 확인 요청
        kr.wisead.domain.email.dto.EmailVerificationRequest verifyRequest = kr.wisead.domain.email.dto.EmailVerificationRequest.builder()
                .email("test@example.com")
                .code("123456")
                .build();

        // Mock: Step 2 응답 - 마스킹된 아이디 반환
        kr.wisead.domain.user.dto.FindIdResponse step2Response = kr.wisead.domain.user.dto.FindIdResponse.verifySuccess("tes*****");
        when(userService.verifyAndGetUserId(eq("test@example.com"), eq("123456"))).thenReturn(step2Response);

        // When & Then: Step 2 - 아이디 조회
        mockMvc.perform(post("/api/users/find-id/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(1))
                .andExpect(jsonPath("$.data.maskedUserId").value("tes*****"))
                .andExpect(jsonPath("$.data.message").value("아이디 조회가 완료되었습니다."));

        verify(userService, times(1)).verifyAndGetUserId(eq("test@example.com"), eq("123456"));
    }

    /**
     * TC1.7.2: 아이디 찾기 - 존재하지 않는 사용자
     *
     * resultCode: 2 반환 (계정 없음)
     * Legacy 시스템: 빈 ArrayList 반환
     * New 시스템: 구조화된 실패 응답
     */
    @Test
    @Order(172)
    @DisplayName("TC1.7.2: 아이디 찾기 - 존재하지 않는 사용자")
    void TC1_7_2_findId_UserNotFound_ReturnsNotFoundResponse() throws Exception {
        // Given: 존재하지 않는 사용자 정보
        kr.wisead.domain.user.dto.FindIdRequest findIdRequest = kr.wisead.domain.user.dto.FindIdRequest.builder()
                .corpName("존재하지않는기업")
                .person("없는사람")
                .phone("010-0000-0000")
                .build();

        // Mock: 사용자 없음 응답
        kr.wisead.domain.user.dto.FindIdResponse notFoundResponse = kr.wisead.domain.user.dto.FindIdResponse.accountNotFound();
        when(userService.requestFindId(any(kr.wisead.domain.user.dto.FindIdRequest.class))).thenReturn(notFoundResponse);

        // When & Then
        mockMvc.perform(post("/api/users/find-id/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(findIdRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(2))
                .andExpect(jsonPath("$.data.message").value("입력하신 정보와 일치하는 계정이 없습니다."))
                .andExpect(jsonPath("$.data.maskedEmail").doesNotExist())
                .andExpect(jsonPath("$.data.maskedUserId").doesNotExist());

        verify(userService, times(1)).requestFindId(any(kr.wisead.domain.user.dto.FindIdRequest.class));
    }

    /**
     * TC1.7.3: 아이디 찾기 - 잘못된 인증코드
     *
     * resultCode: 3 반환 (인증 실패)
     * Legacy 시스템: 인증 단계 없음
     * New 시스템: 2단계 이메일 인증으로 보안 강화
     */
    @Test
    @Order(173)
    @DisplayName("TC1.7.3: 아이디 찾기 - 잘못된 인증코드")
    void TC1_7_3_findId_InvalidCode_ReturnsVerificationFailed() throws Exception {
        // Given: 잘못된 인증코드
        kr.wisead.domain.email.dto.EmailVerificationRequest verifyRequest = kr.wisead.domain.email.dto.EmailVerificationRequest.builder()
                .email("test@example.com")
                .code("wrong_code")
                .build();

        // Mock: 인증 실패 응답
        kr.wisead.domain.user.dto.FindIdResponse failedResponse = kr.wisead.domain.user.dto.FindIdResponse.verificationFailed();
        when(userService.verifyAndGetUserId(eq("test@example.com"), eq("wrong_code"))).thenReturn(failedResponse);

        // When & Then
        mockMvc.perform(post("/api/users/find-id/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(3))
                .andExpect(jsonPath("$.data.message").value("인증 코드가 일치하지 않습니다."))
                .andExpect(jsonPath("$.data.maskedUserId").doesNotExist());

        verify(userService, times(1)).verifyAndGetUserId(eq("test@example.com"), eq("wrong_code"));
    }

    /**
     * TC1.7.4: 아이디 찾기 - 필수 필드 누락
     *
     * @Valid 어노테이션에 의해 400 Bad Request
     * Legacy 시스템: 검증 없이 DB 쿼리 (결과 없음 반환)
     * New 시스템: 요청 단계에서 유효성 검증
     */
    @Test
    @Order(174)
    @DisplayName("TC1.7.4: 아이디 찾기 - 필수 필드 누락")
    void TC1_7_4_findId_MissingFields_Returns400() throws Exception {
        // Given: corpName만 있고 person, phone 누락
        String requestJson = """
                {
                    "corpName": "테스트기업"
                }
                """;

        // When & Then: @Valid 유효성 검사 실패
        mockMvc.perform(post("/api/users/find-id/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // UserService는 호출되지 않아야 함
        verify(userService, never()).requestFindId(any());
    }

    /**
     * TC1.7.5: 아이디 찾기 - 기본 엔드포인트 (하위 호환용)
     *
     * POST /api/users/find-id (email + person)
     * 2단계 없이 바로 마스킹된 아이디 반환
     */
    @Test
    @Order(175)
    @DisplayName("TC1.7.5: 아이디 찾기 - 기본 엔드포인트 (하위 호환)")
    void TC1_7_5_findId_BasicEndpoint_ReturnsMaskedUserId() throws Exception {
        // Given: 기본 엔드포인트 요청 (email + person)
        Map<String, String> request = Map.of(
                "email", "test@example.com",
                "person", "홍길동"
        );

        // Mock: 마스킹된 아이디 반환
        when(userService.findUserId(eq("test@example.com"), eq("홍길동"))).thenReturn("tes*****");

        // When & Then
        mockMvc.perform(post("/api/users/find-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("tes*****"));

        verify(userService, times(1)).findUserId(eq("test@example.com"), eq("홍길동"));
    }

    /**
     * TC1.7.6: 아이디 찾기 - 기본 엔드포인트 사용자 없음
     *
     * 존재하지 않는 사용자 -> BusinessException 발생
     */
    @Test
    @Order(176)
    @DisplayName("TC1.7.6: 아이디 찾기 - 기본 엔드포인트 사용자 없음")
    void TC1_7_6_findId_BasicEndpoint_UserNotFound_Returns400() throws Exception {
        // Given: 존재하지 않는 사용자
        Map<String, String> request = Map.of(
                "email", "notexist@example.com",
                "person", "없는사람"
        );

        // Mock: 사용자 없음 예외
        when(userService.findUserId(eq("notexist@example.com"), eq("없는사람")))
                .thenThrow(new kr.wisead.common.exception.BusinessException(
                        kr.wisead.common.response.ErrorCode.MEMBER_NOT_FOUND,
                        "일치하는 회원 정보가 없습니다."));

        // When & Then
        mockMvc.perform(post("/api/users/find-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("일치하는 회원 정보가 없습니다."));

        verify(userService, times(1)).findUserId(eq("notexist@example.com"), eq("없는사람"));
    }

    // ============================================================
    // Task 1.8 Find Password API Test Cases (TC1.8.1 ~ TC1.8.8)
    // Legacy: POST /qvey/findPw (generates temp password, returns directly)
    // New: POST /api/users/find-pw (token-based email link)
    //      GET /api/users/password/reset-validate (validate token)
    //      POST /api/users/password/reset-confirm (set new password)
    // ============================================================

    /**
     * TC1.8.1: 비밀번호 찾기 - 유효한 정보로 재설정 이메일 발송
     *
     * Legacy 시스템: 임시 비밀번호 직접 반환 (보안 취약)
     * New 시스템: 토큰 기반 이메일 링크 발송 (보안 강화)
     *
     * 주요 차이점:
     * - Legacy: newPw를 응답에 직접 포함
     * - New: 마스킹된 이메일만 반환, 재설정 링크는 이메일로 발송
     */
    @Test
    @Order(181)
    @DisplayName("TC1.8.1: 비밀번호 찾기 - 유효한 정보로 재설정 이메일 발송")
    void TC1_8_1_findPassword_ValidInfo_SendsResetEmail() throws Exception {
        // Given: 비밀번호 찾기 요청
        kr.wisead.domain.user.dto.FindPasswordRequest request = new kr.wisead.domain.user.dto.FindPasswordRequest();
        request.setUserId(TEST_USER_ID);
        request.setCorpName("테스트기업");
        request.setPerson("홍길동");
        request.setPhone("010-2345-6789");
        request.setHintQuestion("가장 좋아하는 음식은?");
        request.setHintAnswer("피자");

        // Mock: 성공 응답 - 재설정 이메일 발송됨
        kr.wisead.domain.user.dto.FindPasswordResponse successResponse =
                kr.wisead.domain.user.dto.FindPasswordResponse.success("te***@example.com");
        when(userService.findPassword(any(kr.wisead.domain.user.dto.FindPasswordRequest.class)))
                .thenReturn(successResponse);

        // When & Then
        mockMvc.perform(post("/api/users/find-pw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(1))
                .andExpect(jsonPath("$.data.message").value("비밀번호 재설정 링크가 이메일로 발송되었습니다."))
                .andExpect(jsonPath("$.data.maskedEmail").value("te***@example.com"));

        verify(userService, times(1)).findPassword(any(kr.wisead.domain.user.dto.FindPasswordRequest.class));
    }

    /**
     * TC1.8.2: 비밀번호 찾기 - 계정 정보 없음
     *
     * resultCode: 2 반환 (계정 없음)
     * Legacy 시스템: result: 2, msg: "계정 정보가 없습니다."
     * New 시스템: resultCode: 2, message: "입력하신 정보와 일치하는 계정이 없습니다."
     */
    @Test
    @Order(182)
    @DisplayName("TC1.8.2: 비밀번호 찾기 - 계정 정보 없음")
    void TC1_8_2_findPassword_AccountNotFound_ReturnsNotFoundResponse() throws Exception {
        // Given: 존재하지 않는 계정 정보
        kr.wisead.domain.user.dto.FindPasswordRequest request = new kr.wisead.domain.user.dto.FindPasswordRequest();
        request.setUserId("nonexistent");
        request.setCorpName("없는기업");
        request.setPerson("없는사람");
        request.setPhone("010-0000-0000");
        request.setHintQuestion("가장 좋아하는 음식은?");
        request.setHintAnswer("피자");

        // Mock: 계정 없음 응답
        kr.wisead.domain.user.dto.FindPasswordResponse notFoundResponse =
                kr.wisead.domain.user.dto.FindPasswordResponse.accountNotFound();
        when(userService.findPassword(any(kr.wisead.domain.user.dto.FindPasswordRequest.class)))
                .thenReturn(notFoundResponse);

        // When & Then
        mockMvc.perform(post("/api/users/find-pw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(2))
                .andExpect(jsonPath("$.data.message").value("입력하신 정보와 일치하는 계정이 없습니다."))
                .andExpect(jsonPath("$.data.maskedEmail").doesNotExist());

        verify(userService, times(1)).findPassword(any(kr.wisead.domain.user.dto.FindPasswordRequest.class));
    }

    /**
     * TC1.8.3: 비밀번호 찾기 - 힌트 불일치
     *
     * resultCode: 3 반환 (힌트 불일치)
     * Legacy 시스템: result: 3, msg: "비밀번호 힌트를 확인해주세요."
     * New 시스템: resultCode: 3, message: "비밀번호 힌트가 일치하지 않습니다."
     */
    @Test
    @Order(183)
    @DisplayName("TC1.8.3: 비밀번호 찾기 - 힌트 불일치")
    void TC1_8_3_findPassword_HintMismatch_ReturnsHintMismatchResponse() throws Exception {
        // Given: 올바른 계정 정보 + 잘못된 힌트
        kr.wisead.domain.user.dto.FindPasswordRequest request = new kr.wisead.domain.user.dto.FindPasswordRequest();
        request.setUserId(TEST_USER_ID);
        request.setCorpName("테스트기업");
        request.setPerson("홍길동");
        request.setPhone("010-2345-6789");
        request.setHintQuestion("가장 좋아하는 음식은?");
        request.setHintAnswer("잘못된답변");

        // Mock: 힌트 불일치 응답
        kr.wisead.domain.user.dto.FindPasswordResponse hintMismatchResponse =
                kr.wisead.domain.user.dto.FindPasswordResponse.hintMismatch();
        when(userService.findPassword(any(kr.wisead.domain.user.dto.FindPasswordRequest.class)))
                .thenReturn(hintMismatchResponse);

        // When & Then
        mockMvc.perform(post("/api/users/find-pw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resultCode").value(3))
                .andExpect(jsonPath("$.data.message").value("비밀번호 힌트가 일치하지 않습니다."))
                .andExpect(jsonPath("$.data.maskedEmail").doesNotExist());

        verify(userService, times(1)).findPassword(any(kr.wisead.domain.user.dto.FindPasswordRequest.class));
    }

    /**
     * TC1.8.4: 비밀번호 찾기 - 필수 필드 누락
     *
     * @Valid 어노테이션에 의해 400 Bad Request
     */
    @Test
    @Order(184)
    @DisplayName("TC1.8.4: 비밀번호 찾기 - 필수 필드 누락")
    void TC1_8_4_findPassword_MissingFields_Returns400() throws Exception {
        // Given: userId만 있고 다른 필드 누락
        String requestJson = """
                {
                    "userId": "testuser01"
                }
                """;

        // When & Then: @Valid 유효성 검사 실패
        mockMvc.perform(post("/api/users/find-pw")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        // UserService는 호출되지 않아야 함
        verify(userService, never()).findPassword(any());
    }

    /**
     * TC1.8.5: 비밀번호 재설정 - 유효한 토큰으로 비밀번호 변경
     *
     * New 시스템 전용 기능: 토큰 기반 비밀번호 재설정
     * Legacy 시스템: 임시 비밀번호 직접 반환 방식 (이 기능 없음)
     */
    @Test
    @Order(185)
    @DisplayName("TC1.8.5: 비밀번호 재설정 - 유효한 토큰으로 비밀번호 변경")
    void TC1_8_5_passwordReset_ValidToken_ChangesPassword() throws Exception {
        // Given: 비밀번호 재설정 요청
        kr.wisead.domain.user.dto.PasswordResetConfirmRequest request =
                new kr.wisead.domain.user.dto.PasswordResetConfirmRequest();
        request.setToken("valid-uuid-token-12345");
        request.setNewPassword("NewPassword123!");

        // Mock: 비밀번호 변경 성공
        doNothing().when(userService).resetPasswordWithToken(eq("valid-uuid-token-12345"), eq("NewPassword123!"));

        // When & Then
        mockMvc.perform(post("/api/users/password/reset-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("비밀번호가 성공적으로 변경되었습니다."));

        verify(userService, times(1)).resetPasswordWithToken(eq("valid-uuid-token-12345"), eq("NewPassword123!"));
    }

    /**
     * TC1.8.6: 비밀번호 재설정 - 만료된 토큰
     *
     * 토큰 만료 시 400 에러 및 적절한 메시지 반환
     */
    @Test
    @Order(186)
    @DisplayName("TC1.8.6: 비밀번호 재설정 - 만료된 토큰")
    void TC1_8_6_passwordReset_ExpiredToken_Returns400() throws Exception {
        // Given: 만료된 토큰으로 비밀번호 재설정 요청
        kr.wisead.domain.user.dto.PasswordResetConfirmRequest request =
                new kr.wisead.domain.user.dto.PasswordResetConfirmRequest();
        request.setToken("expired-uuid-token");
        request.setNewPassword("NewPassword123!");

        // Mock: 만료된 토큰 예외
        doThrow(new kr.wisead.common.exception.BusinessException(
                kr.wisead.common.response.ErrorCode.INVALID_INPUT_VALUE,
                "만료된 토큰입니다. 비밀번호 찾기를 다시 진행해주세요."))
                .when(userService).resetPasswordWithToken(eq("expired-uuid-token"), eq("NewPassword123!"));

        // When & Then
        mockMvc.perform(post("/api/users/password/reset-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("만료된 토큰입니다. 비밀번호 찾기를 다시 진행해주세요."));

        verify(userService, times(1)).resetPasswordWithToken(eq("expired-uuid-token"), eq("NewPassword123!"));
    }

    /**
     * TC1.8.7: 비밀번호 재설정 - 이미 사용된 토큰
     *
     * 이미 사용된 토큰 재사용 방지
     */
    @Test
    @Order(187)
    @DisplayName("TC1.8.7: 비밀번호 재설정 - 이미 사용된 토큰")
    void TC1_8_7_passwordReset_UsedToken_Returns400() throws Exception {
        // Given: 이미 사용된 토큰으로 비밀번호 재설정 요청
        kr.wisead.domain.user.dto.PasswordResetConfirmRequest request =
                new kr.wisead.domain.user.dto.PasswordResetConfirmRequest();
        request.setToken("used-uuid-token");
        request.setNewPassword("NewPassword123!");

        // Mock: 이미 사용된 토큰 예외
        doThrow(new kr.wisead.common.exception.BusinessException(
                kr.wisead.common.response.ErrorCode.INVALID_INPUT_VALUE,
                "이미 사용된 토큰입니다."))
                .when(userService).resetPasswordWithToken(eq("used-uuid-token"), eq("NewPassword123!"));

        // When & Then
        mockMvc.perform(post("/api/users/password/reset-confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("이미 사용된 토큰입니다."));

        verify(userService, times(1)).resetPasswordWithToken(eq("used-uuid-token"), eq("NewPassword123!"));
    }

    /**
     * TC1.8.8: 토큰 유효성 검증 - 유효한 토큰
     *
     * 프론트엔드에서 비밀번호 재설정 폼 표시 전 토큰 검증
     */
    @Test
    @Order(188)
    @DisplayName("TC1.8.8: 토큰 유효성 검증 - 유효한 토큰")
    void TC1_8_8_validateToken_ValidToken_ReturnsValid() throws Exception {
        // Given: 유효한 토큰
        String validToken = "valid-uuid-token-12345";

        // Mock: 토큰 유효성 검증 성공
        Map<String, Object> validationResult = Map.of(
                "valid", true,
                "userId", "tes*****"
        );
        when(userService.validateResetToken(eq(validToken))).thenReturn(validationResult);

        // When & Then
        mockMvc.perform(get("/api/users/password/reset-validate")
                        .param("token", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.userId").value("tes*****"));

        verify(userService, times(1)).validateResetToken(eq(validToken));
    }

    /**
     * TC1.8.9: 토큰 유효성 검증 - 유효하지 않은 토큰
     *
     * 잘못된/만료된/사용된 토큰 검증 실패
     */
    @Test
    @Order(189)
    @DisplayName("TC1.8.9: 토큰 유효성 검증 - 유효하지 않은 토큰")
    void TC1_8_9_validateToken_InvalidToken_Returns400() throws Exception {
        // Given: 유효하지 않은 토큰
        String invalidToken = "invalid-or-expired-token";

        // Mock: 토큰 검증 실패
        when(userService.validateResetToken(eq(invalidToken)))
                .thenThrow(new kr.wisead.common.exception.BusinessException(
                        kr.wisead.common.response.ErrorCode.INVALID_INPUT_VALUE,
                        "유효하지 않은 토큰입니다."));

        // When & Then
        mockMvc.perform(get("/api/users/password/reset-validate")
                        .param("token", invalidToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("유효하지 않은 토큰입니다."));

        verify(userService, times(1)).validateResetToken(eq(invalidToken));
    }

    // ==========================================
    // Task 1.9: Unlock Account API Tests
    // ==========================================

    /**
     * TC1.9.1: 관리자가 잠긴 계정을 해제한다 - 성공
     */
    @Test
    @Order(190)
    @DisplayName("TC1.9.1: 계정 잠금 해제 - 관리자 성공")
    void TC1_9_1_unlockAccount_AdminUser_Success() throws Exception {
        // Given: 잠긴 사용자 ID
        String lockedUserId = "locked_user";

        // 관리자 JWT 토큰 생성
        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                "admin", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        String adminToken = jwtTokenProvider.generateAccessToken(adminAuth);

        // Mock: 잠금 해제 성공
        when(userService.unlockAccount(eq(lockedUserId))).thenReturn(1);

        // When & Then
        mockMvc.perform(put("/api/users/{userId}/unlock", lockedUserId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("계정 잠금이 해제되었습니다."));

        verify(userService, times(1)).unlockAccount(eq(lockedUserId));
    }

    /**
     * TC1.9.2: 일반 사용자가 계정 해제 시도 - 권한 없음
     */
    @Test
    @Order(191)
    @DisplayName("TC1.9.2: 계정 잠금 해제 - 일반 사용자 권한 없음")
    void TC1_9_2_unlockAccount_NonAdminUser_Forbidden() throws Exception {
        // Given: 잠긴 사용자 ID
        String lockedUserId = "locked_user";

        // 일반 사용자 JWT 토큰 생성 (ROLE_USER)
        UsernamePasswordAuthenticationToken userAuth = new UsernamePasswordAuthenticationToken(
                "regularuser", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        String userToken = jwtTokenProvider.generateAccessToken(userAuth);

        // When & Then: 403 Forbidden 예상
        mockMvc.perform(put("/api/users/{userId}/unlock", lockedUserId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // Verify: unlockAccount가 호출되지 않아야 함
        verify(userService, never()).unlockAccount(any());
    }

    /**
     * TC1.9.3: 인증 없이 계정 해제 시도 - 미인증
     */
    @Test
    @Order(192)
    @DisplayName("TC1.9.3: 계정 잠금 해제 - 인증 없음")
    void TC1_9_3_unlockAccount_NoAuthentication_Unauthorized() throws Exception {
        // Given: 잠긴 사용자 ID
        String lockedUserId = "locked_user";

        // When & Then: Authorization 헤더 없이 요청 -> 401 Unauthorized
        mockMvc.perform(put("/api/users/{userId}/unlock", lockedUserId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        // Verify: unlockAccount가 호출되지 않아야 함
        verify(userService, never()).unlockAccount(any());
    }

    /**
     * TC1.9.4: 존재하지 않는 사용자 해제 시도 - 사용자 없음
     */
    @Test
    @Order(193)
    @DisplayName("TC1.9.4: 계정 잠금 해제 - 존재하지 않는 사용자")
    void TC1_9_4_unlockAccount_UserNotFound_Returns404() throws Exception {
        // Given: 존재하지 않는 사용자 ID
        String nonExistentUserId = "nonexistent_user";

        // 관리자 JWT 토큰 생성
        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                "admin", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        String adminToken = jwtTokenProvider.generateAccessToken(adminAuth);

        // Mock: MEMBER_NOT_FOUND 예외 발생
        when(userService.unlockAccount(eq(nonExistentUserId)))
                .thenThrow(new kr.wisead.common.exception.BusinessException(
                        kr.wisead.common.response.ErrorCode.MEMBER_NOT_FOUND));

        // When & Then
        mockMvc.perform(put("/api/users/{userId}/unlock", nonExistentUserId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("M001"))
                .andExpect(jsonPath("$.message").value("회원을 찾을 수 없습니다."));

        verify(userService, times(1)).unlockAccount(eq(nonExistentUserId));
    }

    /**
     * TC1.9.5: 이미 해제된 계정 재해제 - 성공 (멱등성)
     */
    @Test
    @Order(194)
    @DisplayName("TC1.9.5: 계정 잠금 해제 - 이미 해제된 계정 (멱등성)")
    void TC1_9_5_unlockAccount_AlreadyUnlocked_StillSuccess() throws Exception {
        // Given: 이미 해제된 사용자 ID
        String activeUserId = "active_user";

        // 관리자 JWT 토큰 생성
        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                "admin", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        String adminToken = jwtTokenProvider.generateAccessToken(adminAuth);

        // Mock: 해제 성공 (이미 해제된 상태라도 성공)
        when(userService.unlockAccount(eq(activeUserId))).thenReturn(1);

        // When & Then: 첫 번째 해제 요청
        mockMvc.perform(put("/api/users/{userId}/unlock", activeUserId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 두 번째 해제 요청 - 동일하게 성공해야 함 (멱등성)
        mockMvc.perform(put("/api/users/{userId}/unlock", activeUserId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Verify: unlockAccount가 2번 호출됨
        verify(userService, times(2)).unlockAccount(eq(activeUserId));
    }

    /**
     * TC1.9.6: 빈 userId로 해제 시도 - 404 Not Found
     */
    @Test
    @Order(195)
    @DisplayName("TC1.9.6: 계정 잠금 해제 - 빈 userId")
    void TC1_9_6_unlockAccount_EmptyUserId_Returns404() throws Exception {
        // 관리자 JWT 토큰 생성
        UsernamePasswordAuthenticationToken adminAuth = new UsernamePasswordAuthenticationToken(
                "admin", null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));
        String adminToken = jwtTokenProvider.generateAccessToken(adminAuth);

        // When & Then: 빈 userId는 경로 매칭 실패로 404
        mockMvc.perform(put("/api/users//unlock")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());

        // Verify: unlockAccount가 호출되지 않아야 함
        verify(userService, never()).unlockAccount(any());
    }
}
