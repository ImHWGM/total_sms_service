package kr.wisead.domain.message.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 재발송 요청 DTO */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResendRequest {

  private Integer eventSeq; // 이벤트 시퀀스
  private String eventCode; // 이벤트 코드
  private Integer userSeq; // 사용자 시퀀스 (단건 재발송)
  private List<Integer> userSeqList; // 사용자 시퀀스 목록 (다건 재발송)
  private String subject; // 제목 (새로운 제목으로 재발송 시)
  private String text; // 내용 (새로운 내용으로 재발송 시)
  private String callback; // 발신번호
  private String reqType; // 발송 타입 (direct: 즉시, reserve: 예약)
  private String reqDate; // 예약 발송일시
  private boolean useOriginalContent; // 기존 내용 사용 여부
  private String useUrlYn; // 이전 메시지 URL 추출 사용 여부 (Y/N)
  private String delDuplicateNum; // 중복 제거 여부 (Y/N)

  /** 중복 번호 재발송용 수신자 목록 각 수신자는 phone, userSeq, userKey 등을 포함 */
  private List<DuplicateReceiver> duplicateReceivers;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class DuplicateReceiver {
    private String phone; // 수신번호
    private Integer userSeq; // 사용자 시퀀스
    private String userKey; // 유저키
    private String repChar01; // 대치문자1
    private String repChar02; // 대치문자2
    private String repChar03; // 대치문자3
    private String surveyRepChar01; // 설문대치1
    private String surveyRepChar02; // 설문대치2
    private String surveyRepChar03; // 설문대치3
    private String surveyRepChar04; // 설문대치4
    private String surveyRepChar05; // 설문대치5
  }
}
