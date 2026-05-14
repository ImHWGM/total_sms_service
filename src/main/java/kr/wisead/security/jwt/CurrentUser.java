package kr.wisead.security.jwt;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 메서드 파라미터에 인증된 사용자 정보({@link JwtPrincipal})를 주입한다.
 *
 * <p>JWT subject 가 user.seq 숫자 문자열로 발급되므로, 이 어노테이션을 사용하면 컨트롤러에서 매번 {@code
 * userDetails.getUsername()} → seq/userId 변환을 반복할 필요가 없다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {}
