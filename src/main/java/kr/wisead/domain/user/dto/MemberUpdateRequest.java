package kr.wisead.domain.user.dto;

import jakarta.validation.constraints.NotNull;
import kr.wisead.domain.user.entity.User;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 회원 정보 수정 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
public class MemberUpdateRequest {

    /**
     * 회원 시퀀스
     */
    @NotNull(message = "회원 시퀀스가 필요합니다.")
    private Long seq;

    /**
     * 기업명
     */
    private String corpName;

    /**
     * 기업 주소
     */
    private String corpAddr;

    /**
     * 사업자 등록번호
     */
    private String bizNum;

    /**
     * 기업 전화번호
     */
    private String bizTel;

    /**
     * 담당자명
     */
    private String person;

    /**
     * 담당자 연락처
     */
    private String phone;

    /**
     * 담당자 이메일
     */
    private String email;

    /**
     * 사용자 권한
     */
    private Integer userLevel;

    /**
     * 허용 IP 사용 여부
     */
    private String allowIpYn;

    /**
     * 허용 IP
     */
    private String allowIp;

    /**
     * 상태 (미승인/승인/보류/탈퇴)
     */
    private String status;

    /**
     * 문자 발신번호
     */
    private String callback;

    /**
     * User Entity로 변환
     */
    public User toEntity() {
        return User.builder()
                .seq(this.seq)
                .corpName(this.corpName)
                .corpAddr(this.corpAddr)
                .bizNum(this.bizNum)
                .bizTel(this.bizTel)
                .person(this.person)
                .phone(this.phone != null ? this.phone.replace("-", "") : null)
                .email(this.email)
                .userLevel(this.userLevel)
                .allowIpYn(this.allowIpYn)
                .allowIp(this.allowIp)
                .status(this.status)
                .callback(this.callback)
                .build();
    }
}
