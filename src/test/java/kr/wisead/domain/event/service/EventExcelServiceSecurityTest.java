package kr.wisead.domain.event.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.excel.service.ExcelService;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventExcelServiceSecurityTest {

  private static final Integer EVENT_SEQ = 100;
  private static final String OTHER_USER_ID = "other";

  @Mock private EventParticipantService participantService;
  @Mock private SurveyMasterMapper surveyMasterMapper;
  @Mock private ExcelService excelService;

  @InjectMocks private EventExcelService service;

  @Test
  @DisplayName("참가자 엑셀 생성은 소유권 검증 실패 시 중단된다")
  void createParticipantExcel_deniesNonOwner() {
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "접근 권한이 없습니다."))
        .when(participantService)
        .validateEventReadAccess(EVENT_SEQ, OTHER_USER_ID);

    assertThatThrownBy(() -> service.createParticipantExcel(EVENT_SEQ, OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);
    verify(surveyMasterMapper, never()).selectByEventSeq(EVENT_SEQ);
  }

  @Test
  @DisplayName("통계 엑셀 생성은 소유권 검증 실패 시 중단된다")
  void createStatisticsExcel_deniesNonOwner() {
    doThrow(new BusinessException(ErrorCode.ACCESS_DENIED, "접근 권한이 없습니다."))
        .when(participantService)
        .validateEventReadAccess(EVENT_SEQ, OTHER_USER_ID);

    assertThatThrownBy(() -> service.createStatisticsExcel(EVENT_SEQ, OTHER_USER_ID))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.ACCESS_DENIED);
    verify(surveyMasterMapper, never()).selectByEventSeq(EVENT_SEQ);
  }
}
