package kr.wisead.domain.survey.dto;

import java.time.LocalDateTime;
import java.util.List;
import kr.wisead.domain.survey.entity.SurveyMaster;
import lombok.*;

/** 이벤트/설문 응답 DTO */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EventResponse {

  private Integer eventSeq; // 이벤트 시퀀스
  private String eventCode; // 이벤트 코드
  private String eventName; // 이벤트 명
  private String eventEmphasisYn; // 이벤트 명 강조 사용여부
  private String eventDescImg; // 이벤트 설명 이미지
  private String eventDesc; // 이벤트 설명
  private String eventType; // 이벤트 타입
  private String startDate; // 시작일
  private String endDate; // 종료일
  private String status; // 상태
  private String statusName; // 상태명
  private String privacyPolicyYn; // 개인정보취합 안내 노출여부
  private String privacyPolicyTtl; // 개인정보 취합 타이틀
  private String privacyPolicyDesc; // 개인정보 취합 안내
  private String auth; // 인증 종류
  private String authKeyDesc; // 범용인증키 설명
  private String qrCode; // QR코드 사용여부
  private String qrCodeImgPath; // QR코드 이미지 경로
  private String authCodeUrl; // QR코드 간편 URL
  private String endMessage; // 설문 종료 메시지
  private String eventEndImg; // 설문 마무리 이미지
  private String venue; // 행사 장소
  private String organizer; // 주최/주관
  private String badgePrintYn; // 출입증 출력 여부
  private LocalDateTime regDate; // 등록일
  private String regId; // 등록 ID

  // 집계 정보
  private Integer totSurveyUser; // 설문 대상자 수
  private Integer resSurveyUser; // 설문 참여자 수
  private Double responseRate; // 응답률

  // 상세 조회용
  private List<QuestionResponse> questions;

  /** Entity -> Response 변환 */
  public static EventResponse from(SurveyMaster entity) {
    String statusName =
        switch (entity.getStatus()) {
          case "A" -> "준비";
          case "P" -> "진행";
          case "S" -> "중지";
          case "F" -> "종료";
          default -> entity.getStatus();
        };

    Double responseRate = null;
    if (entity.getTotSurveyUser() != null && entity.getTotSurveyUser() > 0) {
      int res = entity.getResSurveyUser() != null ? entity.getResSurveyUser() : 0;
      responseRate = (double) res / entity.getTotSurveyUser() * 100;
    }

    return EventResponse.builder()
        .eventSeq(entity.getEventSeq())
        .eventCode(entity.getEventCode())
        .eventName(entity.getEventName())
        .eventEmphasisYn(entity.getEventEmphasisYn())
        .eventDescImg(entity.getEventDescImg())
        .eventDesc(entity.getEventDesc())
        .eventType(entity.getEventType())
        .startDate(entity.getStartDate())
        .endDate(entity.getEndDate())
        .status(entity.getStatus())
        .statusName(statusName)
        .privacyPolicyYn(entity.getPrivacyPolicyYn())
        .privacyPolicyTtl(entity.getPrivacyPolicyTtl())
        .privacyPolicyDesc(entity.getPrivacyPolicyDesc())
        .auth(entity.getAuth())
        .authKeyDesc(entity.getAuthKeyDesc())
        .qrCode(entity.getQrCode())
        .qrCodeImgPath(entity.getQrCodeImgPath())
        .authCodeUrl(entity.getAuthCodeUrl())
        .endMessage(entity.getEndMessage())
        .eventEndImg(entity.getEventEndImg())
        .venue(entity.getVenue())
        .organizer(entity.getOrganizer())
        .badgePrintYn(entity.getBadgePrintYn())
        .regDate(entity.getRegDate())
        .regId(entity.getRegId())
        .totSurveyUser(entity.getTotSurveyUser())
        .resSurveyUser(entity.getResSurveyUser())
        .responseRate(responseRate)
        .build();
  }

  /** 문항 목록 추가 */
  public EventResponse withQuestions(List<QuestionResponse> questions) {
    this.questions = questions;
    return this;
  }

  /**
   * 이미지 URL을 절대 경로로 변환
   *
   * @param apiBaseUrl API 서버 base URL (예: https://twisead-api.epopkon.com)
   */
  public EventResponse withFullImageUrls(String apiBaseUrl) {
    this.eventDescImg = kr.wisead.common.util.UrlUtils.toAbsoluteUrl(this.eventDescImg, apiBaseUrl);
    this.eventEndImg = kr.wisead.common.util.UrlUtils.toAbsoluteUrl(this.eventEndImg, apiBaseUrl);
    this.qrCodeImgPath =
        kr.wisead.common.util.UrlUtils.toAbsoluteUrl(this.qrCodeImgPath, apiBaseUrl);

    if (this.questions != null) {
      this.questions.forEach(q -> q.withFullImageUrls(apiBaseUrl));
    }
    return this;
  }
}
