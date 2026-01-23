package kr.wisead.domain.ars.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** ARS 응답 DTO ARS 시스템으로 전송하는 응답 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArsResponse {

  private String tId; // 트랜잭션 ID
  private String tTime; // 요청 시간
  private String menuName; // 080 수신거부 번호
  private String result; // 결과 코드 (0: 성공)
  private String actionType; // 액션 타입 (2: 재입력, 3: 종료)
  private String nextMenu; // 다음 메뉴
  private String mentFlag; // 멘트 플래그
  private String mentCnt; // 멘트 개수
  private List<String> ments; // 멘트 목록

  /** 성공 응답 생성 */
  public static ArsResponse success(
      String tId, String tTime, String menuName, String corpName, String dateStr) {
    return ArsResponse.builder()
        .tId(tId)
        .tTime(tTime)
        .menuName(menuName)
        .result("0")
        .actionType("3")
        .nextMenu("")
        .mentFlag("0")
        .mentCnt("3")
        .ments(List.of("T_" + corpName, "D_" + dateStr, "F_succ"))
        .build();
  }

  /** 실패 응답 생성 (휴대폰 번호 아님) */
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

  /** 실패 응답 생성 (잘못된 상점코드) */
  public static ArsResponse failInvalidStoreCode(String tId, String tTime, String menuName) {
    return ArsResponse.builder()
        .tId(tId)
        .tTime(tTime)
        .menuName(menuName)
        .result("0")
        .actionType("2") // 재입력 요청
        .nextMenu("")
        .mentFlag("0")
        .mentCnt("1")
        .ments(List.of("F_nonumber"))
        .build();
  }

  /** ARS 시스템이 파싱할 수 있는 HTML 형식으로 변환 */
  public String toHtml() {
    StringBuilder sb = new StringBuilder();
    sb.append("<html>\n<body>\n<form name='frm' method='post'>\n");
    sb.append(hiddenInput("T_ID", tId));
    sb.append(hiddenInput("T_TIME", tTime));
    sb.append(hiddenInput("RESULT", result));
    sb.append(hiddenInput("MENU_NAME", menuName));
    sb.append(hiddenInput("ACTION_TYPE", actionType));
    sb.append(hiddenInput("NEXT_MENU", nextMenu));
    sb.append(hiddenInput("MENT_FLAG", mentFlag));
    sb.append(hiddenInput("MENT_CNT", mentCnt));

    if (ments != null) {
      for (int i = 0; i < ments.size(); i++) {
        sb.append(hiddenInput("MENT_" + (i + 1), escapeHtml(ments.get(i))));
      }
    }

    sb.append("</form>\n</body>\n</html>");
    return sb.toString();
  }

  private String hiddenInput(String name, String value) {
    return String.format(
        "    <input type='hidden' name='%s' value='%s'/>\n", name, value != null ? value : "");
  }

  private String escapeHtml(String str) {
    if (str == null) return "";
    return str.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
