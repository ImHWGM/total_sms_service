package kr.wisead.domain.inquiry.dto;

import kr.wisead.domain.inquiry.entity.Inquiry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 문의 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InquiryResponse {

    private Long inquiryId;
    private String companyName;
    private String applicantName;
    private String email;
    private String contact;
    private Integer inquiryType;
    private String inquiryTypeName;
    private String content;
    private String status;
    private String statusName;
    private String answer;
    private LocalDateTime answeredAt;
    private String answeredBy;
    private LocalDateTime createdAt;

    public static InquiryResponse from(Inquiry entity) {
        String typeName = switch (entity.getInquiryType()) {
            case 1 -> "서비스 문의";
            case 2 -> "기술 문의";
            case 3 -> "결제 문의";
            case 4 -> "기타";
            default -> "기타";
        };

        String statusNm = switch (entity.getStatus() != null ? entity.getStatus() : "PENDING") {
            case "PENDING" -> "대기중";
            case "ANSWERED" -> "답변완료";
            case "CLOSED" -> "종료";
            default -> entity.getStatus();
        };

        return InquiryResponse.builder()
                .inquiryId(entity.getInquiryId())
                .companyName(entity.getCompanyName())
                .applicantName(entity.getApplicantName())
                .email(entity.getEmail())
                .contact(entity.getContact())
                .inquiryType(entity.getInquiryType())
                .inquiryTypeName(typeName)
                .content(entity.getContent())
                .status(entity.getStatus())
                .statusName(statusNm)
                .answer(entity.getAnswer())
                .answeredAt(entity.getAnsweredAt())
                .answeredBy(entity.getAnsweredBy())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
