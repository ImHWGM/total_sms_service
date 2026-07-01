package kr.wisead.domain.privacy.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.event.service.EventAccessValidator;
import kr.wisead.domain.privacy.service.PrivacyConsentPdfService;
import kr.wisead.domain.survey.entity.SurveyUser;
import kr.wisead.mapper.primary.SurveyUserMapper;
import kr.wisead.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 개인정보제공동의서 다운로드 권한 검증 단위 테스트 — 타 고객 event/user PII IDOR 차단. */
@ExtendWith(MockitoExtension.class)
@DisplayName("개인정보제공동의서 다운로드 권한 검증 테스트")
class PrivacyConsentControllerSecurityTest {

  private static final int EVENT_SEQ = 100;
  private static final int USER_SEQ = 1;
  private static final int OTHER_EVENT_SEQ = 999;
  private static final String TOKEN = "Bearer token";
  private static final String ATTACKER_ID = "attacker";

  @Mock private PrivacyConsentPdfService privacyConsentPdfService;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private UserIdResolver userIdResolver;
  @Mock private EventAccessValidator eventAccessValidator;
  @Mock private SurveyUserMapper surveyUserMapper;

  @InjectMocks private PrivacyConsentController controller;

  private void stubCaller() {
    when(jwtTokenProvider.getUserId(anyString())).thenReturn("subject");
    when(userIdResolver.resolveUserId(any())).thenReturn(ATTACKER_ID);
  }

  @Test
  @DisplayName("타 고객 이벤트 ZIP 다운로드는 ACCESS_DENIED + PDF 미생성")
  void downloadEventZip_otherOwner_denied() throws Exception {
    stubCaller();
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "권한 없음"))
        .when(eventAccessValidator)
        .validateEventReadAccess(EVENT_SEQ, ATTACKER_ID);

    assertThatThrownBy(
            () -> controller.downloadEventPrivacyConsentZip(EVENT_SEQ, true, "ko", TOKEN))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);

    verify(privacyConsentPdfService, never())
        .generatePrivacyConsentPdfZip(anyInt(), anyBoolean(), any(), any());
  }

  @Test
  @DisplayName("타 고객 이벤트 단건 다운로드는 ACCESS_DENIED + PDF 미생성")
  void downloadUser_otherOwner_denied() throws Exception {
    stubCaller();
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "권한 없음"))
        .when(eventAccessValidator)
        .validateEventReadAccess(EVENT_SEQ, ATTACKER_ID);

    assertThatThrownBy(
            () ->
                controller.downloadUserPrivacyConsent(USER_SEQ, EVENT_SEQ, true, "ko", TOKEN))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);

    verify(privacyConsentPdfService, never())
        .generatePrivacyConsentPdf(anyInt(), anyInt(), anyBoolean(), any(), any());
  }

  @Test
  @DisplayName("소유 이벤트라도 userSeq가 타 이벤트 소속이면 RESOURCE_NOT_FOUND + PDF 미생성")
  void downloadUser_userSeqFromOtherEvent_denied() throws Exception {
    stubCaller();
    // 이벤트 read 권한은 통과(void 기본 동작), 단 userSeq는 다른 이벤트 소속.
    when(surveyUserMapper.selectBySeq(USER_SEQ))
        .thenReturn(Optional.of(SurveyUser.builder().seq(USER_SEQ).eventSeq(OTHER_EVENT_SEQ).build()));

    assertThatThrownBy(
            () ->
                controller.downloadUserPrivacyConsent(USER_SEQ, EVENT_SEQ, true, "ko", TOKEN))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

    verify(eventAccessValidator).validateEventReadAccess(eq(EVENT_SEQ), eq(ATTACKER_ID));
    verify(privacyConsentPdfService, never())
        .generatePrivacyConsentPdf(anyInt(), anyInt(), anyBoolean(), any(), any());
  }
}
