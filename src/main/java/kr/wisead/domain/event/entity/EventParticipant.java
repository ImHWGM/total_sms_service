package kr.wisead.domain.event.entity;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

/** 행사 참가자 확장 정보 Entity (EVENT_PARTICIPANT) SURVEY_USER와 1:1 관계 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EventParticipant {

  private Long seq; // 참가자 시퀀스
  private Integer surveyUserSeq; // SURVEY_USER 시퀀스 (1:1)
  private Integer eventSeq; // 이벤트 시퀀스 (SURVEY_MASTER)
  private String checkCode; // QR용 고유 코드 (UUID)
  private String department; // 소속/부서
  private String position; // 직책
  private String participantType; // 참가자 유형 (VIP, 일반, 스태프 등)
  private String memo; // 메모
  private String nametagPrinted; // 명찰 출력 여부
  private String attendTime; // 행사참석시간 (HH:mm)
  private String registType; // 등록구분 (사전등록/현장등록)
  private LocalDateTime regDate; // 등록일
  private LocalDateTime modDate; // 수정일

  // 조회용 필드 (SURVEY_USER JOIN)
  private String userName; // 참가자 이름
  private String userPhone; // 참가자 연락처
  private String userEmail; // 참가자 이메일

  // 조회용 필드 (SURVEY_MASTER JOIN)
  private String eventName; // 행사명

  // 조회용 필드 (엑셀 다운로드용)
  private String actionSummary; // 액션 수행 현황 요약

  /** 참가자 생성 */
  public static EventParticipant create(
      Integer surveyUserSeq,
      Integer eventSeq,
      String department,
      String position,
      String participantType,
      String memo,
      String registType,
      String attendTime) {
    return EventParticipant.builder()
        .surveyUserSeq(surveyUserSeq)
        .eventSeq(eventSeq)
        .checkCode(UUID.randomUUID().toString().replace("-", ""))
        .department(department)
        .position(position)
        .participantType(participantType)
        .memo(memo)
        .nametagPrinted("N")
        .registType(registType)
        .attendTime(attendTime)
        .build();
  }

  /** 명찰 출력 처리 */
  public void markNametagPrinted() {
    this.nametagPrinted = "Y";
    this.modDate = LocalDateTime.now();
  }

  /** 명찰 출력 여부 확인 */
  public boolean checkNametagPrinted() {
    return "Y".equals(this.nametagPrinted);
  }

  /** 정보 수정 */
  public void update(String department, String position, String participantType, String memo) {
    this.department = department;
    this.position = position;
    this.participantType = participantType;
    this.memo = memo;
    this.modDate = LocalDateTime.now();
  }
}
