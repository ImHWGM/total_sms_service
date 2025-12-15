package kr.wisead.domain.message.dto;

import kr.wisead.domain.message.entity.MessageTemplate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 메시지 템플릿 응답 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageTemplateResponse {

    private Long templateSeq;
    private Long userSeq;
    private Integer templateOrder;
    private String sendingForm;
    private String msgType;
    private String subject;
    private String text;
    private LocalDateTime insertTime;
    private String imagePath;

    @Builder
    public MessageTemplateResponse(Long templateSeq, Long userSeq, Integer templateOrder,
                                   String sendingForm, String msgType, String subject,
                                   String text, LocalDateTime insertTime, String imagePath) {
        this.templateSeq = templateSeq;
        this.userSeq = userSeq;
        this.templateOrder = templateOrder;
        this.sendingForm = sendingForm;
        this.msgType = msgType;
        this.subject = subject;
        this.text = text;
        this.insertTime = insertTime;
        this.imagePath = imagePath;
    }

    /**
     * Entity -> Response 변환
     */
    public static MessageTemplateResponse from(MessageTemplate template) {
        return MessageTemplateResponse.builder()
                .templateSeq(template.getTemplateSeq())
                .userSeq(template.getUserSeq())
                .templateOrder(template.getTemplateOrder())
                .sendingForm(template.getSendingForm())
                .msgType(template.getMsgType())
                .subject(template.getSubject())
                .text(template.getText())
                .insertTime(template.getInsertTime())
                .imagePath(template.getImagePath())
                .build();
    }
}
