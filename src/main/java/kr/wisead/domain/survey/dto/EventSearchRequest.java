package kr.wisead.domain.survey.dto;

import java.util.List;
import lombok.*;

/** 이벤트 검색 요청 DTO */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventSearchRequest {

  private Integer userSeq; // 관리자 시퀀스
  private String regId; // 등록자 ID
  private Integer userLevel; // 사용자 레벨
  private List<String> queryUserIds; // 권한별 조회 대상 사용자 ID 목록 (null이면 전체 조회)

  private String eventType; // 이벤트 타입 (단일)
  private List<String> eventTypes; // 이벤트 타입 목록 (복수, 예: S,P)
  private String surveyStatus; // 진행 상태
  private String startDate; // 시작일 (검색)
  private String endDate; // 종료일 (검색)
  private String searchKeyword; // 검색 키워드

  private String sortField; // 정렬 필드
  private String sortOrder; // 정렬 순서 (asc/desc)

  @Builder.Default private int pageNum = 1; // 페이지 번호
  @Builder.Default private int amount = 20; // 페이지 크기

  /** MyBatis 페이징용 offset */
  public int getRowStart() {
    return (pageNum - 1) * amount;
  }

  public int getRowSize() {
    return amount;
  }
}
