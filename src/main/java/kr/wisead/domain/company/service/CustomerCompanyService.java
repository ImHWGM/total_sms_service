package kr.wisead.domain.company.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.util.CryptoUtils;
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

        // 암호화된 필드 복호화 (corpName은 평문이므로 제외)
        list.forEach(item -> {
            item.setPerson(decryptField(item.getPerson()));
            item.setPhone(decryptField(item.getPhone()));
        });

        int total = customerCompanyMapper.selectCompanyListCount(request);

        return PageResponse.of(list, request.getPageNum(), request.getAmount(), total);
    }

    /**
     * 암호화된 필드 복호화 (AES256 + Base64)
     */
    private String decryptField(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            return encryptedValue;
        }
        try {
            return CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(encryptedValue));
        } catch (Exception e) {
            log.debug("필드 복호화 실패, 원본 반환: {}", e.getMessage());
            return encryptedValue;
        }
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

        // selectedUserIds 필수값 체크
        if (request.getSelectedUserIds() == null || request.getSelectedUserIds().isEmpty()) {
            log.error("고객사 일괄 등록 실패: selectedUserIds가 없습니다.");
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "선택된 사용자 ID 목록이 필요합니다.");
        }

        if (request.getCustCompNames() == null || request.getCustCompNames().isEmpty()) {
            log.warn("고객사 일괄 등록: 등록할 고객사명이 없습니다. userId={}", request.getUserId());
            return false;
        }

        // selectedUserIds와 custCompNames 개수가 일치해야 함
        if (request.getSelectedUserIds().size() != request.getCustCompNames().size()) {
            log.error("고객사 일괄 등록 실패: selectedUserIds와 custCompNames 개수가 일치하지 않습니다.");
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                    "선택된 사용자 ID 목록과 고객사명 목록의 개수가 일치해야 합니다.");
        }

        // 유효한 데이터만 필터링 (빈 문자열 및 null 값 제외)
        List<String> validNames = new java.util.ArrayList<>();
        List<String> validUserIds = new java.util.ArrayList<>();

        for (int i = 0; i < request.getCustCompNames().size(); i++) {
            String name = request.getCustCompNames().get(i);
            String selectedUserId = request.getSelectedUserIds().get(i);

            if (name != null && !name.isBlank() && selectedUserId != null && !selectedUserId.isBlank()) {
                validNames.add(name.trim());
                validUserIds.add(selectedUserId.trim());
            }
        }

        if (validNames.isEmpty()) {
            log.warn("고객사 일괄 등록: 유효한 고객사명이 없습니다. userId={}", request.getUserId());
            return false;
        }

        // CustomerCompany 객체 리스트 생성
        List<CustomerCompany> companies = new java.util.ArrayList<>();
        for (int i = 0; i < validNames.size(); i++) {
            companies.add(CustomerCompany.builder()
                    .userId(request.getUserId())
                    .selectedUserId(validUserIds.get(i))
                    .custCompName(validNames.get(i))
                    .uptId(operatorId)
                    .build());
        }

        int result = customerCompanyMapper.insertCustomerCompanyBatch(companies);

        log.info("고객사 일괄 등록 완료: userId={}, count={}", request.getUserId(), result);
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
