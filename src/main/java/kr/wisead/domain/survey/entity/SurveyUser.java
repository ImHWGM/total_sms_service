package kr.wisead.domain.survey.entity;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 설문 참여자 Entity (TB_SURVEY_USER)
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SurveyUser {

    private Integer seq;                    // 시퀀스 (userSeq)
    private Integer eventSeq;               // 이벤트 시퀀스
    private String userKey;                 // 사용자 키 (난수)
    private String userName;                // 사용자 명
    private String juminNum;                // 주민번호 (암호화)
    private String userPhone;               // 휴대전화/연락처 (암호화)
    private String resendUserPhone;         // 재발송 전화번호 (암호화)
    private String userEmail;               // 사용자 이메일
    private String address;                 // 기본주소
    private String address2;                // 상세주소
    private String delYn;                   // 삭제여부
    private LocalDate depositDate;          // 입금일자
    private LocalDate shipmentDate;         // 배송일자
    private LocalDateTime submissionDate;   // 설문완료일
    private LocalDateTime lastConDate;      // 최종접속일
    private LocalDateTime surveyStartTime;  // 설문접속일
    private LocalDateTime surveyAuthTime;   // 설문인증일
    private String regId;                   // 등록 ID
    private LocalDateTime regDate;          // 등록일
    private String uptId;                   // 수정 ID
    private LocalDateTime uptDate;          // 수정일

    // 조회용 필드
    private String eventCode;               // 이벤트 코드
    private String eventType;               // 이벤트 타입
    private String eventName;               // 이벤트 명
    private String auth;                    // 인증종류
    private String generalAuthCode;         // 범용인증코드

    /**
     * 설문 참여자 생성 (발송용)
     */
    public static SurveyUser createForSend(Integer eventSeq, String userKey,
                                            String userPhone, String regId) {
        return SurveyUser.builder()
                .eventSeq(eventSeq)
                .userKey(userKey)
                .userPhone(userPhone)
                .delYn("N")
                .regId(regId)
                .build();
    }

    /**
     * 설문 참여자 생성 (범용인증용)
     */
    public static SurveyUser createForAuth(Integer eventSeq, String userKey, String regId) {
        return SurveyUser.builder()
                .eventSeq(eventSeq)
                .userKey(userKey)
                .delYn("N")
                .regId(regId)
                .build();
    }

    /**
     * 설문 제출 처리
     */
    public void submit(String userName, String juminNum, String userPhone,
                       String userEmail, String address, String address2, String uptId) {
        this.userName = userName;
        this.juminNum = juminNum;
        this.userPhone = userPhone;
        this.userEmail = userEmail;
        this.address = address;
        this.address2 = address2;
        this.uptId = uptId;
        this.submissionDate = LocalDateTime.now();
    }

    /**
     * 설문 접속 시간 기록
     */
    public void recordStartTime() {
        this.surveyStartTime = LocalDateTime.now();
    }

    /**
     * 설문 인증 시간 기록
     */
    public void recordAuthTime() {
        this.surveyAuthTime = LocalDateTime.now();
    }

    /**
     * 설문 완료 여부 확인
     */
    public boolean isSubmitted() {
        return this.submissionDate != null;
    }

    /**
     * 삭제 여부 확인
     */
    public boolean isDeleted() {
        return "Y".equals(this.delYn);
    }

    /**
     * 재발송 전화번호 설정
     */
    public void setResendUserPhone(String resendUserPhone) {
        this.resendUserPhone = resendUserPhone;
    }
}
