package kr.wisead.domain.user.entity;

/**
 * 계정 생명주기 상태 (LIFECYCLE_STATUS 컬럼).
 *
 * <p>기존 STATUS 한글 컬럼과 병행 운영 (90일 후 cleanup PR4에서 한글 컬럼 제거 예정). 매핑: '승인' → ACTIVE, '미승인' →
 * PENDING_APPROVAL, '탈퇴' → WITHDRAWN.
 */
public enum LifecycleStatus {

  /** 정상 활성 계정 */
  ACTIVE,

  /** 가입 후 관리자 승인 대기 */
  PENDING_APPROVAL,

  /** 6개월 미사용으로 휴면 전환된 계정 */
  DORMANT,

  /** 탈퇴 처리된 계정 (PIPA 익명화 완료) */
  WITHDRAWN
}
