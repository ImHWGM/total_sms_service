package kr.wisead.domain.history.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.ars.service.ArsService;
import kr.wisead.domain.history.entity.SendHistory;
import kr.wisead.mapper.sms.SendHistoryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** SendHistoryService.getUnmaskedReceiver 단위 테스트 — seq 원본조회 흐름/권한/404. */
@ExtendWith(MockitoExtension.class)
@DisplayName("발송 이력 원본 수신번호 조회 테스트")
class SendHistoryServiceUnmaskTest {

  private static final Long SEQ = 12345L;

  @Mock private SendHistoryMapper sendHistoryMapper;
  @Mock private ArsService arsService;

  @InjectMocks private SendHistoryService sendHistoryService;

  private SendHistory entityWith(String dstAddr) {
    return SendHistory.builder().msgKey(SEQ).dstAddr(dstAddr).build();
  }

  @Test
  @DisplayName("ym 지정 시 해당 월 단일 테이블만 조회하고 하이픈 포맷 원본을 반환")
  void ymGiven_queriesSingleTable_returnsFormatted() {
    when(sendHistoryMapper.selectBySeq(eq("msg_result_202607"), eq(SEQ), anyString()))
        .thenReturn(entityWith("01012345678"));

    String receiver = sendHistoryService.getUnmaskedReceiver(SEQ, "202607", "userA");

    assertThat(receiver).isEqualTo("010-1234-5678");
    verify(sendHistoryMapper, times(1)).selectBySeq(anyString(), eq(SEQ), anyString());
  }

  @Test
  @DisplayName("권한 범위(queryUserId)가 매퍼로 그대로 전달된다")
  void passesQueryUserIdToMapper() {
    when(sendHistoryMapper.selectBySeq(anyString(), eq(SEQ), eq("userA,userB")))
        .thenReturn(entityWith("01055556666"));

    String receiver = sendHistoryService.getUnmaskedReceiver(SEQ, "202607", "userA,userB");

    assertThat(receiver).isEqualTo("010-5555-6666");
    verify(sendHistoryMapper).selectBySeq(eq("msg_result_202607"), eq(SEQ), eq("userA,userB"));
  }

  @Test
  @DisplayName("권한 범위 밖/미존재 seq 는 RESOURCE_NOT_FOUND(404)")
  void notFound_throwsResourceNotFound() {
    when(sendHistoryMapper.selectBySeq(anyString(), eq(SEQ), anyString())).thenReturn(null);

    assertThatThrownBy(() -> sendHistoryService.getUnmaskedReceiver(SEQ, "202607", "userA"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }

  @Test
  @DisplayName("ym 미지정 시 최근 13개월 테이블을 역순 탐색(전부 미발견이면 404)")
  void ymNull_scansFallbackMonths() {
    when(sendHistoryMapper.selectBySeq(anyString(), eq(SEQ), anyString())).thenReturn(null);

    assertThatThrownBy(() -> sendHistoryService.getUnmaskedReceiver(SEQ, null, "userA"))
        .isInstanceOf(BusinessException.class);

    // 폴백 탐색 폭(UNMASK_FALLBACK_MONTHS = 13) 만큼 조회 시도
    verify(sendHistoryMapper, times(13)).selectBySeq(anyString(), eq(SEQ), anyString());
  }

  @Test
  @DisplayName("폴백 탐색 중 첫 매치에서 즉시 반환(단축 평가)")
  void ymNull_shortCircuitsOnFirstHit() {
    when(sendHistoryMapper.selectBySeq(anyString(), eq(SEQ), anyString()))
        .thenReturn(entityWith("01012345678"));

    String receiver = sendHistoryService.getUnmaskedReceiver(SEQ, null, "userA");

    assertThat(receiver).isEqualTo("010-1234-5678");
    verify(sendHistoryMapper, times(1)).selectBySeq(anyString(), eq(SEQ), anyString());
  }

  @Test
  @DisplayName("형식이 잘못된 ym 은 무시하고 폴백 탐색으로 진행")
  void malformedYm_fallsBackToScan() {
    when(sendHistoryMapper.selectBySeq(anyString(), eq(SEQ), anyString())).thenReturn(null);

    assertThatThrownBy(() -> sendHistoryService.getUnmaskedReceiver(SEQ, "20-07", "userA"))
        .isInstanceOf(BusinessException.class);

    verify(sendHistoryMapper, times(13)).selectBySeq(anyString(), eq(SEQ), any());
  }
}
