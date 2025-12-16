package kr.wisead.domain.message.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 일반 문자 발송 요청 DTO (Multi Message)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultiMessageRequest {

    /**
     * 메시지 타입 (SMS, LMS, MMS)
     */
    private String messageType;

    /**
     * 발신번호
     */
    private String callback;

    /**
     * 제목 (LMS/MMS용)
     */
    private String subject;

    /**
     * 메시지 내용
     */
    private String text;

    /**
     * 발송 유형 (direct: 즉시, reserve: 예약)
     */
    private String reqType;

    /**
     * 예약 발송 일시 (예약 발송 시)
     */
    private String reqDate;

    /**
     * 중복 번호 제거 여부 (Y/N)
     */
    private String delDuplicateNum;

    /**
     * 수신자 목록 (직접 입력 발송 시)
     */
    private List<ReceiverInfo> receivers;

    /**
     * MMS 파일 경로들
     */
    private String fileLoc1;
    private String fileLoc2;
    private String fileLoc3;
    private Integer fileCnt;

    /**
     * 강제 발송 여부 (특수문자 검증 무시)
     */
    private boolean forceValidation;

    /**
     * 수신자 정보 (대치문자 포함)
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReceiverInfo {
        private String phone;       // 수신번호
        private String text;        // 개별 메시지 내용 (대치문자 적용된)
        private String repChar01;   // 대치문자1
        private String repChar02;   // 대치문자2
        private String repChar03;   // 대치문자3
    }

    /**
     * 하이픈 제거된 발신번호 반환
     */
    public String getNormalizedCallback() {
        return callback != null ? callback.replaceAll("-", "") : null;
    }

    /**
     * 즉시 발송 여부
     */
    public boolean isImmediate() {
        return "direct".equalsIgnoreCase(reqType);
    }

    /**
     * 중복 제거 여부
     */
    public boolean isRemoveDuplicate() {
        return "Y".equalsIgnoreCase(delDuplicateNum);
    }

    /**
     * 메시지 타입 코드 반환 (S/L/M)
     */
    public String getMsgTypeCode() {
        if (messageType == null) return "S";
        return switch (messageType.toUpperCase()) {
            case "LMS" -> "L";
            case "MMS" -> "M";
            default -> "S";
        };
    }
}
