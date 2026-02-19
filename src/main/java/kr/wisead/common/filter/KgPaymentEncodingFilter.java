package kr.wisead.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * KG 모빌리언스 결제 콜백 EUC-KR 인코딩 필터
 *
 * <p>KG PG는 콜백 데이터를 EUC-KR로 전송하지만, Spring Boot의 CharacterEncodingFilter가 UTF-8로 강제 설정하여 한글이 깨지는 문제를
 * 해결합니다. CharacterEncodingFilter 이후, 파라미터 파싱 전에 EUC-KR로 재설정합니다.
 */
public class KgPaymentEncodingFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    request.setCharacterEncoding("EUC-KR");
    filterChain.doFilter(request, response);
  }
}
