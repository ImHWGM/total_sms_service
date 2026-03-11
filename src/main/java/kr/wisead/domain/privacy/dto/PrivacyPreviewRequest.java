package kr.wisead.domain.privacy.dto;

import lombok.Data;

/** 개인정보제공동의서 미리보기 요청 DTO */
@Data
public class PrivacyPreviewRequest {
  private String title;
  private String content;
  private String language;
  private String thirdPartyYn; // 제3자 제공 동의 사용여부
  private String thirdPartyTtl; // 제3자 제공 동의 타이틀
  private String thirdPartyContent; // 제3자 제공 동의 내용
}
