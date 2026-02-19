package kr.wisead.config;

import kr.wisead.common.filter.ApiLoggingFilter;
import kr.wisead.common.filter.KgPaymentEncodingFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** 필터 등록 설정 */
@Configuration
@RequiredArgsConstructor
public class FilterConfig {

  private final ApiLoggingFilter apiLoggingFilter;

  /** API 로깅 필터 등록 - /api/** 경로에만 적용 - 가장 먼저 실행되도록 우선순위 설정 */
  @Bean
  public FilterRegistrationBean<ApiLoggingFilter> apiLoggingFilterRegistration() {
    FilterRegistrationBean<ApiLoggingFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(apiLoggingFilter);
    registration.addUrlPatterns("/api/*");
    registration.setName("apiLoggingFilter");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  /**
   * KG 모빌리언스 결제 콜백 EUC-KR 인코딩 필터 - /api/payment/kg/* 경로에만 적용 - CharacterEncodingFilter(UTF-8) 이후,
   * 파라미터 파싱 전에 EUC-KR로 재설정
   */
  @Bean
  public FilterRegistrationBean<KgPaymentEncodingFilter> kgPaymentEncodingFilterRegistration() {
    FilterRegistrationBean<KgPaymentEncodingFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new KgPaymentEncodingFilter());
    registration.addUrlPatterns("/api/payment/kg/*");
    registration.setName("kgPaymentEncodingFilter");
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return registration;
  }
}
