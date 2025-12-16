package kr.wisead.domain.survey.dto;

import lombok.*;

import java.time.LocalDate;

/**
 * 설문 참여자 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyUserRequest {

    private Integer eventSeq;           // 이벤트 시퀀스
    private String userPhone;           // 휴대전화 (평문)
    private String userName;            // 사용자 명 (평문)
    private String userEmail;           // 이메일
    private String address;             // 기본주소
    private String address2;            // 상세주소
    private LocalDate depositDate;      // 입금일자
    private LocalDate shipmentDate;     // 배송일자
}
