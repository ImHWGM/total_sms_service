package kr.wisead.domain.message.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * ResendRequest.DuplicateReceiver Jackson 역직렬화 검증.
 *
 * <p>회귀 방지: 중복 번호 재발송/신규 발송 경로(resendToDuplicates, sendNewToDuplicates)가 설문대치문자
 * (surveyRepChar01~05)를 수신하도록 한다. 과거에는 해당 필드가 DTO에 없어 조용히 누락되어 설문 화면에 치환값이 표시되지 않았다.
 */
class ResendRequestSerializationTest {

  private final ObjectMapper objectMapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @Test
  void duplicateReceiver_surveyRepCharFields_roundTrip() throws Exception {
    String json =
        """
        {
          "phone": "01012345678",
          "userSeq": 100,
          "userKey": "key123",
          "repChar01": "홍길동",
          "surveyRepChar01": "테스트",
          "surveyRepChar02": "asdidsaf",
          "surveyRepChar03": "S3",
          "surveyRepChar04": "S4",
          "surveyRepChar05": "S5"
        }
        """;

    ResendRequest.DuplicateReceiver receiver =
        objectMapper.readValue(json, ResendRequest.DuplicateReceiver.class);

    assertThat(receiver.getPhone()).isEqualTo("01012345678");
    assertThat(receiver.getUserSeq()).isEqualTo(100);
    assertThat(receiver.getRepChar01()).isEqualTo("홍길동");
    assertThat(receiver.getSurveyRepChar01()).isEqualTo("테스트");
    assertThat(receiver.getSurveyRepChar02()).isEqualTo("asdidsaf");
    assertThat(receiver.getSurveyRepChar03()).isEqualTo("S3");
    assertThat(receiver.getSurveyRepChar04()).isEqualTo("S4");
    assertThat(receiver.getSurveyRepChar05()).isEqualTo("S5");
  }

  @Test
  void duplicateReceiver_missingSurveyRepCharFields_areNull() throws Exception {
    String json =
        """
        {
          "phone": "01012345678",
          "userSeq": 100
        }
        """;

    ResendRequest.DuplicateReceiver receiver =
        objectMapper.readValue(json, ResendRequest.DuplicateReceiver.class);

    assertThat(receiver.getSurveyRepChar01()).isNull();
    assertThat(receiver.getSurveyRepChar05()).isNull();
  }
}
