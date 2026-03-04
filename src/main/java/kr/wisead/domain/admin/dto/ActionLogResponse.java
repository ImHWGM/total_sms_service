package kr.wisead.domain.admin.dto;

import kr.wisead.domain.admin.entity.ActionLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 액션 로그 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionLogResponse {

    private Long seq;
    private String menuName;
    private String actionType;
    private String actionTypeName;   // 액션 타입 명칭
    private String actionReason;
    private String menuUrl;
    private String code;
    private String referer;
    private String userId;
    private String userName;
    private String ip;
    private LocalDateTime regDate;
    private String corpName;        // 회사명

    public static ActionLogResponse from(ActionLog entity) {
        if (entity == null) return null;

        String actionTypeName = switch (entity.getActionType()) {
            case "R" -> "조회";
            case "C" -> "생성";
            case "U" -> "수정";
            case "D" -> "삭제";
            default -> entity.getActionType();
        };

        return ActionLogResponse.builder()
                .seq(entity.getSeq())
                .menuName(entity.getMenuName())
                .actionType(entity.getActionType())
                .actionTypeName(actionTypeName)
                .actionReason(entity.getActionReason())
                .menuUrl(entity.getMenuUrl())
                .code(entity.getCode())
                .referer(entity.getReferer())
                .userId(entity.getUserId())
                .userName(entity.getUserName())
                .ip(entity.getIp())
                .regDate(entity.getRegDate())
            .corpName(entity.getCorpName())
                .build();
    }
}
