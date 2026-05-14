package kr.wisead.domain.company.controller;

import java.util.List;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.company.dto.CustomerCompanyRequest;
import kr.wisead.domain.company.dto.CustomerCompanyResponse;
import kr.wisead.domain.company.dto.CustomerCompanySearchRequest;
import kr.wisead.domain.company.entity.CustomerCompany;
import kr.wisead.domain.company.service.CustomerCompanyService;
import kr.wisead.security.jwt.CurrentUser;
import kr.wisead.security.jwt.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** 고객사 관리 Controller */
@Slf4j
@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CustomerCompanyController {

  private final CustomerCompanyService customerCompanyService;

  /** 고객사 목록 조회 (페이징) POST /api/company/list */
  @PostMapping("/list")
  public ApiResponse<PageResponse<CustomerCompanyResponse>> getCompanyList(
      @RequestBody CustomerCompanySearchRequest request) {
    PageResponse<CustomerCompanyResponse> response = customerCompanyService.getCompanyList(request);
    return ApiResponse.success(response);
  }

  /** 사용자의 선택된 고객사 목록 조회 GET /api/company/selected/{userId} */
  @GetMapping("/selected/{userId}")
  public ApiResponse<List<CustomerCompany>> getSelectedCompanies(@PathVariable String userId) {
    List<CustomerCompany> companies = customerCompanyService.getSelectedCompanies(userId);
    return ApiResponse.success(companies);
  }

  /** 고객사 선택/해제 POST /api/company/toggle */
  @PostMapping("/toggle")
  public ApiResponse<Integer> toggleCompanySelection(
      @CurrentUser JwtPrincipal user, @RequestBody CustomerCompanyRequest request) {
    int result = customerCompanyService.toggleCompanySelection(request, user.userId());
    return ApiResponse.success(result);
  }

  /** 고객사 일괄 등록 POST /api/company/enroll */
  @PostMapping("/enroll")
  public ApiResponse<Boolean> enrollCompanies(
      @CurrentUser JwtPrincipal user, @RequestBody CustomerCompanyRequest request) {
    boolean result = customerCompanyService.enrollCompanies(request, user.userId());
    return ApiResponse.success(result);
  }

  /** 고객사명 변경 PUT /api/company/name */
  @PutMapping("/name")
  public ApiResponse<Integer> updateCompanyName(
      @RequestParam String newName, @RequestParam String oldName) {
    int result = customerCompanyService.updateCompanyName(newName, oldName);
    return ApiResponse.success(result);
  }

  /** 담당자명 조회 GET /api/company/person/{userId} */
  @GetMapping("/person/{userId}")
  public ApiResponse<String> getPersonByUserId(@PathVariable String userId) {
    String person = customerCompanyService.getPersonByUserId(userId);
    return ApiResponse.<String>success(person, "담당자 조회 성공");
  }

  /** 회사명 조회 GET /api/company/corp/{userId} */
  @GetMapping("/corp/{userId}")
  public ApiResponse<String> getCorpNameByUserId(@PathVariable String userId) {
    String corpName = customerCompanyService.getCorpNameByUserId(userId);
    return ApiResponse.<String>success(corpName, "회사명 조회 성공");
  }

  /** 고객사 정보 수정 PUT /api/company/{seq} */
  @PutMapping("/{seq}")
  public ApiResponse<Boolean> updateCompany(
      @CurrentUser JwtPrincipal user,
      @PathVariable Integer seq,
      @RequestBody CustomerCompanyRequest request) {
    request.setSeq(seq);
    boolean result = customerCompanyService.updateCompany(request, user.userId());
    return ApiResponse.success(result);
  }

  /** 고객사 상세 조회 GET /api/company/{seq} */
  @GetMapping("/{seq}")
  public ApiResponse<CustomerCompany> getCompanyBySeq(@PathVariable Integer seq) {
    CustomerCompany company = customerCompanyService.getCompanyBySeq(seq);
    return ApiResponse.success(company);
  }
}
