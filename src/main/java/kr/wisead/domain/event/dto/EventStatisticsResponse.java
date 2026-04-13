package kr.wisead.domain.event.dto;

import java.util.List;
import lombok.*;

/** 행사 통계 응답 DTO */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventStatisticsResponse {

  private Integer eventSeq;
  private String eventName;

  // 참석현황 (attendTime 기준)
  private AttendanceSummary attendanceSummary;

  // 전체 참가자 통계
  private ParticipantSummary participantSummary;

  // RSVP 통계
  private RsvpSummary rsvpSummary;

  // 문자 발송 통계
  private MessageSummary messageSummary;

  // 액션별 통계
  private List<ActionStatistics> actionStatistics;

  // 명찰 출력 통계
  private NametagSummary nametagSummary;

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class AttendanceSummary {
    private int totalCount; // 전체 참가자 수
    private int attendedCount; // 참석 인원 (attendTime 있음)
    private int notAttendedCount; // 미참석 인원 (attendTime 없음)
    private double attendanceRate; // 참석율 (%)
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class ParticipantSummary {
    private int totalCount; // 전체 참가자 수
    private int checkedInCount; // 체크인 완료 수
    private int notCheckedInCount; // 미체크인 수
    private double checkedInRate; // 체크인율 (%)
    private int preRegisteredCount; // 사전등록 수
    private int onsiteRegisteredCount; // 현장등록 수
    private int absentCount; // 사전미참석 수
    private int unregisteredCount; // 미등록 수

    // 참가자 유형별 통계
    private List<ParticipantTypeStat> byType;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class ParticipantTypeStat {
    private String participantType;
    private int count;
    private int checkedInCount;
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class RsvpSummary {
    private int totalCount; // 전체 참가자 수
    private int attendCount; // 참석 의사 (사전등록)
    private int absentCount; // 불참 의사
    private int noResponseCount; // 미응답 (미등록)
    private int onsiteRegisteredCount; // 현장등록
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class MessageSummary {
    private int totalSent; // 전체 발송 수
    private int successCount; // 성공
    private int failCount; // 실패
    private int pendingCount; // 대기중
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class ActionStatistics {
    private Long actionTypeSeq;
    private String actionCode;
    private String actionName;
    private int totalCount; // 전체 참가자 수
    private int completedCount; // 완료 수
    private double completionRate; // 완료율 (%)
    private String requireAdminAuth; // 관리자 인증 필요 여부
  }

  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class NametagSummary {
    private int totalCount; // 전체 참가자 수
    private int printedCount; // 명찰 출력 수
    private int notPrintedCount; // 미출력 수
    private double printRate; // 출력율 (%)
  }
}
