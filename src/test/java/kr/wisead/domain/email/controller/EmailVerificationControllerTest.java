package kr.wisead.domain.email.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import kr.wisead.domain.email.dto.EmailVerificationStatus;
import kr.wisead.domain.email.service.EmailService;
import kr.wisead.domain.email.service.PreSignupEmailAuthService;
import kr.wisead.security.jwt.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * EmailController 단위 테스트 — 회원가입 흐름이 PreSignupEmailAuthService(key=email)로 라우팅됨 검증.
 *
 * <p>plan §4 Phase B-0-8: signupFlowEmailKeySpace.
 */
@WebMvcTest(
    controllers = EmailController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class},
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = {JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("EmailController: 회원가입 이메일 인증 라우팅")
class EmailVerificationControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private PreSignupEmailAuthService emailAuthService;
  @MockitoBean private EmailService emailService;

  private static final String EMAIL = "signup@example.com";

  @Test
  @DisplayName(
      "signupFlowEmailKeySpace: POST /verification → PreSignupEmailAuthService.sendVerificationCode"
          + " 호출")
  void signupFlow_sendVerificationCode_routesToPreSignup() throws Exception {
    when(emailAuthService.sendVerificationCode(EMAIL)).thenReturn(true);

    mockMvc
        .perform(
            post("/api/email/verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", EMAIL))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(emailAuthService).sendVerificationCode(eq(EMAIL));
  }

  @Test
  @DisplayName("signupFlow: POST /verification/verify → PreSignupEmailAuthService.verifyCode 호출")
  void signupFlow_verifyCode_routesToPreSignup() throws Exception {
    when(emailAuthService.verifyCode(EMAIL, "A1B2C3")).thenReturn(true);

    mockMvc
        .perform(
            post("/api/email/verification/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", EMAIL, "code", "A1B2C3"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(true));

    verify(emailAuthService).verifyCode(eq(EMAIL), eq("A1B2C3"));
  }

  @Test
  @DisplayName(
      "signupFlow: GET /verification/status → PreSignupEmailAuthService.getVerificationStatus 호출")
  void signupFlow_getStatus_routesToPreSignup() throws Exception {
    when(emailAuthService.getVerificationStatus(EMAIL))
        .thenReturn(new EmailVerificationStatus(true, 240, 0, 5));

    mockMvc
        .perform(get("/api/email/verification/status").param("email", EMAIL))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.codeSent").value(true))
        .andExpect(jsonPath("$.data.remainingAttempts").value(5));

    verify(emailAuthService).getVerificationStatus(eq(EMAIL));
  }

  @Test
  @DisplayName(
      "signupFlow: POST /verification/resend → PreSignupEmailAuthService.resendVerificationCode 호출")
  void signupFlow_resend_routesToPreSignup() throws Exception {
    when(emailAuthService.resendVerificationCode(EMAIL)).thenReturn(true);

    mockMvc
        .perform(
            post("/api/email/verification/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", EMAIL))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    verify(emailAuthService).resendVerificationCode(eq(EMAIL));
  }
}
