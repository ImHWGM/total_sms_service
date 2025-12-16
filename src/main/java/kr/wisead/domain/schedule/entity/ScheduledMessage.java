package kr.wisead.domain.schedule.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 예약 메시지 Entity (msg_queue 테이블)
 * REQUEST_TIME이 미래인 경우 예약 메시지로 대기
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledMessage {

    private Integer mSeq;           // 메시지 시퀀스
    private String msgType;         // 발송타입 (S: SMS, L: LMS, M: MMS)
    private String dstAddr;         // 수신전화번호
    private String callBack;        // 발송전화번호
    private Integer stat;           // 상태 (0: 대기)
    private String subject;         // 제목 (LMS, MMS)
    private String text;            // 문자 내용
    private LocalDateTime insertTime;   // 등록일시
    private LocalDateTime requestTime;  // 예약발송일시
    private String senderCode;      // 발송코드
    private Integer extCol0;        // EVENT_SEQ
    private String extCol1;         // SURVEY_USER_SEQ or YYYYMMdd-HHmmssSSS
    private String extCol2;         // 발송타입 (직접등록, 대량발송)
    private String extCol3;         // 등록 아이디
    private Integer messageCount;   // 그룹별 메시지 건수 (조회용)
    private Integer fileCnt;        // 첨부 파일 개수
    private String fileLoc1;
    private String fileLoc2;
    private String fileLoc3;
    private String fileLoc4;
    private String fileLoc5;
}
