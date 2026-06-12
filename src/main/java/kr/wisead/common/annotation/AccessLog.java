package kr.wisead.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 개인정보 접근 로그 자동 기록 대상 표시.
 *
 * <p>이 어노테이션이 붙은 컨트롤러 메서드 호출 시 {@code AccessLogInterceptor}가 ACTION_LOG에
 * 접속 기록(누가/언제/어디서/검색조건)을 남긴다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AccessLog {

  /** 접속 메뉴명 (예: "개인정보취합 발송조회"). ACTION_LOG.MENU_NAME 에 기록된다. */
  String menuName();

  /** 액션 타입. 기본 "R"(조회). ACTION_LOG.ACTION_TYPE 에 기록된다. */
  String actionType() default "R";
}
