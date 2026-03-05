package kr.wisead.domain.ars.dto;

import kr.wisead.domain.ars.entity.BlockedSender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 수신거부 조회 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockedSenderResponse {

    private String ani;             // 수신거부 번호 (복호화됨)
    private String storeCode;       // 상점 코드
    private String storeId;
    private String menuName;        // 080 번호
    private String regDate;         // 등록일시

    public static BlockedSenderResponse from(BlockedSender entity, String decryptedAni,
        String storeId) {
        if (entity == null) return null;

        return BlockedSenderResponse.builder()
                .ani(decryptedAni)
                .storeCode(entity.getDtmf1())
            .storeId(storeId)
                .menuName(entity.getMenuName())
                .regDate(entity.getTTime())
                .build();
    }
}
