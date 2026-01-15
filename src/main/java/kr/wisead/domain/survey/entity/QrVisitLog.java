package kr.wisead.domain.survey.entity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * QR 코드 방문 로그 엔티티
 */
@Getter
@NoArgsConstructor
public class QrVisitLog {

    private Integer seq;              // 방문 시퀀스
    private Integer eventSeq;         // 이벤트 시퀀스
    private String eventStatus;       // 방문 시점의 이벤트 상태
    private LocalDateTime visitDate;  // 방문 일시

    @Builder
    public QrVisitLog(Integer seq, Integer eventSeq, String eventStatus, LocalDateTime visitDate) {
        this.seq = seq;
        this.eventSeq = eventSeq;
        this.eventStatus = eventStatus;
        this.visitDate = visitDate;
    }

    /**
     * QR 방문 로그 생성
     */
    public static QrVisitLog create(Integer eventSeq, String eventStatus) {
        return QrVisitLog.builder()
                .eventSeq(eventSeq)
                .eventStatus(eventStatus)
                .build();
    }
}