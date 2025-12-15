package kr.wisead.domain.message.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 메시지 템플릿 Entity
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageTemplate {

    private Long templateSeq;
    private Long userSeq;
    private Integer templateOrder;
    private String sendingForm;      // 발송 형태 (I: 즉시, R: 예약)
    private String msgType;          // 메시지 타입 (SMS, LMS, MMS)
    private String subject;          // 제목 (LMS/MMS용)
    private String text;             // 내용
    private LocalDateTime insertTime;
    private String imagePath;        // MMS 이미지 경로

    @Builder
    public MessageTemplate(Long templateSeq, Long userSeq, Integer templateOrder,
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
     * 템플릿 수정
     */
    public void update(String sendingForm, String msgType, String subject,
                       String text, String imagePath) {
        this.sendingForm = sendingForm;
        this.msgType = msgType;
        this.subject = subject;
        this.text = text;
        this.imagePath = imagePath;
    }

    /**
     * 순서 변경
     */
    public void changeOrder(Integer newOrder) {
        this.templateOrder = newOrder;
    }

    /**
     * SMS 타입인지 확인
     */
    public boolean isSms() {
        return "SMS".equals(this.msgType);
    }

    /**
     * LMS 타입인지 확인
     */
    public boolean isLms() {
        return "LMS".equals(this.msgType);
    }

    /**
     * MMS 타입인지 확인
     */
    public boolean isMms() {
        return "MMS".equals(this.msgType);
    }
}
