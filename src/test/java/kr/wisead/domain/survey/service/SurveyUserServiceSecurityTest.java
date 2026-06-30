package kr.wisead.domain.survey.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.event.service.EventAccessValidator;
import kr.wisead.domain.survey.entity.SurveyUser;
import kr.wisead.mapper.primary.SurveyUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** SurveyUserController 대체 조회 경로의 이벤트별 소유권 검증 단위 테스트 — 교차 고객 IDOR 차단. */
@ExtendWith(MockitoExtension.class)
@DisplayName("설문 참여자(관리자) 조회 권한 검증 테스트")
class SurveyUserServiceSecurityTest {

  private static final Integer EVENT_SEQ = 100;
  private static final Integer USER_SEQ = 7;
  private static final String OTHER_USER_ID = "other";

  @Mock private SurveyUserMapper surveyUserMapper;
  @Mock private EventAccessValidator eventAccessValidator;

  @InjectMocks private SurveyUserService surveyUserService;

  @Test
  @DisplayName("타 고객 이벤트 완료자 조회는 ACCESS_DENIED + 데이터 미조회")
  void getCompletedUsers_otherOwner_denied() {
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "권한 없음"))
        .when(eventAccessValidator)
        .validateEventReadAccess(EVENT_SEQ, OTHER_USER_ID);

    assertThatThrownBy(() -> surveyUserService.getCompletedUsers(EVENT_SEQ, OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);

    verify(surveyUserMapper, never()).selectCompletedByEventSeq(any());
  }

  @Test
  @DisplayName("타 고객 이벤트 미참여자 조회는 ACCESS_DENIED + 데이터 미조회")
  void getAbsenteesAndLurkers_otherOwner_denied() {
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "권한 없음"))
        .when(eventAccessValidator)
        .validateEventReadAccess(EVENT_SEQ, OTHER_USER_ID);

    assertThatThrownBy(() -> surveyUserService.getAbsenteesAndLurkers(EVENT_SEQ, OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);

    verify(surveyUserMapper, never()).selectAbsenteesAndLurkers(any());
  }

  @Test
  @DisplayName("참여자 상세 조회는 레코드의 실제 eventSeq로 소유권 검증한다")
  void getUserBySeq_validatesByRecordEventSeq() {
    SurveyUser record = SurveyUser.builder().eventSeq(EVENT_SEQ).build();
    when(surveyUserMapper.selectBySeq(USER_SEQ)).thenReturn(Optional.of(record));
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "권한 없음"))
        .when(eventAccessValidator)
        .validateEventReadAccess(EVENT_SEQ, OTHER_USER_ID);

    assertThatThrownBy(() -> surveyUserService.getUserBySeq(USER_SEQ, OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);
  }

  @Test
  @DisplayName("참여자 수 통계 조회도 소유권 검증을 거친다")
  void getParticipantCounts_otherOwner_denied() {
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "권한 없음"))
        .when(eventAccessValidator)
        .validateEventReadAccess(EVENT_SEQ, OTHER_USER_ID);

    assertThatThrownBy(() -> surveyUserService.getParticipantCounts(EVENT_SEQ, OTHER_USER_ID))
        .isInstanceOf(BusinessException.class);

    verify(surveyUserMapper, never()).countByEventSeq(any());
  }
}
