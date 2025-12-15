package kr.wisead.domain.ars.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ARS 응답 DTO
 * ARS 시스템으로 전송하는 응답
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArsResponse {

    private String tId;             // 트랜잭션 ID
    private String tTime;           // 요청 시간
    private String menuName;        // 080 수신거부 번호
    private String result;          // 결과 코드 (0: 성공)
    private String actionType;      // 액션 타입 (2: 재입력, 3: 종료)
    private String nextMenu;        // 다음 메뉴
    private String mentFlag;        // 멘트 플래그
    private String mentCnt;         // 멘트 개수
    private List<String> ments;     // 멘트 목록

    /**
     * 성공 응답 생성
     */
    public static ArsResponse success(String tId, String tTime, String menuName,
                                       String corpName, String dateStr) {
        return ArsResponse.builder()
                .tId(tId)
                .tTime(tTime)
                .menuName(menuName)
                .result("0")
                .actionType("3")
                .nextMenu("")
                .mentFlag("0")
                .mentCnt("3")
                .ments(List.of(
                        "T_" + corpName,
                        "D_" + dateStr,
                        "F_succ"
                ))
                .build();
    }

    /**
     * 실패 응답 생성 (휴대폰 번호 아님)
     */
    public static ArsResponse failNotCellPhone(String tId, String tTime, String menuName) {
        return ArsResponse.builder()
                .tId(tId)
                .tTime(tTime)
                .menuName(menuName)
                .result("0")
                .actionType("3")
                .nextMenu("")
                .mentFlag("0")
                .mentCnt("1")
                .ments(List.of("F_fail"))
                .build();
    }

    /**
     * 실패 응답 생성 (잘못된 상점코드)
     */
    public static ArsResponse failInvalidStoreCode(String tId, String tTime, String menuName) {
        return ArsResponse.builder()
                .tId(tId)
                .tTime(tTime)
                .menuName(menuName)
                .result("0")
                .actionType("2")  // 재입력 요청
                .nextMenu("")
                .mentFlag("0")
                .mentCnt("1")
                .ments(List.of("F_nonumber"))
                .build();
    }
}
