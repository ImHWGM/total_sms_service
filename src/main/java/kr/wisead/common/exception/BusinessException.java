package kr.wisead.common.exception;

import kr.wisead.common.response.ErrorCode;
import lombok.Getter;

/** 비즈니스 로직 예외 */
@Getter
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;
  private final String customMessage;

  /** 선택적 추가 페이로드 (LoginFailureResponse 등 상세 응답 전달용). */
  private final Object data;

  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage());
    this.errorCode = errorCode;
    this.customMessage = null;
    this.data = null;
  }

  public BusinessException(ErrorCode errorCode, String customMessage) {
    super(customMessage);
    this.errorCode = errorCode;
    this.customMessage = customMessage;
    this.data = null;
  }

  public BusinessException(ErrorCode errorCode, String customMessage, Object data) {
    super(customMessage);
    this.errorCode = errorCode;
    this.customMessage = customMessage;
    this.data = data;
  }

  public BusinessException(ErrorCode errorCode, Throwable cause) {
    super(errorCode.getMessage(), cause);
    this.errorCode = errorCode;
    this.customMessage = null;
    this.data = null;
  }

  public String getEffectiveMessage() {
    return customMessage != null ? customMessage : errorCode.getMessage();
  }
}
