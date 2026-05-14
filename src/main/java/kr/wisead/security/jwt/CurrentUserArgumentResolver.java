package kr.wisead.security.jwt;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.UserIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link CurrentUser} 어노테이션이 붙은 {@link JwtPrincipal} 파라미터에 대해 현재 인증된 사용자 정보를 주입한다.
 *
 * <p>JWT subject(=user.seq 문자열)를 한 곳에서 해석하여 seq + userId 를 묶어 전달한다.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

  private final UserIdResolver userIdResolver;

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(CurrentUser.class)
        && parameter.getParameterType().equals(JwtPrincipal.class);
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "인증 정보가 없습니다.");
    }
    Integer seq = userIdResolver.fromJwtUsername(authentication.getName());
    if (seq == null) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "유효하지 않은 인증 정보입니다.");
    }
    String userId = userIdResolver.toUserId(seq);
    return new JwtPrincipal(seq, userId);
  }
}
