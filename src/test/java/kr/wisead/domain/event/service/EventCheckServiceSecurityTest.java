package kr.wisead.domain.event.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.ratelimit.FailureRateLimiter;
import kr.wisead.common.ratelimit.RateLimitExceededException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.event.entity.EventActionLog;
import kr.wisead.mapper.primary.EventActionLogMapper;
import kr.wisead.mapper.primary.EventActionTypeMapper;
import kr.wisead.mapper.primary.EventNametagLogMapper;
import kr.wisead.mapper.primary.EventParticipantMapper;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import kr.wisead.mapper.primary.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class EventCheckServiceSecurityTest {

  private static final Integer EVENT_SEQ = 100;
  private static final String CHECK_CODE = "NOPE1";
  private static final String CLIENT_IP = "127.0.0.1";

  @Mock private EventParticipantMapper participantMapper;
  @Mock private EventActionTypeMapper actionTypeMapper;
  @Mock private EventActionLogMapper actionLogMapper;
  @Mock private EventNametagLogMapper nametagLogMapper;
  @Mock private UserMapper userMapper;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private SurveyMasterMapper surveyMasterMapper;
  @Spy private FailureRateLimiter failureRateLimiter = new FailureRateLimiter();

  private EventCheckService service;

  @BeforeEach
  void setUp() {
    service =
        new EventCheckService(
            participantMapper,
            actionTypeMapper,
            actionLogMapper,
            nametagLogMapper,
            userMapper,
            passwordEncoder,
            surveyMasterMapper,
            failureRateLimiter);
  }

  @Test
  @DisplayName("공개 check POST 미발견 실패는 IP+eventSeq check 버킷으로 10회 초과 시 차단한다")
  void publicCheckIn_rateLimitsBruteForceOnNotFound() {
    when(participantMapper.selectDetailByEventSeqAndCheckCode(EVENT_SEQ, CHECK_CODE))
        .thenReturn(Optional.empty());

    for (int i = 0; i < 10; i++) {
      assertThatThrownBy(() -> service.checkIn(EVENT_SEQ, CHECK_CODE, null, CLIENT_IP))
          .isInstanceOf(BusinessException.class)
          .extracting("errorCode")
          .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    assertThatThrownBy(() -> service.checkIn(EVENT_SEQ, CHECK_CODE, null, CLIENT_IP))
        .isInstanceOf(RateLimitExceededException.class);

    verify(actionLogMapper, never()).insert(org.mockito.ArgumentMatchers.any(EventActionLog.class));
  }
}
