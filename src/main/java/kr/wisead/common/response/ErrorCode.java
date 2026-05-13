package kr.wisead.common.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 에러 코드 정의 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  // Common (C)
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력입니다."),
  INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
  INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "C002", "잘못된 타입입니다."),
  MISSING_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C003", "필수 입력값이 누락되었습니다."),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C004", "허용되지 않은 HTTP 메소드입니다."),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C005", "서버 내부 오류가 발생했습니다."),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C005", "서버 내부 오류가 발생했습니다."),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C006", "요청한 리소스를 찾을 수 없습니다."),
  DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "C007", "중복된 리소스입니다."),

  // Authentication (A)
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "유효하지 않은 토큰입니다."),
  EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "만료된 토큰입니다."),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "A004", "접근 권한이 없습니다."),
  LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "A005", "로그인에 실패했습니다."),
  ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "A006", "비활성화된 계정입니다."),
  ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "A007", "잠긴 계정입니다."),

  // Member (M)
  MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "회원을 찾을 수 없습니다."),
  DUPLICATE_USER_ID(HttpStatus.CONFLICT, "M002", "이미 사용 중인 아이디입니다."),
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "M003", "이미 등록된 이메일입니다."),
  DUPLICATE_PHONE(HttpStatus.CONFLICT, "M003", "이미 등록된 전화번호입니다."),
  INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "M004", "비밀번호가 일치하지 않습니다."),
  PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "M005", "비밀번호 확인이 일치하지 않습니다."),

  // Message (MSG)
  MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "MSG001", "메시지를 찾을 수 없습니다."),
  MESSAGE_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "MSG002", "메시지 전송에 실패했습니다."),
  TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "MSG003", "템플릿을 찾을 수 없습니다."),
  INVALID_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "MSG004", "유효하지 않은 전화번호입니다."),

  // Survey (S)
  SURVEY_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "설문을 찾을 수 없습니다."),
  SURVEY_CLOSED(HttpStatus.BAD_REQUEST, "S002", "종료된 설문입니다."),
  ALREADY_ANSWERED(HttpStatus.CONFLICT, "S003", "이미 응답한 설문입니다."),
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "S004", "서비스를 일시적으로 이용할 수 없습니다."),
  KEYPAD_EXPIRED(HttpStatus.BAD_REQUEST, "S005", "키패드 세션이 만료되었습니다."),
  KEYPAD_INVALID(HttpStatus.BAD_REQUEST, "S006", "유효하지 않은 키패드 세션입니다."),

  // Payment (P)
  PAYMENT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "P001", "결제에 실패했습니다."),
  INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "P002", "잔액이 부족합니다."),
  REFUND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "P003", "환불에 실패했습니다."),

  // File (F)
  FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "F001", "파일을 찾을 수 없습니다."),
  FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "F002", "파일 업로드에 실패했습니다."),
  INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "F003", "허용되지 않은 파일 형식입니다."),
  FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "F004", "파일 크기가 초과되었습니다."),
  FILE_READ_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "F005", "파일 읽기에 실패했습니다."),
  FILE_WRITE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "F006", "파일 쓰기에 실패했습니다.");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
