package kr.wisead.config;

import java.util.List;
import kr.wisead.common.interceptor.AccessLogInterceptor;
import kr.wisead.security.jwt.CurrentUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 커스텀 Spring MVC 설정. {@link CurrentUserArgumentResolver} 등 사용자 정의 argument resolver 등록. */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

  private final CurrentUserArgumentResolver currentUserArgumentResolver;
  private final AccessLogInterceptor accessLogInterceptor;

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(currentUserArgumentResolver);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(accessLogInterceptor).addPathPatterns("/api/**");
  }
}
