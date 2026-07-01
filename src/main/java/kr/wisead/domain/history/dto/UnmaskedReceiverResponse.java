package kr.wisead.domain.history.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 발송 이력 원본 수신번호 조회 응답.
 *
 * <p>GET /api/history/send/{seq}/unmasked 의 data 필드. 마스킹되지 않은 원본 번호를 담는다(감사 로그 기록됨).
 */
@Getter
@AllArgsConstructor
public class UnmaskedReceiverResponse {

  private final String receiver; // 원본 수신번호 (010-1234-5678)
}
