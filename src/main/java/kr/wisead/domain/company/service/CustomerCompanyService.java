package kr.wisead.domain.company.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
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
import java.util.stream.Collectors;

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
                request.getCustCompName());

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
        // userId 필수값 체크
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            log.error("고객사 일괄 등록 실패: userId가 없습니다.");
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "사용자 ID가 필요합니다.");
        }

        // selectedUserId 필수값 체크
        if (request.getSelectedUserId() == null || request.getSelectedUserId().isBlank()) {
            log.error("고객사 일괄 등록 실패: selectedUserId가 없습니다.");
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "선택된 사용자 ID가 필요합니다.");
        }

        if (request.getCustCompNames() == null || request.getCustCompNames().isEmpty()) {
            log.warn("고객사 일괄 등록: 등록할 고객사명이 없습니다. userId={}", request.getUserId());
            return false;
        }

        // 빈 문자열 및 null 값 필터링, 중복 제거
        List<String> validNames = request.getCustCompNames().stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());

        if (validNames.isEmpty()) {
            log.warn("고객사 일괄 등록: 유효한 고객사명이 없습니다. userId={}", request.getUserId());
            return false;
        }

        int result = customerCompanyMapper.insertCustomerCompanyBatch(
                request.getUserId(),
                request.getSelectedUserId(),
                operatorId,
                validNames);

        log.info("고객사 일괄 등록 완료: userId={}, selectedUserId={}, count={}",
                request.getUserId(), request.getSelectedUserId(), result);
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
