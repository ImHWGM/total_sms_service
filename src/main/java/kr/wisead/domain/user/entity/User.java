package kr.wisead.domain.user.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 기업회원 Entity
 */
@Getter
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
    private String status;
    private String emailCode;
    private LocalDateTime codeValidate;
    private String callback;
    private String bizPdfLoc;
    private String storeCode;
    private Integer blockedSeq;

    @Builder
    public User(Integer seq, String userId, String userPass, String corpName, String corpAddr,
                String bizNum, String bizTel, String person, String phone, String email,
                Integer userLevel, String useYn, String allowIpYn, String allowIp,
                LocalDateTime lastLogin, Integer loginFailureCnt, LocalDateTime regDate,
                String regId, LocalDateTime uptDate, String uptId, String status,
                String emailCode, LocalDateTime codeValidate,
                String callback, String bizPdfLoc, String storeCode, Integer blockedSeq) {
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
    }

    /**
     * 로그인 성공 처리
     */
    public void loginSuccess() {
        this.lastLogin = LocalDateTime.now();
        this.loginFailureCnt = 0;
    }

    /**
     * 로그인 실패 처리
     */
    public void loginFailed() {
        this.loginFailureCnt = (this.loginFailureCnt == null ? 0 : this.loginFailureCnt) + 1;
    }

    /**
     * 비밀번호 변경
     */
    public void changePassword(String encodedPassword) {
        this.userPass = encodedPassword;
        this.uptDate = LocalDateTime.now();
    }

    /**
     * 이메일 인증 코드 설정
     */
    public void setEmailVerification(String code, int validMinutes) {
        this.emailCode = code;
        this.codeValidate = LocalDateTime.now().plusMinutes(validMinutes);
    }

    /**
     * 계정 활성화 여부
     */
    public boolean isActive() {
        return "Y".equals(this.useYn) && "승인".equals(this.status);
    }

    /**
     * 계정 잠금 여부 (로그인 실패 5회 이상)
     */
    public boolean isLocked() {
        return this.loginFailureCnt != null && this.loginFailureCnt >= 5;
    }

    /**
     * 관리자 여부
     */
    public boolean isAdmin() {
        return this.userLevel != null && this.userLevel >= 90;
    }
}
