package kr.wisead.domain.admin.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;

import java.util.List;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.mapper.primary.CustomerCompanyMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 조회(canRead)/수정(canModify) 권한 분리 검증.
 *
 * <p>B 레벨(최고관리자B=90, 운영관리자B=50)은 A 레벨과 조회 범위는 같으나 수정 권한이 없다. read 경로가 수정 권한을 요구해 B 조회가 차단되던 회귀를
 * 방지한다.
 *
 * <p>currentUserId 를 비숫자 문자열로 주어 getUserIdBySeq 가 그대로 반환하도록 한다(userMapper 조회 회피).
 */
@ExtendWith(MockitoExtension.class)
class AdminServicePermissionTest {

  private static final String OWNER = "owner";
  private static final String MANAGED = "managed-account";
  private static final String OTHER = "other-account";

  private static final int SUPER_A = 99; // 최고관리자(A)
  private static final int SUPER_B = 90; // 최고관리자(B)
  private static final int OPER_A = 60; // 운영관리자(A)
  private static final int OPER_B = 50; // 운영관리자(B)
  private static final int CORP = 10; // 기업관리자

  @Mock private CustomerCompanyMapper customerCompanyMapper;

  @InjectMocks private AdminService adminService;

  @Nested
  @DisplayName("canRead - 조회 권한")
  class CanRead {

    @Test
    @DisplayName("본인 데이터는 모든 레벨이 조회 가능")
    void self_alwaysReadable() {
      assertTrue(adminService.canRead(OWNER, CORP, OWNER));
    }

    @Test
    @DisplayName("최고관리자 A/B 는 타인 데이터 조회 가능")
    void superAdmin_readsAnyone() {
      assertTrue(adminService.canRead("superA", SUPER_A, OTHER));
      assertTrue(adminService.canRead("superB", SUPER_B, OTHER));
    }

    @Test
    @DisplayName("운영관리자 A/B 는 관리 계정 데이터 조회 가능")
    void operationAdmin_readsManagedAccount() {
      lenient().when(customerCompanyMapper.selectManagedUserIds("operA")).thenReturn(List.of(MANAGED));
      lenient().when(customerCompanyMapper.selectManagedUserIds("operB")).thenReturn(List.of(MANAGED));

      assertTrue(adminService.canRead("operA", OPER_A, MANAGED));
      assertTrue(adminService.canRead("operB", OPER_B, MANAGED));
    }

    @Test
    @DisplayName("운영관리자 B 는 관리 범위 밖 데이터는 조회 불가")
    void operationAdminB_cannotReadUnmanaged() {
      lenient().when(customerCompanyMapper.selectManagedUserIds("operB")).thenReturn(List.of(MANAGED));

      assertFalse(adminService.canRead("operB", OPER_B, OTHER));
    }

    @Test
    @DisplayName("기업관리자는 타인 데이터 조회 불가")
    void corporateAdmin_cannotReadOthers() {
      assertFalse(adminService.canRead("corp", CORP, OTHER));
    }

    @Test
    @DisplayName("validateReadPermission: 권한 없으면 ACCESS_DENIED 예외")
    void validateReadPermission_throwsWhenDenied() {
      assertThrows(
          BusinessException.class, () -> adminService.validateReadPermission("corp", CORP, OTHER));
    }
  }

  @Nested
  @DisplayName("canModify - 수정 권한 (B는 불가, A는 가능)")
  class CanModify {

    @Test
    @DisplayName("본인 데이터는 모든 레벨이 수정 가능")
    void self_alwaysModifiable() {
      assertTrue(adminService.canModify(OWNER, CORP, OWNER));
    }

    @Test
    @DisplayName("최고관리자 A 는 타인 데이터 수정 가능, B 는 불가")
    void superAdmin_aCanModify_bCannot() {
      assertTrue(adminService.canModify("superA", SUPER_A, OTHER));
      assertFalse(adminService.canModify("superB", SUPER_B, OTHER));
    }

    @Test
    @DisplayName("운영관리자 A 는 관리 계정 수정 가능, B 는 불가")
    void operationAdmin_aCanModify_bCannot() {
      lenient().when(customerCompanyMapper.selectManagedUserIds("operA")).thenReturn(List.of(MANAGED));

      assertTrue(adminService.canModify("operA", OPER_A, MANAGED));
      assertFalse(adminService.canModify("operB", OPER_B, MANAGED));
    }

    @Test
    @DisplayName("validateModifyPermission: B 레벨은 타인 데이터에 ACCESS_DENIED 예외")
    void validateModifyPermission_throwsForLevelB() {
      assertThrows(
          BusinessException.class,
          () -> adminService.validateModifyPermission("superB", SUPER_B, OTHER));
      assertDoesNotThrow(() -> adminService.validateModifyPermission("superA", SUPER_A, OTHER));
    }
  }
}
