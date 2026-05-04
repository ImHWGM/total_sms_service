package kr.wisead.domain.message.service;

import static org.assertj.core.api.Assertions.assertThat;

import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.ars.service.BlockedNumberService;
import kr.wisead.domain.event.service.EventParticipantService;
import kr.wisead.domain.payment.service.WalletService;
import kr.wisead.mapper.primary.SmsSendMapper;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import kr.wisead.mapper.primary.SurveyUserMapper;
import kr.wisead.mapper.primary.UserMapper;
import kr.wisead.mapper.sms.MsgQueueMapper;
import kr.wisead.mapper.sms.MsgResultMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 대치문자 / 설문대치문자 헬퍼 단위 테스트.
 *
 * <p>plan AC-3, AC-5, AC-5b 검증 (§3 Phase F-1). 두 헬퍼의 빈 값 시맨틱 차이를 같은 클래스에서 pin (FIX-5).
 */
@ExtendWith(MockitoExtension.class)
class MessageSendServiceReplaceCharsTest {

  @Mock private MsgQueueMapper msgQueueMapper;
  @Mock private MsgResultMapper msgResultMapper;
  @Mock private SurveyUserMapper surveyUserMapper;
  @Mock private SurveyMasterMapper surveyMasterMapper;
  @Mock private SmsSendMapper smsSendMapper;
  @Mock private WalletService walletService;
  @Mock private UserIdResolver userIdResolver;
  @Mock private EventParticipantService eventParticipantService;
  @Mock private BlockedNumberService blockedNumberService;
  @Mock private UserMapper userMapper;

  @InjectMocks private MessageSendService service;

  // ===== applyReplaceChars (기존) — 시맨틱: 빈 값일 때 토큰 잔존 =====

  @Test
  void applyReplaceChars_emptyValue_keepsToken() {
    String result =
        ReflectionTestUtils.invokeMethod(
            service, "applyReplaceChars", "안녕 #대치문자1#님", "", null, null);
    assertThat(result).isEqualTo("안녕 #대치문자1#님");
  }

  @Test
  void applyReplaceChars_value_isReplaced() {
    String result =
        ReflectionTestUtils.invokeMethod(
            service, "applyReplaceChars", "안녕 #대치문자1#님", "홍길동", null, null);
    assertThat(result).isEqualTo("안녕 홍길동님");
  }

  // ===== applySurveyReplaceChars (신규) — 시맨틱: 빈 값일 때 빈 문자열로 치환 (AC-5b) =====

  @Test
  void applySurveyReplaceChars_emptyValue_blanksToken() {
    String result =
        ReflectionTestUtils.invokeMethod(
            service,
            "applySurveyReplaceChars",
            "안내 #설문대치1# 끝",
            null,
            null,
            null,
            null,
            null);
    assertThat(result).isEqualTo("안내  끝");
  }

  @Test
  void applySurveyReplaceChars_allFiveTokensReplaced() {
    String result =
        ReflectionTestUtils.invokeMethod(
            service,
            "applySurveyReplaceChars",
            "[#설문대치1#][#설문대치2#][#설문대치3#][#설문대치4#][#설문대치5#]",
            "A",
            "B",
            "C",
            "D",
            "E");
    assertThat(result).isEqualTo("[A][B][C][D][E]");
  }

  @Test
  void applySurveyReplaceChars_partialValues_blanksMissingTokens() {
    String result =
        ReflectionTestUtils.invokeMethod(
            service,
            "applySurveyReplaceChars",
            "[#설문대치1#][#설문대치2#][#설문대치3#]",
            "값1",
            null,
            "값3",
            null,
            null);
    assertThat(result).isEqualTo("[값1][][값3]");
  }

  @Test
  void applySurveyReplaceChars_nullText_returnsNull() {
    String result =
        ReflectionTestUtils.invokeMethod(
            service, "applySurveyReplaceChars", (Object) null, "A", "B", "C", "D", "E");
    assertThat(result).isNull();
  }

  @Test
  void applySurveyReplaceChars_sameTokenTwice_replacedGlobally() {
    String result =
        ReflectionTestUtils.invokeMethod(
            service,
            "applySurveyReplaceChars",
            "#설문대치1# 그리고 #설문대치1#",
            "X",
            null,
            null,
            null,
            null);
    assertThat(result).isEqualTo("X 그리고 X");
  }

  // ===== 시맨틱 차이 pin: 빈 값일 때 두 헬퍼 동작이 의도적으로 다름 (AC-5b) =====

  @Test
  void semanticDiff_replaceCharsKeepsToken_butSurveyReplaceCharsBlanksIt() {
    String repCharResult =
        ReflectionTestUtils.invokeMethod(
            service, "applyReplaceChars", "[#대치문자1#]", null, null, null);
    String surveyResult =
        ReflectionTestUtils.invokeMethod(
            service, "applySurveyReplaceChars", "[#설문대치1#]", null, null, null, null, null);

    assertThat(repCharResult).isEqualTo("[#대치문자1#]"); // 토큰 잔존
    assertThat(surveyResult).isEqualTo("[]"); // 토큰 사라짐
  }
}
