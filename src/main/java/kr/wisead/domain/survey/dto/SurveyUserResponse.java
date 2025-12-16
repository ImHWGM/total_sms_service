package kr.wisead.domain.survey.dto;

import kr.wisead.domain.survey.entity.SurveyUser;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 설문 참여자 응답 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SurveyUserResponse {

    private Integer userSeq;                // 사용자 시퀀스
    private Integer eventSeq;               // 이벤트 시퀀스
    private String userKey;                 // 사용자 키
    private String userName;                // 사용자 명
    private String userPhone;               // 휴대전화 (마스킹)
    private String resendUserPhone;         // 재발송 전화번호 (마스킹)
    private String userEmail;               // 이메일
    private String address;                 // 주소
    private LocalDate depositDate;          // 입금일자
    private LocalDate shipmentDate;         // 배송일자
    private LocalDateTime submissionDate;   // 설문완료일
    private LocalDateTime surveyStartTime;  // 설문접속일
    private LocalDateTime regDate;          // 등록일
    private String status;                  // 상태 (참여/미참여/접속중)

    // 조회용
    private String eventName;               // 이벤트 명
    private String eventCode;               // 이벤트 코드
    private String eventType;               // 이벤트 타입 (P:개인정보, S:설문조사)
    private String generalAuthCode;         // 범용인증코드

    /**
     * Entity -> Response 변환
     */
    public static SurveyUserResponse from(SurveyUser entity) {
        String status;
        if (entity.getSubmissionDate() != null) {
            status = "참여완료";
        } else if (entity.getSurveyStartTime() != null) {
            status = "접속중";
        } else {
            status = "미참여";
        }

        return SurveyUserResponse.builder()
                .userSeq(entity.getSeq())
                .eventSeq(entity.getEventSeq())
                .userKey(entity.getUserKey())
                .userName(entity.getUserName())
                .userPhone(maskPhone(entity.getUserPhone()))
                .resendUserPhone(maskPhone(entity.getResendUserPhone()))
                .userEmail(entity.getUserEmail())
                .address(entity.getAddress())
                .depositDate(entity.getDepositDate())
                .shipmentDate(entity.getShipmentDate())
                .submissionDate(entity.getSubmissionDate())
                .surveyStartTime(entity.getSurveyStartTime())
                .regDate(entity.getRegDate())
                .status(status)
                .eventName(entity.getEventName())
                .eventCode(entity.getEventCode())
                .eventType(entity.getEventType())
                .generalAuthCode(entity.getGeneralAuthCode())
                .build();
    }

    /**
     * 전화번호 마스킹
     */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return phone;
        }
        // 뒷 4자리만 표시
        return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
    }
}
