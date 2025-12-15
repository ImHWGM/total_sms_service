package kr.wisead.domain.company.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 고객사 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerCompanyRequest {

    private String userId;                  // 사용자 ID
    private String selectedUserId;          // 선택된 사용자 ID
    private String custCompName;            // 고객사명
    private String custCompBizCode;         // 사업자등록번호
    private List<String> custCompNames;     // 고객사명 목록 (일괄 등록용)
    private String chkedYn;                 // 체크 여부
}
