package kr.wisead.domain.event.dto;

import lombok.*;

/**
 * 참가자 검색 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipantSearchRequest {

    private Integer eventSeq;
    private String keyword;             // 이름, 소속, 직책 검색
    private String participantType;     // 참가자 유형 필터
    @Builder.Default
    private Integer page = 1;
    @Builder.Default
    private Integer size = 20;

    public int getOffset() {
        return (page - 1) * size;
    }
}
