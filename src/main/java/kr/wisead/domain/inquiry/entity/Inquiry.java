package kr.wisead.domain.inquiry.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 문의 Entity
 * COM_INTRO_EMAIL 테이블 매핑
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inquiry {

    private Long inquiryId;             // 문의 ID
    private String companyName;         // 회사명
    private String applicantName;       // 신청자명
    private String email;               // 이메일
    private String contact;             // 연락처
    private Integer inquiryType;        // 문의 유형 (1: 서비스문의, 2: 기술문의, 3: 결제문의, 4: 기타)
    private String content;             // 문의 내용
    private String status;              // 상태 (PENDING, ANSWERED, CLOSED)
    private String answer;              // 답변 내용
    private LocalDateTime answeredAt;   // 답변 일시
    private String answeredBy;          // 답변자 ID
    private LocalDateTime createdAt;    // 등록일시
    private LocalDateTime updatedAt;    // 수정일시
}
