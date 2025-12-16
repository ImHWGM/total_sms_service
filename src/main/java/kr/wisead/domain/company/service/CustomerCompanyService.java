package kr.wisead.domain.company.service;

import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.company.dto.CustomerCompanyRequest;
import kr.wisead.domain.company.dto.CustomerCompanyResponse;
import kr.wisead.domain.company.dto.CustomerCompanySearchRequest;
import kr.wisead.domain.company.entity.CustomerCompany;
import kr.wisead.mapper.primary.CustomerCompanyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 고객사 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerCompanyService {

    private final CustomerCompanyMapper customerCompanyMapper;

    /**
     * 고객사 목록 조회 (페이징)
     */
    public PageResponse<CustomerCompanyResponse> getCompanyList(CustomerCompanySearchRequest request) {
        if (request.getAmount() <= 0) {
            request.setAmount(10);
        }
        if (request.getPageNum() <= 0) {
            request.setPageNum(1);
        }

        List<CustomerCompanyResponse> list = customerCompanyMapper.selectCompanyList(request);
        int total = customerCompanyMapper.selectCompanyListCount(request);

        return PageResponse.of(list, request.getPageNum(), request.getAmount(), total);
    }

    /**
     * 사용자의 선택된 고객사 목록 조회
     */
    public List<CustomerCompany> getSelectedCompanies(String userId) {
        return customerCompanyMapper.selectSelectedCompanies(userId);
    }

    /**
     * 고객사 선택/해제
     */
    @Transactional
    public int toggleCompanySelection(CustomerCompanyRequest request, String operatorId) {
        String exists = customerCompanyMapper.selectCompanyExists(
                request.getUserId(),
                request.getSelectedUserId(),
                request.getCustCompName()
        );

        CustomerCompany company = CustomerCompany.builder()
                .userId(request.getUserId())
                .selectedUserId(request.getSelectedUserId())
                .custCompName(request.getCustCompName())
                .uptId(operatorId)
                .chkedYn(request.getChkedYn())
                .build();

        if ("Y".equals(request.getChkedYn())) {
            // 선택
            if (exists != null && !exists.isEmpty()) {
                // 이미 존재하면 상태만 업데이트
                customerCompanyMapper.updateCompanyChecked(company);
                return 1; // 업데이트
            } else {
                // 새로 등록
                customerCompanyMapper.insertCustomerCompany(company);
                return 2; // 신규 등록
            }
        } else {
            // 해제
            customerCompanyMapper.updateCompanyChecked(company);
            return 3; // 해제
        }
    }

    /**
     * 고객사 일괄 등록
     */
    @Transactional
    public boolean enrollCompanies(CustomerCompanyRequest request, String operatorId) {
        if (request.getCustCompNames() == null || request.getCustCompNames().isEmpty()) {
            return false;
        }

        int result = customerCompanyMapper.insertCustomerCompanyBatch(
                request.getUserId(),
                operatorId,
                request.getCustCompNames()
        );

        return result > 0;
    }

    /**
     * 고객사명 변경
     */
    @Transactional
    public int updateCompanyName(String newName, String oldName) {
        return customerCompanyMapper.updateCompanyName(newName, oldName);
    }

    /**
     * 담당자명 조회
     */
    public String getPersonByUserId(String userId) {
        return customerCompanyMapper.selectPersonByUserId(userId);
    }

    /**
     * 회사명 조회
     */
    public String getCorpNameByUserId(String userId) {
        return customerCompanyMapper.selectCorpNameByUserId(userId);
    }

    /**
     * 고객사 정보 수정
     */
    @Transactional
    public boolean updateCompany(CustomerCompanyRequest request, String operatorId) {
        if (request.getSeq() == null) {
            log.error("고객사 시퀀스가 없습니다.");
            return false;
        }

        CustomerCompany company = CustomerCompany.builder()
                .seq(request.getSeq())
                .custCompName(request.getCustCompName())
                .uptId(operatorId)
                .build();

        int result = customerCompanyMapper.updateCustomerCompany(company);
        return result > 0;
    }

    /**
     * 고객사 시퀀스로 조회
     */
    public CustomerCompany getCompanyBySeq(Integer seq) {
        return customerCompanyMapper.selectBySeq(seq);
    }
}
