package kr.wisead.domain.company.dto;

import kr.wisead.domain.company.entity.CustomerCompany;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 고객사 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerCompanyResponse {

    private Integer seq;
    private String userId;
    private String selectedUserId;
    private String custCompName;
    private String person;                  // 담당자명
    private String corpName;                // 회사명 (TB_MANAGER)
    private String bizNum;                  // 사업자번호
    private LocalDateTime uptDate;
    private String chkedYn;

    public static CustomerCompanyResponse from(CustomerCompany entity) {
        return CustomerCompanyResponse.builder()
                .seq(entity.getSeq())
                .userId(entity.getUserId())
                .selectedUserId(entity.getSelectedUserId())
                .custCompName(entity.getCustCompName())
                .uptDate(entity.getUptDate())
                .chkedYn(entity.getChkedYn())
                .build();
    }
}
