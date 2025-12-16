package kr.wisead.domain.survey.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * KCP 인증 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
public class KcpAuthRequest {

    private String siteCd;          // 사이트 코드
    private String ordrIdxx;        // 주문번호 (유니크)
    private String certNo;          // 인증번호
    private String encCertData2;    // 암호화된 인증 데이터
    private String dnHash;          // 검증용 해시

    private String eventCode;       // 이벤트 코드 (설문 연동용)
    private String userKey;         // 사용자 키 (설문 연동용)
}
