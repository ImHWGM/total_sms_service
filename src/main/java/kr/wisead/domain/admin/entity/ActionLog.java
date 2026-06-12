package kr.wisead.domain.admin.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 액션 로그 Entity (ACTION_LOG)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActionLog {

    private Long seq; // 시퀀스
    private String menuName; // 메뉴명
    private String actionType; // 액션 타입 (R: 조회, C: 생성, U: 수정, D: 삭제)
    private String actionReason; // 액션 사유
    private String searchCondition; // 검색 조건 (쿼리스트링 원문)
    private String menuUrl; // 메뉴 URL
    private String code; // 상태 코드 (200, 401, 500 등)
    private String referer; // 리퍼러
    private String userId; // 사용자 ID
    private String userName; // 사용자 이름
    private String ip; // IP 주소
    private LocalDateTime regDate; // 등록일시
    private String corpName; // 회사명
}
