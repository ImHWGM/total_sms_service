package kr.wisead.domain.history.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 발송 이력 Entity (msg_result_YYYYMM 테이블)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendHistory {

    private Long msgKey;            // 메시지 키
    private String userId;          // 사용자 ID (ext_col3)
    private String msgType;         // 메시지 타입 (SMS, LMS, MMS)
    private String dstAddr;         // 수신번호
    private String callBack;        // 발신번호
    private String subject;         // 제목
    private String text;            // 내용
    private Integer stat;           // 상태 (0: 대기, 1: 발송중, 2: 발송완료, 3: 실패, 4: 취소)
    private String result;          // 발신결과
    private Integer fileCnt;        // 파일 개수
    private String fileLoc1;        // 파일 경로1 (MMS 이미지)
    private LocalDateTime requestTime;  // 요청시간
    private LocalDateTime sendTime;     // 발송시간
    private LocalDateTime reportTime;   // 수신시간
    private String telecom;         // 통신사
    private String extCol2;         // 발송형식
    private String extCol3;         // 발신 아이디
}
