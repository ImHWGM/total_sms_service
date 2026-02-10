package kr.wisead.domain.survey.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.survey.entity.SurveyUser;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

/** 설문 참여자 응답 DTO */
@Slf4j
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SurveyUserResponse {

  private Integer userSeq; // 사용자 시퀀스
  private Integer eventSeq; // 이벤트 시퀀스
  private String userKey; // 사용자 키
  private String userName; // 사용자 명
  private String juminNum; // 주민등록번호 (마스킹)
  private String userPhone; // 휴대전화 (마스킹)
  private String resendUserPhone; // 재발송 전화번호 (마스킹)
  private String userEmail; // 이메일
  private String address; // 주소
  private String address2; // 상세주소
  private LocalDate depositDate; // 입금일자
  private LocalDate shipmentDate; // 배송일자
  private LocalDateTime submissionDate; // 설문완료일
  private LocalDateTime surveyStartTime; // 설문접속일
  private LocalDateTime regDate; // 등록일
  private String status; // 상태 (참여/미참여/접속중)

  // 조회용
  private String eventName; // 이벤트 명
  private String eventCode; // 이벤트 코드
  private String eventType; // 이벤트 타입 (P:개인정보, S:설문조사)
  private String generalAuthCode; // 범용인증코드
  private String privacyPolicyYn; // 개인정보취합 안내 노출여부
  private String privacyPolicyTtl; // 개인정보 취합 타이틀
  private String privacyPolicyDesc; // 개인정보 취합 안내
  private String corpName; // 고객사명

  /** Entity -> Response 변환 (암호화된 개인정보 복호화 포함) */
  public static SurveyUserResponse from(SurveyUser entity) {
    String status;
    if (entity.getSubmissionDate() != null) {
      status = "참여완료";
    } else if (entity.getSurveyStartTime() != null) {
      status = "접속중";
    } else {
      status = "미참여";
    }

    // 복호화된 전화번호를 마스킹
    String decryptedPhone = decryptField(entity.getUserPhone());
    String decryptedResendPhone = decryptField(entity.getResendUserPhone());

    return SurveyUserResponse.builder()
        .userSeq(entity.getSeq())
        .eventSeq(entity.getEventSeq())
        .userKey(entity.getUserKey())
        .userName(decryptField(entity.getUserName()))
        .juminNum(maskJuminNum(decryptField(entity.getJuminNum())))
        .userPhone(maskPhone(decryptedPhone))
        .resendUserPhone(maskPhone(decryptedResendPhone))
        .userEmail(decryptField(entity.getUserEmail()))
        .address(decryptField(entity.getAddress()))
        .address2(decryptField(entity.getAddress2()))
        .depositDate(entity.getDepositDate())
        .shipmentDate(entity.getShipmentDate())
        .submissionDate(entity.getSubmissionDate())
        .surveyStartTime(entity.getSurveyStartTime())
        .regDate(entity.getRegDate())
        .status(status)
        .eventName(entity.getEventName())
        .eventCode(entity.getEventCode())
        .eventType(entity.getEventType())
        .generalAuthCode(entity.getGeneralAuthCode())
        .build();
  }

  /** 암호화된 필드 복호화 (AES256 + Base64) */
  private static String decryptField(String encryptedValue) {
    if (encryptedValue == null || encryptedValue.isEmpty()) {
      return encryptedValue;
    }
    try {
      return CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(encryptedValue));
    } catch (Exception e) {
      log.debug("필드 복호화 실패, 원본 반환: {}", e.getMessage());
      return encryptedValue;
    }
  }

  /** 전화번호 마스킹 */
  private static String maskPhone(String phone) {
    if (phone == null || phone.length() < 4) {
      return phone;
    }
    // 뒷 4자리만 표시
    return "*".repeat(phone.length() - 4) + phone.substring(phone.length() - 4);
  }

  /** 주민번호 마스킹 (앞 6자리만 표시) */
  private static String maskJuminNum(String juminNum) {
    if (juminNum == null || juminNum.isEmpty()) {
      return juminNum;
    }
    // 외국인 ID인 경우 (FOREIGN: 접두사)
    if (juminNum.startsWith("FOREIGN:")) {
      return maskForeignId(juminNum.substring("FOREIGN:".length()));
    }
    if (juminNum.length() < 7) {
      return juminNum;
    }
    // 앞 6자리만 표시, 나머지 마스킹
    return juminNum.substring(0, 6) + "-*******";
  }

  private static String maskForeignId(String foreignId) {
    if (foreignId == null || foreignId.isEmpty()) {
      return foreignId;
    }
    if (foreignId.length() <= 4) {
      return foreignId.substring(0, 1) + "***";
    }
    return foreignId.substring(0, 2)
        + "*".repeat(foreignId.length() - 4)
        + foreignId.substring(foreignId.length() - 2);
  }
}
