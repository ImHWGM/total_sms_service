package kr.wisead.domain.company.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 고객사 Entity customer_company 테이블 매핑 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerCompany {

  private Integer seq; // 시퀀스
  private String userId; // 사용자 ID (소속 사용자)
  private String selectedUserId; // 선택된 사용자 ID
  private String custCompName; // 고객사명
  private LocalDateTime uptDate; // 수정일시
  private String uptId; // 수정자 ID
  private String chkedYn; // 체크 여부 (Y/N)
}
