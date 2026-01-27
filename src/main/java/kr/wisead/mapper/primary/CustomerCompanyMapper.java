package kr.wisead.mapper.primary;

import java.util.List;
import kr.wisead.domain.company.dto.CustomerCompanyResponse;
import kr.wisead.domain.company.dto.CustomerCompanySearchRequest;
import kr.wisead.domain.company.entity.CustomerCompany;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 고객사 Mapper */
@Mapper
public interface CustomerCompanyMapper {

  /** 고객사 선택 등록 */
  int insertCustomerCompany(CustomerCompany company);

  /** 고객사 일괄 등록 */
  int insertCustomerCompanyBatch(@Param("list") List<CustomerCompany> companies);

  /** 고객사 목록 조회 (USER 기반) */
  List<CustomerCompanyResponse> selectCompanyList(CustomerCompanySearchRequest request);

  /** 고객사 목록 총 개수 */
  int selectCompanyListCount(CustomerCompanySearchRequest request);

  /** 사용자의 선택된 고객사 목록 조회 */
  List<CustomerCompany> selectSelectedCompanies(@Param("userId") String userId);

  /** 고객사 선택 여부 확인 */
  String selectCompanyExists(
      @Param("userId") String userId,
      @Param("selectedUserId") String selectedUserId,
      @Param("custCompName") String custCompName);

  /** 고객사 선택 상태 수정 */
  int updateCompanyChecked(CustomerCompany company);

  /** 고객사명 일괄 수정 */
  int updateCompanyName(@Param("newName") String newName, @Param("oldName") String oldName);

  /** 담당자명 조회 */
  String selectPersonByUserId(@Param("userId") String userId);

  /** 회사명 조회 (USER) */
  String selectCorpNameByUserId(@Param("userId") String userId);

  /** 고객사 정보 수정 */
  int updateCustomerCompany(CustomerCompany company);

  /** 고객사 시퀀스로 조회 */
  CustomerCompany selectBySeq(@Param("seq") Integer seq);

  /**
   * 관리 계정 사용자 ID 목록 조회 - 운영 관리자(level 50-89)가 관리하는 계정 목록 - customer_company에 등록되고 checked_yn='Y'인
   * 계정들
   */
  List<String> selectManagedUserIds(@Param("userId") String userId);
}
