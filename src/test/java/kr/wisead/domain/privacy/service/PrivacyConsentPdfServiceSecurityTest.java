package kr.wisead.domain.privacy.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.event.service.EventAccessValidator;
import kr.wisead.domain.survey.entity.SurveyMaster;
import kr.wisead.domain.survey.entity.SurveyUser;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import kr.wisead.mapper.primary.SurveyUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 개인정보제공동의서 PDF/ZIP 다운로드 경로의 이벤트별 소유권 검증 단위 테스트 — 인증된 교차 고객 IDOR(타 고객 PII 전량 덤프) 차단.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("개인정보제공동의서 다운로드 권한 검증 테스트")
class PrivacyConsentPdfServiceSecurityTest {

  private static final int EVENT_SEQ = 100;
  private static final int OTHER_EVENT_SEQ = 200;
  private static final int USER_SEQ = 7;
  private static final String OTHER_USER_ID = "other";

  @Mock private SurveyMasterMapper surveyMasterMapper;
  @Mock private SurveyUserMapper surveyUserMapper;
  @Mock private EventAccessValidator eventAccessValidator;

  @InjectMocks private PrivacyConsentPdfService privacyConsentPdfService;

  @Test
  @DisplayName("타 고객 이벤트 단건 PDF는 ACCESS_DENIED + 참여자 미조회")
  void generatePrivacyConsentPdf_otherOwner_denied() {
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "권한 없음"))
        .when(eventAccessValidator)
        .validateEventReadAccess(EVENT_SEQ, OTHER_USER_ID);

    assertThatThrownBy(
            () ->
                privacyConsentPdfService.generatePrivacyConsentPdf(
                    USER_SEQ, EVENT_SEQ, true, "ko", OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);

    verify(surveyMasterMapper, never()).selectByEventSeq(anyInt());
    verify(surveyUserMapper, never()).selectBySeq(anyInt());
  }

  @Test
  @DisplayName("타 고객 이벤트 ZIP 전량 다운로드는 ACCESS_DENIED + 참여자 미조회")
  void generatePrivacyConsentPdfZip_otherOwner_denied() {
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "권한 없음"))
        .when(eventAccessValidator)
        .validateEventReadAccess(EVENT_SEQ, OTHER_USER_ID);

    assertThatThrownBy(
            () ->
                privacyConsentPdfService.generatePrivacyConsentPdfZip(
                    EVENT_SEQ, true, "ko", OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);

    verify(surveyUserMapper, never()).selectCompletedByEventSeq(anyInt());
  }

  @Test
  @DisplayName("권한 통과해도 userSeq가 타 이벤트 참여자면 RESOURCE_NOT_FOUND (이벤트 치환 차단)")
  void generatePrivacyConsentPdf_userSeqFromOtherEvent_notFound() {
    SurveyMaster event =
        SurveyMaster.builder().eventSeq(EVENT_SEQ).privacyPolicyYn("Y").build();
    SurveyUser foreignUser = SurveyUser.builder().seq(USER_SEQ).eventSeq(OTHER_EVENT_SEQ).build();
    when(surveyMasterMapper.selectByEventSeq(EVENT_SEQ)).thenReturn(Optional.of(event));
    when(surveyUserMapper.selectBySeq(USER_SEQ)).thenReturn(Optional.of(foreignUser));

    assertThatThrownBy(
            () ->
                privacyConsentPdfService.generatePrivacyConsentPdf(
                    USER_SEQ, EVENT_SEQ, true, "ko", OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

    verify(eventAccessValidator).validateEventReadAccess(EVENT_SEQ, OTHER_USER_ID);
  }
}
