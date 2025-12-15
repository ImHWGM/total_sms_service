package kr.wisead.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 관리자 권한 레벨 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminLevelResponse {

    private String userId;
    private Integer userLevel;
    private String userLevelName;
    private boolean isAdmin;
    private boolean isSuperAdmin;
}
