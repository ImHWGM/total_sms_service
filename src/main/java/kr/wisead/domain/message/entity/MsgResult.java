package kr.wisead.domain.message.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 메시지 발송 이력 Entity (SMS DB - msg_result_YYYYMM)
 * 테이블명이 월별로 동적 생성됨 (예: msg_result_202512)
 *
 * <pre>
 * STAT 상태값:
 * - 0: 전송대기
 * - 1: 송신중
 * - 2: 송신완료
 * - 3: 결과수신
 *
 * RESULT 결과코드:
 * - 0: 성공
 * - 그 외: 실패 (Error Code 표 참조)
 *
 * EXT_COL 매핑:
 * [설문 발송]
 * - EXT_COL0: eventSeq (설문 시퀀스)
 * - EXT_COL1: userSeq (설문 참여자 시퀀스)
 * - EXT_COL2: 발송타입 (1: 직접, 2: 대량)
 * - EXT_COL3: regId (등록자 아이디)
 *
 * [일반 문자 발송]
 * - EXT_COL0: NULL
 * - EXT_COL1: userKey (배치 추적용)
 * - EXT_COL2: 발송타입 (1: 직접, 2: 대량)
 * - EXT_COL3: regId (등록자 아이디)
 * </pre>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MsgResult {

    private Integer mseq;
    private String msgType;           // S: SMS, L: LMS, M: MMS
    private String dstaddr;           // 수신번호
    private String callback;          // 발신번호
    private Integer stat;             // 상태 (0: 대기, 1: 송신중, 2: 송신완료, 3: 결과수신)
    private String subject;           // 제목 (LMS/MMS)
    private String text;              // 내용

    // 첨부파일 정보 (MMS)
    private Integer filecnt;
    private String fileloc1;
    private Integer filesize1;
    private String fileloc2;
    private Integer filesize2;
    private String fileloc3;
    private Integer filesize3;

    // 시간 정보
    private LocalDateTime requestTime; // 발송 요청 시간
    private LocalDateTime sendTime;    // 실제 발송 시간
    private LocalDateTime reportTime;  // 결과 수신 시간

    // 결과 정보
    private String telecom;           // 통신사 (SK, KT, LG, KKO, ETC)
    private String result;            // 결과 코드 (0: 성공)

    // 확장 컬럼
    private Integer extCol0;          // 설문: eventSeq / 일반: NULL
    private String extCol1;           // 설문: userSeq / 일반: userKey
    private String extCol2;           // 발송타입 (1: 직접, 2: 대량)
    private String extCol3;           // regId (등록자 아이디)

    @Builder
    public MsgResult(Integer mseq, String msgType, String dstaddr, String callback,
                     Integer stat, String subject, String text,
                     Integer filecnt, String fileloc1, Integer filesize1,
                     String fileloc2, Integer filesize2, String fileloc3, Integer filesize3,
                     LocalDateTime requestTime, LocalDateTime sendTime, LocalDateTime reportTime,
                     String telecom, String result,
                     Integer extCol0, String extCol1, String extCol2, String extCol3) {
        this.mseq = mseq;
        this.msgType = msgType;
        this.dstaddr = dstaddr;
        this.callback = callback;
        this.stat = stat;
        this.subject = subject;
        this.text = text;
        this.filecnt = filecnt;
        this.fileloc1 = fileloc1;
        this.filesize1 = filesize1;
        this.fileloc2 = fileloc2;
        this.filesize2 = filesize2;
        this.fileloc3 = fileloc3;
        this.filesize3 = filesize3;
        this.requestTime = requestTime;
        this.sendTime = sendTime;
        this.reportTime = reportTime;
        this.telecom = telecom;
        this.result = result;
        this.extCol0 = extCol0;
        this.extCol1 = extCol1;
        this.extCol2 = extCol2;
        this.extCol3 = extCol3;
    }

    /**
     * 테이블명 생성 (현재 월)
     */
    public static String getTableName() {
        return "msg_result_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    /**
     * 테이블명 생성 (특정 날짜 기준)
     */
    public static String getTableName(LocalDateTime dateTime) {
        return "msg_result_" + dateTime.format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    /**
     * 발송 성공 여부
     */
    public boolean isSuccess() {
        return "0".equals(result);
    }

    /**
     * 발송 실패 여부
     */
    public boolean isFailed() {
        return result != null && !"0".equals(result);
    }

    /**
     * 메시지 타입명
     */
    public String getMsgTypeName() {
        return switch (msgType) {
            case "S" -> "SMS";
            case "L" -> "LMS";
            case "M" -> "MMS";
            default -> msgType;
        };
    }

    /**
     * 결과 코드명
     */
    public String getResultName() {
        if ("0".equals(result)) {
            return "성공";
        }
        return "실패 (" + result + ")";
    }

    /**
     * 발송 타입명
     */
    public String getSendTypeName() {
        return switch (extCol2) {
            case "1" -> "직접발송";
            case "2" -> "대량발송";
            default -> extCol2;
        };
    }
}
