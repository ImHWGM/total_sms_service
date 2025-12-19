package kr.wisead.domain.event.dto;

import kr.wisead.domain.event.entity.EventParticipant;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 행사 참가자 응답 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventParticipantResponse {

    private Long seq;
    private Integer surveyUserSeq;
    private Integer eventSeq;
    private String checkCode;
    private String department;
    private String position;
    private String participantType;
    private String memo;
    private String nametagPrinted;
    private LocalDateTime regDate;
    private LocalDateTime modDate;

    // SURVEY_USER 정보
    private String userName;
    private String userPhone;
    private String userEmail;

    // SURVEY_MASTER 정보
    private String eventName;

    // QR 코드 URL
    private String qrCodeUrl;

    /**
     * Entity -> Response 변환
     */
    public static EventParticipantResponse from(EventParticipant entity) {
        return EventParticipantResponse.builder()
                .seq(entity.getSeq())
                .surveyUserSeq(entity.getSurveyUserSeq())
                .eventSeq(entity.getEventSeq())
                .checkCode(entity.getCheckCode())
                .department(entity.getDepartment())
                .position(entity.getPosition())
                .participantType(entity.getParticipantType())
                .memo(entity.getMemo())
                .nametagPrinted(entity.getNametagPrinted())
                .regDate(entity.getRegDate())
                .modDate(entity.getModDate())
                .userName(entity.getUserName())
                .userPhone(entity.getUserPhone())
                .userEmail(entity.getUserEmail())
                .eventName(entity.getEventName())
                .build();
    }

    /**
     * QR 코드 URL 설정
     */
    public EventParticipantResponse withQrCodeUrl(String baseUrl) {
        this.qrCodeUrl = baseUrl + "/event/check/" + this.checkCode;
        return this;
    }
}
