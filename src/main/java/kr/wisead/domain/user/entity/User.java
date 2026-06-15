package kr.wisead.domain.user.entity;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 기업회원 Entity */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

  private Integer seq;
  private String userId;
  private String userPass;
  private String corpName;
  private String corpAddr;
  private String bizNum;
  private String bizTel;
  private String person;
  private String phone;
  private String email;
  private Integer userLevel;
  private String useYn;
  private String allowIpYn;
  private String allowIp;
  private LocalDateTime lastLogin;
  private Integer loginFailureCnt;
  private LocalDateTime regDate;
  private String regId;
  private LocalDateTime uptDate;
  private String uptId;

  /** 기존 한글 상태 컬럼 (STATUS). 90일 레거시 fallback 용; PR4에서 제거 예정. */
  private String status;

  private String emailCode;
  private LocalDateTime codeValidate;
  private String callback;
  private String bizPdfLoc;
  private String storeCode;
  private Integer blockedSeq;
  private String loginPhone;
  private String defaultTwoFactorMethod;

  // ── PR1: 신규 컬럼 (PR0 additive schema 에서 추가됨) ──────────────────────
  /** 로그인 잠금 해제 예정 시각 (NULL = 미잠금). */
  private LocalDateTime lockedUntil;

  /**
   * 계정 생명주기 상태 (LIFECYCLE_STATUS). non-null 이면 이 값이 우선; null 이면 legacy {@code status} 한글 값으로
   * fallback.
   */
  private LifecycleStatus lifecycleStatus;

  /** 휴면 전환 시각 */
  private LocalDateTime dormantAt;

  /** 휴면 사전 알림 발송 시각 (1회 발송 idempotent guard) */
  private LocalDateTime dormantNotifiedAt;

  /** 탈퇴 전환 시각 */
  private LocalDateTime withdrawnAt;

  /** PIPA 익명화 처리 시각 (NULL = 미익명화; idempotent guard) */
  private LocalDateTime anonymizedAt;

  @Builder
  public User(
      Integer seq,
      String userId,
      String userPass,
      String corpName,
      String corpAddr,
      String bizNum,
      String bizTel,
      String person,
      String phone,
      String email,
      Integer userLevel,
      String useYn,
      String allowIpYn,
      String allowIp,
      LocalDateTime lastLogin,
      Integer loginFailureCnt,
      LocalDateTime regDate,
      String regId,
      LocalDateTime uptDate,
      String uptId,
      String status,
      String emailCode,
      LocalDateTime codeValidate,
      String callback,
      String bizPdfLoc,
      String storeCode,
      Integer blockedSeq,
      String loginPhone,
      String defaultTwoFactorMethod,
      LocalDateTime lockedUntil,
      LifecycleStatus lifecycleStatus,
      LocalDateTime dormantAt,
      LocalDateTime dormantNotifiedAt,
      LocalDateTime withdrawnAt,
      LocalDateTime anonymizedAt) {
    this.seq = seq;
    this.userId = userId;
    this.userPass = userPass;
    this.corpName = corpName;
    this.corpAddr = corpAddr;
    this.bizNum = bizNum;
    this.bizTel = bizTel;
    this.person = person;
    this.phone = phone;
    this.email = email;
    this.userLevel = userLevel;
    this.useYn = useYn;
    this.allowIpYn = allowIpYn;
    this.allowIp = allowIp;
    this.lastLogin = lastLogin;
    this.loginFailureCnt = loginFailureCnt;
    this.regDate = regDate;
    this.regId = regId;
    this.uptDate = uptDate;
    this.uptId = uptId;
    this.status = status;
    this.emailCode = emailCode;
    this.codeValidate = codeValidate;
    this.callback = callback;
    this.bizPdfLoc = bizPdfLoc;
    this.storeCode = storeCode;
    this.blockedSeq = blockedSeq;
    this.loginPhone = loginPhone;
    this.defaultTwoFactorMethod = defaultTwoFactorMethod != null ? defaultTwoFactorMethod : "EMAIL";
    this.lockedUntil = lockedUntil;
    this.lifecycleStatus = lifecycleStatus;
    this.dormantAt = dormantAt;
    this.dormantNotifiedAt = dormantNotifiedAt;
    this.withdrawnAt = withdrawnAt;
    this.anonymizedAt = anonymizedAt;
  }

  /** 로그인 성공 처리 */
  public void loginSuccess() {
    this.lastLogin = LocalDateTime.now();
    this.loginFailureCnt = 0;
  }

  /** 로그인 실패 처리 */
  public void loginFailed() {
    this.loginFailureCnt = (this.loginFailureCnt == null ? 0 : this.loginFailureCnt) + 1;
  }

  /** 비밀번호 변경 */
  public void changePassword(String encodedPassword) {
    this.userPass = encodedPassword;
    this.uptDate = LocalDateTime.now();
  }

  /** 이메일 인증 코드 설정 */
  public void setEmailVerification(String code, int validMinutes) {
    this.emailCode = code;
    this.codeValidate = LocalDateTime.now().plusMinutes(validMinutes);
  }

  /**
   * 계정 활성화 여부.
   *
   * <p>lifecycleStatus 가 non-null 이면 이 값을 우선 사용한다. PR1 data migration 이후 lifecycleStatus 는 항상
   * non-null 이므로 legacy fallback 은 사실상 dead path. 90일 cleanup PR4에서 제거 예정.
   */
  public boolean isActive() {
    if (lifecycleStatus != null) {
      return lifecycleStatus == LifecycleStatus.ACTIVE;
    }
    // legacy fallback: 한글 STATUS 컬럼 (PR4 cleanup 전까지 보존)
    return "Y".equals(this.useYn) && "승인".equals(this.status);
  }

  /**
   * 계정 잠금 여부.
   *
   * <p>lockedUntil 기반으로만 판정한다. lockedUntil 이 현재 시각보다 이후면 잠금 상태. 5회 카운트 기반 fallback 은 제거됨 (영구 잠금 버그
   * 방지 — plan AC27/isLocked 재작성).
   */
  public boolean isLocked() {
    return this.lockedUntil != null && this.lockedUntil.isAfter(LocalDateTime.now());
  }

  /** 관리자 여부 (userLevel >= 90). */
  public boolean isAdmin() {
    return this.userLevel != null && this.userLevel >= 90;
  }

  /** 최고 관리자 여부 (userLevel == 99 strict equality — ChargeBonusEventService 동일 패턴). */
  public boolean isSuperAdmin() {
    return this.userLevel != null && this.userLevel == 99;
  }

  /** 익명화 처리 여부 (PIPA Art.21 idempotent guard). */
  public boolean isAnonymized() {
    return this.anonymizedAt != null;
  }

  /** 휴면 계정 여부. */
  public boolean isDormant() {
    return this.lifecycleStatus == LifecycleStatus.DORMANT;
  }

  /** 탈퇴 계정 여부. */
  public boolean isWithdrawn() {
    return this.lifecycleStatus == LifecycleStatus.WITHDRAWN;
  }
}
