package kr.wisead.domain.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 액션 로그 기록 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionLogRequest {

    private String menuName;         // 메뉴명
    private String actionType;       // 액션 타입 (R: 조회, C: 생성, U: 수정, D: 삭제)
    private String actionReason;     // 액션 사유 (다운로드 사유 등)
    private String menuUrl;          // 메뉴 URL
    private String code;             // 상태 코드
    private String referer;          // 리퍼러
}
