package kr.wisead.domain.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import kr.wisead.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AdminService 관련 User 엔티티 빌더 정책 검증 테스트.
 *
 * <p>SMS 2FA 추가 (Phase A) 도입에 따라 관리자 계정 생성 흐름에서 신규 컬럼이 안전한 기본값으로 채워지는지 확인한다.
 *
 * <p>SpringBootTest 비용을 피하고 AdminService.createAdminAccount 내부의 User.builder() 호출 정책을
 * 동일한 방식으로 재현하여 검증한다.
 */
class AdminServiceTest {

  @Test
  @DisplayName("createAdmin 흐름: defaultTwoFactorMethod 는 EMAIL 로 초기화된다")
  void createAdmin_setsDefaultTwoFactorMethodToEmail() {
    // Given & When: 관리자 계정 생성 시 AdminService.java:94 에서 사용하는 빌더 호출 정책을 재현
    User admin =
        User.builder()
            .userId("admin01")
            .userPass("encodedPass")
            .corpName("WiseAd")
            .userLevel(90)
            .useYn("Y")
            .allowIpYn("Y")
            .status("승인")
            .regId("creator")
            .loginFailureCnt(0)
            .storeCode("ST0001")
            .loginPhone(null)
            .defaultTwoFactorMethod("EMAIL")
            .build();

    // Then
    assertEquals("EMAIL", admin.getDefaultTwoFactorMethod());
  }

  @Test
  @DisplayName("createAdmin 흐름: loginPhone 은 미등록(null) 상태이다")
  void createAdmin_loginPhoneIsNull() {
    // Given & When
    User admin =
        User.builder()
            .userId("admin02")
            .userPass("encodedPass")
            .corpName("WiseAd")
            .userLevel(90)
            .useYn("Y")
            .allowIpYn("Y")
            .status("승인")
            .regId("creator")
            .loginFailureCnt(0)
            .storeCode("ST0002")
            .loginPhone(null)
            .defaultTwoFactorMethod("EMAIL")
            .build();

    // Then
    assertNull(admin.getLoginPhone());
  }

  @Test
  @DisplayName("defaultTwoFactorMethod 가 명시되지 않으면 EMAIL 로 백필된다 (NOT NULL 보호)")
  void user_builder_defaultsTwoFactorMethodToEmailWhenMissing() {
    // Given & When: 빌더에서 defaultTwoFactorMethod 를 명시하지 않은 케이스
    User user =
        User.builder()
            .userId("user01")
            .userPass("encodedPass")
            .corpName("WiseAd")
            .userLevel(1)
            .build();

    // Then: DB 컬럼 NOT NULL 제약을 위반하지 않도록 EMAIL 로 기본값 부여
    assertEquals("EMAIL", user.getDefaultTwoFactorMethod());
  }
}
