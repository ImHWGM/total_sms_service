package kr.wisead.domain.survey.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.event.service.EventAccessValidator;
import kr.wisead.mapper.primary.SurveyUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 설문 참여자 조회 시 이벤트별 소유권(계정) 검증 단위 테스트 — 타 고객 PII IDOR 차단. */
@ExtendWith(MockitoExtension.class)
@DisplayName("설문 참여자 조회 권한 검증 테스트")
class SurveyServiceSecurityTest {

  private static final Integer EVENT_SEQ = 100;
  private static final String OTHER_USER_ID = "other";

  @Mock private SurveyUserMapper surveyUserMapper;
  @Mock private EventAccessValidator eventAccessValidator;

  @InjectMocks private SurveyService surveyService;

  @Test
  @DisplayName("타 고객 이벤트 참여자 목록 조회는 ACCESS_DENIED + 데이터 미조회")
  void getParticipants_otherOwner_denied() {
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "권한 없음"))
        .when(eventAccessValidator)
        .validateEventReadAccess(EVENT_SEQ, OTHER_USER_ID);

    assertThatThrownBy(() -> surveyService.getParticipants(EVENT_SEQ, OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);

    verify(surveyUserMapper, never()).selectCompletedByEventSeq(any());
  }

  @Test
  @DisplayName("타 고객 이벤트 미참여자 목록 조회는 ACCESS_DENIED + 데이터 미조회")
  void getAbsentees_otherOwner_denied() {
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "권한 없음"))
        .when(eventAccessValidator)
        .validateEventReadAccess(EVENT_SEQ, OTHER_USER_ID);

    assertThatThrownBy(() -> surveyService.getAbsenteesAndLurkers(EVENT_SEQ, OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);

    verify(surveyUserMapper, never()).selectAbsenteesAndLurkers(any());
  }
}
