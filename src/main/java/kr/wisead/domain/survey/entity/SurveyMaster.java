package kr.wisead.domain.survey.entity;

import java.time.LocalDateTime;
import lombok.*;

/** 설문 마스터 Entity (SURVEY_MASTER) */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SurveyMaster {

  private Integer eventSeq; // 이벤트 시퀀스
  private Integer userSeq; // 회원 시퀀스
  private String eventCode; // 이벤트 코드
  private String eventName; // 이벤트 명(타이틀)
  private String eventEmphasisYn; // 이벤트 명 강조 사용여부
  private String eventDescImg; // 이벤트 설명 이미지
  private String eventDesc; // 이벤트 설명
  private String eventType; // 이벤트 타입
  private String startDate; // 이벤트 시작일
  private String endDate; // 이벤트 종료일
  private String status; // 이벤트 진행상태 (A:준비, P:진행, S:중지, F:종료)
  private String privacyPolicyYn; // 개인정보취합 안내 노출여부
  private String privacyPolicyTtl; // 개인정보 취합 타이틀
  private String privacyPolicyDesc; // 개인정보 취합 안내
  private String thirdPartyYn; // 개인정보 제3자 제공 동의 사용여부
  private String thirdPartyTtl; // 개인정보 제3자 제공 동의 타이틀
  private String thirdPartyDesc; // 개인정보 제3자 제공 동의 내용
  private String auth; // 사용인증 종류
  private String authKeyDesc; // 범용인증키 설명 문구
  private String qrCode; // QR코드 사용여부
  private String qrCodeImgPath; // QR코드 이미지 경로
  private String authCodeUrl; // QR코드 간편 URL
  private String endMessage; // 설문 종료 메시지
  private String eventEndImg; // 설문 마무리 이미지
  private LocalDateTime regDate; // 등록일
  private String regId; // 등록 ID
  private LocalDateTime uptDate; // 수정일
  private String uptId; // 수정 ID
  private String venue; // 행사 장소
  private String organizer; // 주최/주관
  private String badgePrintType; // 출입증 출력 여부
  private String nametagConfig; // 명찰 템플릿 설정 (JSON 문자열)

  // 집계 정보 (조회용)
  private Integer totSurveyUser; // 설문 대상자 수
  private Integer resSurveyUser; // 설문 참여자 수

  /** 이벤트 생성 */
  public static SurveyMaster create(
      Integer userSeq,
      String eventCode,
      String eventName,
      String eventType,
      String startDate,
      String endDate,
      String auth,
      String venue,
      String organizer,
      String badgePrintType,
      String nametagConfig,
      String regId) {
    return SurveyMaster.builder()
        .userSeq(userSeq)
        .eventCode(eventCode)
        .eventName(eventName)
        .eventType(eventType)
        .startDate(startDate)
        .endDate(endDate)
        .status("A")
        .auth(auth)
        .venue(venue)
        .organizer(organizer)
        .badgePrintType(badgePrintType)
        .nametagConfig(nametagConfig)
        .regId(regId)
        .build();
  }

  /** 이벤트 정보 수정 */
  public void update(
      String eventName,
      String eventEmphasisYn,
      String eventType,
      String eventDesc,
      String startDate,
      String endDate,
      String status,
      String privacyPolicyYn,
      String privacyPolicyTtl,
      String privacyPolicyDesc,
      String thirdPartyYn,
      String thirdPartyTtl,
      String thirdPartyDesc,
      String auth,
      String qrCode,
      String endMessage,
      String eventDescImg,
      String eventEndImg,
      String venue,
      String organizer,
      String badgePrintType,
      String nametagConfig,
      String uptId) {
    this.eventName = eventName;
    this.eventEmphasisYn = eventEmphasisYn;
    this.eventType = eventType;
    this.eventDesc = eventDesc;
    this.startDate = startDate;
    this.endDate = endDate;
    this.status = status;
    this.privacyPolicyYn = privacyPolicyYn;
    this.privacyPolicyTtl = privacyPolicyTtl;
    this.privacyPolicyDesc = privacyPolicyDesc;
    this.thirdPartyYn = thirdPartyYn;
    this.thirdPartyTtl = thirdPartyTtl;
    this.thirdPartyDesc = thirdPartyDesc;
    this.auth = auth;
    this.qrCode = qrCode;
    this.endMessage = endMessage;
    this.eventDescImg = eventDescImg;
    this.eventEndImg = eventEndImg;
    this.venue = venue;
    this.organizer = organizer;
    this.badgePrintType = badgePrintType;
    this.nametagConfig = nametagConfig;
    this.uptId = uptId;
  }

  /** 이벤트가 진행 중인지 확인 */
  public boolean isActive() {
    return "P".equals(this.status);
  }

  /** 이벤트가 종료되었는지 확인 */
  public boolean isFinished() {
    return "F".equals(this.status);
  }

  /** 상태 변경 */
  public void changeStatus(String status) {
    this.status = status;
  }

  /** 설명 이미지 경로 설정 */
  public void setDescImg(String eventDescImg) {
    this.eventDescImg = eventDescImg;
  }

  /** 종료 이미지 경로 설정 */
  public void setEndImg(String eventEndImg) {
    this.eventEndImg = eventEndImg;
  }

  /** QR코드 정보 설정 */
  public void setQrCodeInfo(String qrCodeImgPath, String authCodeUrl) {
    this.qrCodeImgPath = qrCodeImgPath;
    this.authCodeUrl = authCodeUrl;
  }
}
