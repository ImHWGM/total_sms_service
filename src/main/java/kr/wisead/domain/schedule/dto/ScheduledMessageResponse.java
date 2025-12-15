package kr.wisead.domain.schedule.dto;

import kr.wisead.domain.schedule.entity.ScheduledMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 예약 메시지 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledMessageResponse {

    private Integer mSeq;
    private String msgType;
    private String msgTypeName;
    private String dstAddr;
    private String callBack;
    private Integer stat;
    private String statName;
    private String subject;
    private String text;
    private LocalDateTime insertTime;
    private LocalDateTime requestTime;
    private String sendType;        // 발송타입 (extCol2)
    private String userId;          // 등록자 ID (extCol3)
    private Integer messageCount;
    private Integer fileCnt;

    public static ScheduledMessageResponse from(ScheduledMessage entity) {
        if (entity == null) return null;

        String msgTypeName = switch (entity.getMsgType()) {
            case "S" -> "SMS";
            case "L" -> "LMS";
            case "M" -> "MMS";
            default -> entity.getMsgType();
        };

        String statName = entity.getStat() != null && entity.getStat() == 0 ? "대기" : "처리중";

        return ScheduledMessageResponse.builder()
                .mSeq(entity.getMSeq())
                .msgType(entity.getMsgType())
                .msgTypeName(msgTypeName)
                .dstAddr(maskPhoneNumber(entity.getDstAddr()))
                .callBack(entity.getCallBack())
                .stat(entity.getStat())
                .statName(statName)
                .subject(entity.getSubject())
                .text(entity.getText())
                .insertTime(entity.getInsertTime())
                .requestTime(entity.getRequestTime())
                .sendType(entity.getExtCol2())
                .userId(entity.getExtCol3())
                .messageCount(entity.getMessageCount())
                .fileCnt(entity.getFileCnt())
                .build();
    }

    private static String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        if (phone.length() == 11) {
            return phone.substring(0, 3) + "-****-" + phone.substring(7);
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
