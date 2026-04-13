package kr.wisead.domain.event.dto;

import java.util.List;
import lombok.*;

/** 참가자 검색 요청 DTO */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParticipantSearchRequest {

  private Integer eventSeq;
  private String keyword; // 이름, 소속, 직책 검색
  private List<String> participantTypes; // 참가자 유형 포함 필터 (복수 선택 가능)
  private List<String> excludeParticipantTypes; // 참가자 유형 제외 필터 (복수 선택 가능)
  private String registType; // 등록구분 필터 (사전등록/현장등록/사전미참석, null=전체)
  private String attendStatus; // 참석여부 필터 (attended=참석, notAttended=미참석, null=전체)
  @Builder.Default private Integer page = 1;
  @Builder.Default private Integer size = 20;

  public int getOffset() {
    return (page - 1) * size;
  }
}
