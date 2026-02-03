package kr.wisead.domain.survey.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** KCP 인증 응답 DTO */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KcpAuthResponse {

  private boolean success; // 인증 성공 여부
  private String resultCode; // 결과 코드 (0000: 성공)
  private String resultMessage; // 결과 메시지

  // 인증된 사용자 정보
  private String phoneNo; // 전화번호
  private String userName; // 이름
  private String birthDay; // 생년월일 (YYYYMMDD)
  private String sexCode; // 성별코드 (01:남, 02:여)
  private String localCode; // 내/외국인 (01:내국인, 02:외국인)
  private String commId; // 이동통신사 코드

  // 연동 정보
  private String ci; // CI (Connecting Information)
  private String di; // DI (Duplication Information)

  // 설문 연동용
  private String userKey; // 사용자 키
  private String eventCode; // 이벤트 코드

  // === KCP 폼 제출용 필드 (Init 응답) ===
  private String ordrIdxx; // 주문번호 (유니크)
  private String siteCd; // KCP 사이트 코드
  private String reqTx; // 요청 타입 (cert)
  private String certMethod; // 인증 방식 (01: 휴대폰)
  private String webSiteid; // 웹 사이트 ID
  private String fixCommid; // 고정 통신사 (빈값 = 선택 가능)

  @JsonProperty("Ret_URL")
  private String retUrl; // 콜백 URL

  private String cerTradeType; // 거래 타입
  private String upHash; // 위변조 방지 해시값
  private String certOtpUse; // OTP 사용 여부
  private String certEncUseExt; // 암호화 확장 사용
  private String kcpCertGwUrl; // KCP Gateway URL

  /** 실패 응답 생성 */
  public static KcpAuthResponse fail(String resultCode, String resultMessage) {
    return KcpAuthResponse.builder()
        .success(false)
        .resultCode(resultCode)
        .resultMessage(resultMessage)
        .build();
  }
}
