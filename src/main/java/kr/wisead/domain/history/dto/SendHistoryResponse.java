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

    private Long seq;               // 메시지 시퀀스 (mseq)
    private String msgType;         // 메시지 타입
    private String receiver;        // 수신번호 (마스킹 처리됨)
    private String callback;        // 발신번호
    private String subject;         // 제목
    private String text;            // 내용
    private Integer stat;           // 상태 코드
    private String statName;        // 상태 명칭
    private String result;          // 결과
    private Integer fileCnt;        // 파일 개수
    private String fileLoc;         // 파일 경로 (MMS 이미지)
    private LocalDateTime requestTime;  // 요청 시간
    private LocalDateTime sendTime;     // 발송 시간
    private LocalDateTime reportTime;   // 수신 시간
    private String telecom;         // 통신사
    private String sendType;        // 발송 형식
    private String senderId;        // 발신 아이디

    public static SendHistoryResponse from(SendHistory entity) {
        if (entity == null) return null;

        String statName = translateStatus(entity.getStat());
        String maskedReceiver = maskPhoneNumber(entity.getDstAddr());

        return SendHistoryResponse.builder()
                .seq(entity.getMsgKey())
                .msgType(entity.getMsgType())
                .receiver(maskedReceiver)
                .callback(entity.getCallBack())
                .subject(entity.getSubject())
                .text(entity.getText())
                .stat(entity.getStat())
                .statName(statName)
                .result(entity.getResult())
                .fileCnt(entity.getFileCnt())
                .fileLoc(entity.getFileLoc1())
                .requestTime(entity.getRequestTime())
                .sendTime(entity.getSendTime())
                .reportTime(entity.getReportTime())
                .telecom(entity.getTelecom())
                .sendType(entity.getExtCol2())
                .senderId(entity.getExtCol3())
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
            case 3 -> "결과수신";
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
