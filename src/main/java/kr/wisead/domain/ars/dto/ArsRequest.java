package kr.wisead.domain.ars.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ARS 수신거부 요청 DTO
 * ARS 시스템에서 전송하는 파라미터
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArsRequest {

    private String tId;             // 트랜잭션 ID
    private String tTime;           // 요청 시간 (yyyyMMddHHmmss)
    private String menuName;        // 080 수신거부 번호
    private String ani;             // 발신자 전화번호 (수신거부 요청자)
    private String dtmfCnt;         // DTMF 입력 개수
    private String dtmf1;           // 상점 코드 (code-reject 방식에서 사용)
}
