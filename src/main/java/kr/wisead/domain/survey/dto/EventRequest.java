package kr.wisead.domain.survey.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

/**
 * 이벤트/설문 생성/수정 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventRequest {

    @NotBlank(message = "이벤트명은 필수입니다.")
    private String eventName;               // 이벤트 명

    private String eventEmphasisYn;         // 이벤트 명 강조 사용여부

    private String eventDesc;               // 이벤트 설명

    @NotBlank(message = "이벤트 타입은 필수입니다.")
    private String eventType;               // 이벤트 타입

    @NotBlank(message = "시작일은 필수입니다.")
    private String startDate;               // 시작일

    @NotBlank(message = "종료일은 필수입니다.")
    private String endDate;                 // 종료일

    private String status;                  // 상태 (A:준비, P:진행, S:중지, F:종료)

    private String privacyPolicyYn;         // 개인정보취합 안내 노출여부

    private String privacyPolicyTtl;        // 개인정보 취합 타이틀

    private String privacyPolicyDesc;       // 개인정보 취합 안내

    @NotBlank(message = "인증 종류는 필수입니다.")
    private String auth;                    // 인증 종류

    private String qrCode;                  // QR코드 사용여부

    private String endMessage;              // 설문 종료 메시지

    // 문항 목록 (설문 생성 시)
    private List<QuestionRequest> questions;
}
