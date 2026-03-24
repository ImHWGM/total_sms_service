package kr.wisead.config;

import java.util.Arrays;
import java.util.List;
import kr.wisead.security.jwt.JwtAccessDeniedHandler;
import kr.wisead.security.jwt.JwtAuthenticationEntryPoint;
import kr.wisead.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Spring Security 설정 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
  private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

  /** 인증 없이 접근 가능한 경로 */
  private static final String[] PUBLIC_ENDPOINTS = {
    // 인증 관련
    "/api/auth/**", // 로그인, 회원가입, 사업자번호 검증 등
    "/api/public/**",
    "/api/users/find-id", // 아이디 찾기 (레거시)
    "/api/users/find-id/**", // 아이디 찾기 (2단계 플로우)
    "/api/users/find-pw", // 비밀번호 찾기
    "/api/users/password/reset-validate", // 비밀번호 재설정 토큰 검증
    "/api/users/password/reset-confirm", // 비밀번호 재설정 확인
    "/api/email/verification/**", // 이메일 인증 (회원가입, 로그인 시 사용)
    "/api/unsubscribe", // 이메일 수신거부
    "/unsubscribe", // 이메일 수신거부 (레거시 호환)

    // 설문 참여 (비로그인 허용) - /api/survey/users 관리자 엔드포인트 제외
    "/api/survey/code/**", // 이벤트코드로 설문 조회
    "/api/survey/qr/**", // QR코드로 설문 조회
    "/api/survey/key/**", // 사용자키로 설문 조회
    "/api/survey/auth/**", // 범용인증
    "/api/survey/*/submit", // 설문 제출
    "/api/survey/*/participants", // 참여자 목록 조회
    "/api/survey/*/absentees", // 미참여자 조회
    "/api/survey/answers/**", // 설문 응답/통계
    // 설문 참여자 프론트 엔드포인트 (비로그인 허용)
    "/api/survey/users/key/**", // 사용자키로 참여자 조회
    "/api/survey/users/validate-key", // 사용자키 유효성 검증
    "/api/survey/users/start-time", // 설문 접속시간 기록
    "/api/survey/users/auth-time", // 설문 인증시간 기록
    "/api/survey/users/check-phone", // 전화번호 중복확인
    "/api/survey/users/auth/**", // 범용인증 상태/조회
    "/api/front/**",

    // 행사 체크인 (비로그인 허용 - QR 스캔)
    "/api/events/*/check/**",
    "/api/events/*/staff-auth", // 스태프 인증코드 검증 (비로그인 허용)
    "/api/events/*/staff-checkin/**", // 스태프 체크인 (쿠키 인증으로 전환)

    // 문의 등록 (비로그인 허용)
    "/api/inquiry",
    "/api/file/survey/answer", // 설문 응답 파일 업로드 (비로그인)

    // ARS 수신거부 (외부 ARS 시스템 호출)
    "/ars/**",

    // 결제 콜백 (PG사 호출)
    "/api/payment/kg/**",
    "/api/payment/callback",

    // 파일 다운로드/서빙
    "/files/**",
    "/survey/**", // 설문 이미지 (DB 경로 직접 접근)
    "/mmsfile/**", // MMS 파일
    "/template/**", // 템플릿 이미지
    "/bizreg/**", // 사업자등록증
    "/qrcode/**", // QR 코드

    // Swagger/API 문서
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/v3/api-docs/**",
    "/swagger-resources/**",

    // Actuator (상태 체크)
    "/actuator/health",
    "/actuator/info",

    // 정적 리소스
    "/favicon.ico",
    "/error"
  };

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // CSRF 비활성화 (JWT 사용)
        .csrf(AbstractHttpConfigurer::disable)

        // CORS 설정
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))

        // 세션 사용 안함 (Stateless)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 예외 처리
        .exceptionHandling(
            exception ->
                exception
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                    .accessDeniedHandler(jwtAccessDeniedHandler))

        // 요청 인가 설정
        .authorizeHttpRequests(
            auth ->
                // 관리자 설문 엔드포인트 보호 (PUBLIC_ENDPOINTS 와일드카드 패턴과 충돌 방지)
                // /api/survey/*/absentees 등이 /api/survey/users/absentees를 매칭하는 것을 방지
                auth.requestMatchers("/api/survey/users")
                    .authenticated()
                    .requestMatchers("/api/survey/users/completed")
                    .authenticated()
                    .requestMatchers("/api/survey/users/absentees")
                    .authenticated()
                    .requestMatchers("/api/survey/users/count")
                    .authenticated()
                    .requestMatchers("/api/survey/users/batch")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/survey/users/*")
                    .authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/survey/users/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/api/survey/users/**")
                    .authenticated()
                    .requestMatchers(PUBLIC_ENDPOINTS)
                    .permitAll()
                    // 인증된 사용자면 접근 가능 (내부 비즈니스 로직에서 권한별 분기)
                    .requestMatchers("/api/admin/selectable-user-ids")
                    .authenticated()
                    .requestMatchers("/api/admin/level")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/admin/logs/phone-masking")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/admin/logs/download")
                    .authenticated()
                    // 나머지 관리자 API는 ADMIN 역할 필요
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())

        // JWT 필터 추가
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  /** CORS 설정 (React Native 연동) */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    // 허용할 Origin (개발 환경)
    configuration.setAllowedOrigins(
        Arrays.asList(
            "http://localhost:3000", // React 개발 서버
            "http://localhost:8081", // React Native Metro
            "http://localhost:19006", // Expo Web
            "http://10.0.2.2:8100", // Android Emulator
            "exp://localhost:19000" // Expo
            ));

    // 모든 Origin 패턴 허용 (운영 환경에서는 특정 도메인으로 제한 권장)
    configuration.setAllowedOriginPatterns(List.of("*"));

    // 허용할 HTTP 메소드
    configuration.setAllowedMethods(
        Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

    // 허용할 헤더
    configuration.setAllowedHeaders(List.of("*"));

    // 노출할 헤더 (클라이언트에서 접근 가능)
    configuration.setExposedHeaders(
        Arrays.asList("Authorization", "X-Total-Count", "X-Page-Number", "X-Page-Size"));

    // 인증 정보 포함 허용
    configuration.setAllowCredentials(true);

    // Preflight 요청 캐시 시간 (1시간)
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
      throws Exception {
    return config.getAuthenticationManager();
  }
}
