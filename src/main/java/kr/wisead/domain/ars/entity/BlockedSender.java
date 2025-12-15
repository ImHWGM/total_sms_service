package kr.wisead.domain.ars.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 수신거부 Entity (BLOCKED_SENDERS 테이블)
 * ARS를 통해 등록된 광고 문자 수신거부 번호
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockedSender {

    private String tTime;           // 등록 시간 (yyyyMMddHHmmss)
    private String ani;             // 수신거부 전화번호 (암호화됨)
    private String dtmf1;           // 상점 코드 (스토어 코드)
    private String menuName;        // 080 수신거부 번호
}
