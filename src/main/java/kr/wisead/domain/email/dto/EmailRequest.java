package kr.wisead.domain.email.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 이메일 발송 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailRequest {

    private String to;              // 수신자
    private String cc;              // 참조
    private String bcc;             // 숨은참조
    private String subject;         // 제목
    private String content;         // 내용 (HTML)
    private boolean saveSentMail;   // 발송 메일함 저장 여부
}
