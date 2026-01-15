package kr.wisead.domain.message.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 메시지 발송 큐 Entity (SMS DB - msg_queue)
 *
 * <pre>
 * STAT 상태값:
 * - 0: 대기
 * - 1: 발송중
 * - 2: 처리중
 * - 3: 완료
 *
 * MSG_TYPE:
 * - S: SMS
 * - L: LMS
 * - M: MMS
 *
 * EXT_COL 매핑:
 * [설문 발송 (insertMSGQueue)]
 * - EXT_COL0: eventSeq (설문 시퀀스)
 * - EXT_COL1: userSeq (설문 참여자 시퀀스)
 * - EXT_COL2: txGroupId (결제 거래 그룹 ID, 환불용)
 * - EXT_COL3: regId (등록자 아이디)
 *
 * [일반 문자 발송 (insertMSGQueueSMS/LMS/MMS)]
 * - EXT_COL0: NULL
 * - EXT_COL1: userKey (배치 추적용, yyyyMMdd-HHmmssSSS)
 * - EXT_COL2: txGroupId (결제 거래 그룹 ID, 환불용)
 * - EXT_COL3: regId (등록자 아이디)
 * </pre>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MsgQueue {

    private Integer mseq;
    private String msgType;           // S: SMS, L: LMS, M: MMS
    private String dstaddr;           // 수신번호
    private String callback;          // 발신번호
    private String stat;              // 상태 (0: 대기, 3: 완료)
    private String subject;           // 제목 (LMS/MMS)
    private String text;              // 내용
    private String textEncoding;      // 인코딩 (0)
    private Integer filecnt;          // 첨부파일 개수
    private String fileloc1;          // 첨부파일1 경로
    private Integer filesize1;
    private String fileloc2;
    private Integer filesize2;
    private String fileloc3;
    private Integer filesize3;
    private String fileloc4;
    private Integer filesize4;
    private String fileloc5;
    private Integer filesize5;
    private Integer filecntCheckup;
    private LocalDateTime insertTime;  // 등록 시간
    private LocalDateTime requestTime; // 발송 요청 시간 (예약 발송시 미래 시간)
    private LocalDateTime sendTime;    // 실제 발송 시간
    private LocalDateTime reportTime;  // 리포트 수신 시간
    private LocalDateTime tcprecvTime;
    private LocalDateTime saveTime;
    private String telecom;           // 통신사 (LG, KT, SKT)
    private String result;            // 결과 코드 (0: 성공)
    private String serverId;

    // 카카오 관련 (현재 미사용)
    private String kakaoMethod;       // 기본값: p
    private String senderKey;
    private String templateCode;
    private String kakaoSideInfo;
    private String replaceText;
    private String failover;          // 기본값: 0
    private String kakaoResult;
    private String spamProcess;       // 기본값: 0

    // 발신자 정보
    private String senderCode;        // 발송 업체 코드 (301200115)
    private String dptCode;
    private String natCode;
    private String optId;
    private String optCmp;
    private String optPost;
    private String optName;

    // 확장 컬럼 (업무용) - 설문/일반 발송에 따라 다름
    private Integer extCol0;          // 설문: eventSeq / 일반: NULL
    private String extCol1;           // 설문: userSeq / 일반: userKey(배치ID)
    private String extCol2;           // txGroupId (결제 거래 그룹 ID, 환불용)
    private String extCol3;           // regId (등록자 아이디)

    @Builder
    public MsgQueue(Integer mseq, String msgType, String dstaddr, String callback,
                    String stat, String subject, String text, String textEncoding,
                    Integer filecnt, String fileloc1, Integer filesize1,
                    String fileloc2, Integer filesize2, String fileloc3, Integer filesize3,
                    String fileloc4, Integer filesize4, String fileloc5, Integer filesize5,
                    Integer filecntCheckup, LocalDateTime insertTime, LocalDateTime requestTime,
                    LocalDateTime sendTime, LocalDateTime reportTime, LocalDateTime tcprecvTime,
                    LocalDateTime saveTime, String telecom, String result, String serverId,
                    String kakaoMethod, String senderKey, String templateCode, String kakaoSideInfo,
                    String replaceText, String failover, String kakaoResult, String spamProcess,
                    String senderCode, String dptCode, String natCode, String optId,
                    String optCmp, String optPost, String optName,
                    Integer extCol0, String extCol1, String extCol2, String extCol3) {
        this.mseq = mseq;
        this.msgType = msgType;
        this.dstaddr = dstaddr;
        this.callback = callback;
        this.stat = stat;
        this.subject = subject;
        this.text = text;
        this.textEncoding = textEncoding;
        this.filecnt = filecnt;
        this.fileloc1 = fileloc1;
        this.filesize1 = filesize1;
        this.fileloc2 = fileloc2;
        this.filesize2 = filesize2;
        this.fileloc3 = fileloc3;
        this.filesize3 = filesize3;
        this.fileloc4 = fileloc4;
        this.filesize4 = filesize4;
        this.fileloc5 = fileloc5;
        this.filesize5 = filesize5;
        this.filecntCheckup = filecntCheckup;
        this.insertTime = insertTime;
        this.requestTime = requestTime;
        this.sendTime = sendTime;
        this.reportTime = reportTime;
        this.tcprecvTime = tcprecvTime;
        this.saveTime = saveTime;
        this.telecom = telecom;
        this.result = result;
        this.serverId = serverId;
        this.kakaoMethod = kakaoMethod;
        this.senderKey = senderKey;
        this.templateCode = templateCode;
        this.kakaoSideInfo = kakaoSideInfo;
        this.replaceText = replaceText;
        this.failover = failover;
        this.kakaoResult = kakaoResult;
        this.spamProcess = spamProcess;
        this.senderCode = senderCode;
        this.dptCode = dptCode;
        this.natCode = natCode;
        this.optId = optId;
        this.optCmp = optCmp;
        this.optPost = optPost;
        this.optName = optName;
        this.extCol0 = extCol0;
        this.extCol1 = extCol1;
        this.extCol2 = extCol2;
        this.extCol3 = extCol3;
    }

    /**
     * 배치 ID 생성 (일반 문자 발송용)
     */
    public static String generateUserKey() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS"));
    }

    // ========== 설문 발송용 (EXT_COL0=eventSeq, EXT_COL1=userSeq) ==========

    /**
     * 설문 문자 발송용 빌더
     * @param msgType 메시지 타입 (S/L/M)
     * @param dstaddr 수신번호
     * @param callback 발신번호
     * @param subject 제목 (LMS/MMS용)
     * @param text 내용
     * @param eventSeq 설문 시퀀스
     * @param userSeq 설문 참여자 시퀀스
     * @param txGroupId 결제 거래 그룹 ID (환불용)
     * @param regId 등록자 아이디
     */
    public static MsgQueue createForSurvey(String msgType, String dstaddr, String callback,
                                            String subject, String text,
                                            Integer eventSeq, Integer userSeq,
                                            String txGroupId, String regId) {
        LocalDateTime now = LocalDateTime.now();
        return MsgQueue.builder()
                .msgType(msgType)
                .dstaddr(dstaddr)
                .callback(callback)
                .stat("0")
                .subject("S".equals(msgType) ? "-" : subject)
                .text(text)
                .insertTime(now)
                .requestTime(now)
                .senderCode("301200115")
                .extCol0(eventSeq)
                .extCol1(userSeq != null ? String.valueOf(userSeq) : null)
                .extCol2(txGroupId)
                .extCol3(regId)
                .build();
    }

    // ========== 일반 문자 발송용 (EXT_COL0=NULL, EXT_COL1=userKey) ==========

    /**
     * 일반 SMS 발송용 빌더
     * @param txGroupId 결제 거래 그룹 ID (환불용)
     */
    public static MsgQueue createSms(String dstaddr, String callback, String subject, String text,
                                      String userKey, String txGroupId, String regId) {
        LocalDateTime now = LocalDateTime.now();
        return MsgQueue.builder()
                .msgType("S")
                .dstaddr(dstaddr)
                .callback(callback)
                .stat("0")
                .subject(subject)
                .text(text)
                .filecnt(0)
                .insertTime(now)
                .requestTime(now)
                .senderCode("301200115")
                .extCol0(null)
                .extCol1(userKey)
                .extCol2(txGroupId)
                .extCol3(regId)
                .build();
    }

    /**
     * 일반 LMS 발송용 빌더
     * @param txGroupId 결제 거래 그룹 ID (환불용)
     */
    public static MsgQueue createLms(String dstaddr, String callback, String subject, String text,
                                      String userKey, String txGroupId, String regId) {
        LocalDateTime now = LocalDateTime.now();
        return MsgQueue.builder()
                .msgType("L")
                .dstaddr(dstaddr)
                .callback(callback)
                .stat("0")
                .subject(subject)
                .text(text)
                .filecnt(0)
                .insertTime(now)
                .requestTime(now)
                .senderCode("301200115")
                .extCol0(null)
                .extCol1(userKey)
                .extCol2(txGroupId)
                .extCol3(regId)
                .build();
    }

    /**
     * 일반 MMS 발송용 빌더
     * @param txGroupId 결제 거래 그룹 ID (환불용)
     */
    public static MsgQueue createMms(String dstaddr, String callback, String subject, String text,
                                      int fileCnt, String fileloc1, String fileloc2, String fileloc3,
                                      String userKey, String txGroupId, String regId) {
        LocalDateTime now = LocalDateTime.now();
        return MsgQueue.builder()
                .msgType("M")
                .dstaddr(dstaddr)
                .callback(callback)
                .stat("0")
                .subject(subject)
                .text(text)
                .filecnt(fileCnt)
                .fileloc1(fileloc1)
                .fileloc2(fileloc2)
                .fileloc3(fileloc3)
                .insertTime(now)
                .requestTime(now)
                .senderCode("301200115")
                .extCol0(null)
                .extCol1(userKey)
                .extCol2(txGroupId)
                .extCol3(regId)
                .build();
    }

    /**
     * 예약 발송 시간 설정
     */
    public MsgQueue withRequestTime(LocalDateTime requestTime) {
        return MsgQueue.builder()
                .mseq(this.mseq)
                .msgType(this.msgType)
                .dstaddr(this.dstaddr)
                .callback(this.callback)
                .stat(this.stat)
                .subject(this.subject)
                .text(this.text)
                .textEncoding(this.textEncoding)
                .filecnt(this.filecnt)
                .fileloc1(this.fileloc1)
                .filesize1(this.filesize1)
                .insertTime(this.insertTime)
                .requestTime(requestTime)
                .kakaoMethod(this.kakaoMethod)
                .failover(this.failover)
                .spamProcess(this.spamProcess)
                .senderCode(this.senderCode)
                .extCol0(this.extCol0)
                .extCol1(this.extCol1)
                .extCol2(this.extCol2)
                .extCol3(this.extCol3)
                .build();
    }

    /**
     * 발송 성공 여부
     */
    public boolean isSuccess() {
        return "0".equals(result);
    }

    /**
     * 발송 대기 상태 여부
     */
    public boolean isPending() {
        return "0".equals(stat);
    }

    /**
     * 발송 완료 상태 여부
     */
    public boolean isCompleted() {
        return "3".equals(stat);
    }

    /**
     * 결제 거래 그룹 ID (환불용)
     */
    public String getTxGroupId() {
        return extCol2;
    }
}
