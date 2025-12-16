package kr.wisead.domain.message.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 재발송 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResendRequest {

    private Integer eventSeq;               // 이벤트 시퀀스
    private Integer userSeq;                // 사용자 시퀀스 (단건 재발송)
    private List<Integer> userSeqList;      // 사용자 시퀀스 목록 (다건 재발송)
    private String subject;                 // 제목 (새로운 제목으로 재발송 시)
    private String text;                    // 내용 (새로운 내용으로 재발송 시)
    private String callback;                // 발신번호
    private String reqType;                 // 발송 타입 (direct: 즉시, reserve: 예약)
    private String reqDate;                 // 예약 발송일시
    private boolean useOriginalContent;     // 기존 내용 사용 여부
}
