package kr.wisead.domain.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 메시지 템플릿 생성/수정 요청 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageTemplateRequest {

    private String sendingForm;      // 발송 형태 (I: 즉시, R: 예약)

    @NotBlank(message = "메시지 타입은 필수입니다.")
    private String msgType;          // 메시지 타입 (SMS, LMS, MMS)

    @Size(max = 128, message = "제목은 128자 이내로 입력해주세요.")
    private String subject;          // 제목 (LMS/MMS용)

    @NotBlank(message = "메시지 내용은 필수입니다.")
    @Size(max = 4000, message = "메시지 내용은 4000자 이내로 입력해주세요.")
    private String text;             // 내용

    private String imagePath;        // MMS 이미지 경로

    @Builder
    public MessageTemplateRequest(String sendingForm, String msgType, String subject,
                                  String text, String imagePath) {
        this.sendingForm = sendingForm;
        this.msgType = msgType;
        this.subject = subject;
        this.text = text;
        this.imagePath = imagePath;
    }
}
