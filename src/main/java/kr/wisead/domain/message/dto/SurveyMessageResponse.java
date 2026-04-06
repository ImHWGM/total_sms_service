package kr.wisead.domain.message.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** 설문 문자 발송 응답 DTO */
@Getter
@Builder
public class SurveyMessageResponse {

  private boolean success;
  private String message;
  private int successCount;
  private int failCount;
  private int duplicateCount;
  private List<String> failedPhones;
  private int blockedCount;
  private List<String> blockedNumbers;
  private boolean allBlocked;
  private List<Integer> mseqList;
  private String txGroupId;
  private LocalDateTime sendTime;

  /** 성공 응답 */
  public static SurveyMessageResponse success(
      int successCount, List<Integer> mseqList, String txGroupId, LocalDateTime sendTime) {
    return SurveyMessageResponse.builder()
        .success(true)
        .message("설문 문자 발송이 등록되었습니다.")
        .successCount(successCount)
        .failCount(0)
        .duplicateCount(0)
        .mseqList(mseqList)
        .txGroupId(txGroupId)
        .sendTime(sendTime)
        .build();
  }

  /** 부분 성공 응답 */
  public static SurveyMessageResponse partial(
      int successCount,
      int failCount,
      int duplicateCount,
      List<String> failedPhones,
      List<Integer> mseqList,
      String txGroupId) {
    return partial(
        successCount, failCount, duplicateCount, 0, failedPhones, List.of(), mseqList, txGroupId);
  }

  /** 실패 응답 */
  public static SurveyMessageResponse fail(String message) {
    return SurveyMessageResponse.builder()
        .success(false)
        .message(message)
        .successCount(0)
        .failCount(0)
        .duplicateCount(0)
        .sendTime(LocalDateTime.now())
        .build();
  }

  /** 잔액 부족 응답 */
  public static SurveyMessageResponse insufficientBalance(String message) {
    return SurveyMessageResponse.builder()
        .success(false)
        .message(message != null ? message : "잔액이 부족합니다.")
        .successCount(0)
        .failCount(0)
        .duplicateCount(0)
        .sendTime(LocalDateTime.now())
        .build();
  }

  /** 수신거부로 전체 번호 차단 응답 */
  public static SurveyMessageResponse allBlocked(int blockedCount, List<String> blockedNumbers) {
    return SurveyMessageResponse.builder()
        .success(false)
        .message(
            String.format(
                "설문 문자 발송 실패: 수신거부 등록으로 인해 모든 번호가 제외되었습니다.\r\n수신거부 제외: %d건", blockedCount))
        .successCount(0)
        .failCount(0)
        .duplicateCount(0)
        .blockedCount(blockedCount)
        .blockedNumbers(blockedNumbers)
        .allBlocked(true)
        .sendTime(LocalDateTime.now())
        .build();
  }

  /** 야간 전송 제한 응답 */
  public static SurveyMessageResponse nightTimeRestricted() {
    return SurveyMessageResponse.builder()
        .success(false)
        .message("현재 야간 전송제한 시간입니다.\r\n금일 20:00 ~ 익일 09:00까지는 광고문자 전송이 제한됩니다.")
        .successCount(0)
        .failCount(0)
        .duplicateCount(0)
        .sendTime(LocalDateTime.now())
        .build();
  }

  /** 부분 성공 응답 (수신거부 정보 포함) */
  public static SurveyMessageResponse partial(
      int successCount,
      int failCount,
      int duplicateCount,
      int blockedCount,
      List<String> failedPhones,
      List<String> blockedNumbers,
      List<Integer> mseqList,
      String txGroupId) {
    String message =
        String.format(
            "설문 문자 발송 완료 (성공: %d건, 실패: %d건, 중복: %d건", successCount, failCount, duplicateCount);
    if (blockedCount > 0) {
      message += String.format(", 수신거부 제외: %d건", blockedCount);
    }
    message += ")";
    return SurveyMessageResponse.builder()
        .success(true)
        .message(message)
        .successCount(successCount)
        .failCount(failCount)
        .duplicateCount(duplicateCount)
        .blockedCount(blockedCount)
        .failedPhones(failedPhones)
        .blockedNumbers(blockedNumbers)
        .mseqList(mseqList)
        .txGroupId(txGroupId)
        .sendTime(LocalDateTime.now())
        .build();
  }
}
