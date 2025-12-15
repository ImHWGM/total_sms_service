package kr.wisead.domain.history.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 수신거부 Entity (blocked_senders 테이블)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockedSender {

    private String ani;             // 수신거부 번호
    private String dtmf1;           // 스토어 코드
    private String menuName;        // 080 번호
    private String tTime;           // 등록일시
}
