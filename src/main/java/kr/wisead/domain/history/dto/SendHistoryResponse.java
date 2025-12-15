package kr.wisead.domain.history.dto;

import kr.wisead.domain.history.entity.SendHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 발송 이력 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendHistoryResponse {

    private Long msgKey;
    private String userId;
    private String msgType;
    private String dstAddr;         // 수신번호 (마스킹 처리됨)
    private String callBack;        // 발신번호
    private String subject;
    private String text;
    private Integer stat;
    private String statName;        // 상태 명칭
    private String result;
    private Integer fileCnt;
    private LocalDateTime requestTime;
    private LocalDateTime sendTime;
    private LocalDateTime reportTime;
    private String telecom;
    private String sendType;        // 발송형식 (extCol2)

    public static SendHistoryResponse from(SendHistory entity) {
        if (entity == null) return null;

        String statName = translateStatus(entity.getStat());
        String maskedDstAddr = maskPhoneNumber(entity.getDstAddr());

        return SendHistoryResponse.builder()
                .msgKey(entity.getMsgKey())
                .userId(entity.getExtCol3())
                .msgType(entity.getMsgType())
                .dstAddr(maskedDstAddr)
                .callBack(entity.getCallBack())
                .subject(entity.getSubject())
                .text(entity.getText())
                .stat(entity.getStat())
                .statName(statName)
                .result(entity.getResult())
                .fileCnt(entity.getFileCnt())
                .requestTime(entity.getRequestTime())
                .sendTime(entity.getSendTime())
                .reportTime(entity.getReportTime())
                .telecom(entity.getTelecom())
                .sendType(entity.getExtCol2())
                .build();
    }

    /**
     * 상태 코드를 명칭으로 변환
     */
    public static String translateStatus(Integer stat) {
        if (stat == null) return "알수없음";

        return switch (stat) {
            case 0 -> "대기";
            case 1 -> "발송중";
            case 2 -> "발송완료";
            case 3 -> "실패";
            case 4 -> "취소";
            default -> "알수없음";
        };
    }

    /**
     * 전화번호 마스킹
     */
    private static String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 7) return phone;

        // 010-1234-5678 -> 010-****-5678
        if (phone.length() == 11) {
            return phone.substring(0, 3) + "-****-" + phone.substring(7);
        }
        // 그 외 형식
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
