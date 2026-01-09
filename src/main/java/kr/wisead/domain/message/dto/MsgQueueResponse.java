package kr.wisead.domain.message.dto;

import kr.wisead.domain.message.entity.MsgQueue;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 발송 대기 목록 응답 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MsgQueueResponse {

    private Integer mseq;
    private String msgType;
    private String msgTypeName;
    private String dstaddr;           // 수신번호
    private String callback;          // 발신번호
    private String stat;
    private String subject;
    private String text;

    // 첨부파일
    private Integer filecnt;
    private String fileloc1;
    private String fileloc2;
    private String fileloc3;

    // 시간
    private LocalDateTime insertTime;
    private LocalDateTime requestTime;

    // 확장 정보
    private Integer eventSeq;         // 설문 시퀀스 (EXT_COL0)
    private String userKey;           // 배치ID 또는 userSeq (EXT_COL1)
    private String sendType;          // 발송 타입 (EXT_COL2)
    private String regId;             // 등록자 ID (EXT_COL3)

    @Builder
    public MsgQueueResponse(Integer mseq, String msgType, String msgTypeName,
                            String dstaddr, String callback, String stat,
                            String subject, String text,
                            Integer filecnt, String fileloc1, String fileloc2, String fileloc3,
                            LocalDateTime insertTime, LocalDateTime requestTime,
                            Integer eventSeq, String userKey, String sendType, String regId) {
        this.mseq = mseq;
        this.msgType = msgType;
        this.msgTypeName = msgTypeName;
        this.dstaddr = dstaddr;
        this.callback = callback;
        this.stat = stat;
        this.subject = subject;
        this.text = text;
        this.filecnt = filecnt;
        this.fileloc1 = fileloc1;
        this.fileloc2 = fileloc2;
        this.fileloc3 = fileloc3;
        this.insertTime = insertTime;
        this.requestTime = requestTime;
        this.eventSeq = eventSeq;
        this.userKey = userKey;
        this.sendType = sendType;
        this.regId = regId;
    }

    /**
     * Entity -> Response 변환
     */
    public static MsgQueueResponse from(MsgQueue entity) {
        return MsgQueueResponse.builder()
                .mseq(entity.getMseq())
                .msgType(entity.getMsgType())
                .msgTypeName(getMsgTypeName(entity.getMsgType()))
                .dstaddr(entity.getDstaddr())
                .callback(entity.getCallback())
                .stat(entity.getStat())
                .subject(entity.getSubject())
                .text(entity.getText())
                .filecnt(entity.getFilecnt())
                .fileloc1(entity.getFileloc1())
                .fileloc2(entity.getFileloc2())
                .fileloc3(entity.getFileloc3())
                .insertTime(entity.getInsertTime())
                .requestTime(entity.getRequestTime())
                .eventSeq(entity.getExtCol0())
                .userKey(entity.getExtCol1())
                .sendType(entity.getExtCol2())
                .regId(entity.getExtCol3())
                .build();
    }

    private static String getMsgTypeName(String msgType) {
        if (msgType == null) return null;
        return switch (msgType) {
            case "S" -> "SMS";
            case "L" -> "LMS";
            case "M" -> "MMS";
            default -> msgType;
        };
    }
}
