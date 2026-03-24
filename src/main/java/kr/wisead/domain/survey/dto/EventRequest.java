package kr.wisead.domain.survey.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.*;

/** 이벤트/설문 생성/수정 요청 DTO */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventRequest {

  @NotBlank(message = "이벤트명은 필수입니다.")
  private String eventName; // 이벤트 명

  private String eventEmphasisYn; // 이벤트 명 강조 사용여부

  private String eventDesc; // 이벤트 설명

  @NotBlank(message = "이벤트 타입은 필수입니다.")
  private String eventType; // 이벤트 타입

  @NotBlank(message = "시작일은 필수입니다.")
  private String startDate; // 시작일

  @NotBlank(message = "종료일은 필수입니다.")
  private String endDate; // 종료일

  private String status; // 상태 (A:준비, P:진행, S:중지, F:종료)

  private String privacyPolicyYn; // 개인정보취합 안내 노출여부

  private String privacyPolicyTtl; // 개인정보 취합 타이틀

  private String privacyPolicyDesc; // 개인정보 취합 안내

  private String thirdPartyYn; // 개인정보 제3자 제공 동의 사용여부

  private String thirdPartyTtl; // 개인정보 제3자 제공 동의 타이틀

  private String thirdPartyDesc; // 개인정보 제3자 제공 동의 내용

  @NotBlank(message = "인증 종류는 필수입니다.")
  private String auth; // 인증 종류

  private String qrCode; // QR코드 사용여부

  private String endMessage; // 설문 종료 메시지

  private String eventDescImg; // 이벤트 설명 이미지 경로

  private String eventEndImg; // 설문 종료 이미지 경로

  private String venue; // 행사 장소

  private String organizer; // 주최/주관

  private String badgePrintType; // 서비스 옵션 (N:명찰출력, C:체크인카드)

  private String nametagConfig; // 명찰 템플릿 설정 (JSON 문자열)

  private String staffAuthCode; // 스태프 체크인 인증코드

  // 문항 목록 (설문 생성 시)
  private List<QuestionRequest> questions;

  // 임시 파일 디렉토리 ID (신규 이벤트 생성 시 이미지 파일 이동용)
  private String tempId;
}
