package kr.wisead.domain.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 사용자별 통계 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatsResponse {

    private Integer userSeq;            // 사용자 시퀀스
    private String userId;              // 사용자 ID
    private String userName;            // 사용자명
    private String companyName;         // 회사명

    // SMS 통계
    private int smsTotalCnt;            // SMS 총 건수
    private int smsSuccCnt;             // SMS 성공 건수
    private int smsFailCnt;             // SMS 실패 건수

    // LMS 통계
    private int lmsTotalCnt;            // LMS 총 건수
    private int lmsSuccCnt;             // LMS 성공 건수
    private int lmsFailCnt;             // LMS 실패 건수

    // MMS 통계
    private int mmsTotalCnt;            // MMS 총 건수
    private int mmsSuccCnt;             // MMS 성공 건수
    private int mmsFailCnt;             // MMS 실패 건수

    // 카카오 통계
    private int kakaoTotalCnt;          // 카카오 총 건수
    private int kakaoSuccCnt;           // 카카오 성공 건수
    private int kakaoFailCnt;           // 카카오 실패 건수

    // 설문 통계
    private int surveyTotalCnt;         // 설문 총 건수
    private int surveyCompleteCnt;      // 설문 완료 건수

    // QR 통계
    private int qrTotalCnt;             // QR 총 건수
    private int qrScanCnt;              // QR 스캔 건수

    /**
     * 전체 메시지 건수
     */
    public int getTotalMessageCnt() {
        return smsTotalCnt + lmsTotalCnt + mmsTotalCnt + kakaoTotalCnt;
    }

    /**
     * 전체 메시지 성공 건수
     */
    public int getTotalSuccCnt() {
        return smsSuccCnt + lmsSuccCnt + mmsSuccCnt + kakaoSuccCnt;
    }

    /**
     * 전체 메시지 실패 건수
     */
    public int getTotalFailCnt() {
        return smsFailCnt + lmsFailCnt + mmsFailCnt + kakaoFailCnt;
    }
}
