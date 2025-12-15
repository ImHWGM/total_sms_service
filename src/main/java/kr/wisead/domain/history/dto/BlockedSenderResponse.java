package kr.wisead.domain.history.dto;

import kr.wisead.domain.history.entity.BlockedSender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 수신거부 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockedSenderResponse {

    private String ani;             // 수신거부 번호
    private String menuName;        // 080 번호
    private String regDate;         // 등록일시

    public static BlockedSenderResponse from(BlockedSender entity) {
        if (entity == null) return null;

        return BlockedSenderResponse.builder()
                .ani(entity.getAni())
                .menuName(entity.getMenuName())
                .regDate(entity.getTTime())
                .build();
    }
}
