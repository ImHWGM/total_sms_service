package kr.wisead.domain.message.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * SurveyMessageRequest Jackson 역직렬화 검증.
 *
 * <p>plan AC-4, FIX-7 검증: surveyRepChar01~05 round-trip + @JsonIgnoreProperties unknown 필드 무시.
 */
class SurveyMessageRequestSerializationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void receiver_surveyRepCharFields_roundTrip() throws Exception {
    String json =
        """
        {
          "phone": "01012345678",
          "repChar01": "홍길동",
          "surveyRepChar01": "S1",
          "surveyRepChar02": "S2",
          "surveyRepChar03": "S3",
          "surveyRepChar04": "S4",
          "surveyRepChar05": "S5"
        }
        """;

    SurveyMessageRequest.Receiver receiver =
        objectMapper.readValue(json, SurveyMessageRequest.Receiver.class);

    assertThat(receiver.getPhone()).isEqualTo("01012345678");
    assertThat(receiver.getRepChar01()).isEqualTo("홍길동");
    assertThat(receiver.getSurveyRepChar01()).isEqualTo("S1");
    assertThat(receiver.getSurveyRepChar02()).isEqualTo("S2");
    assertThat(receiver.getSurveyRepChar03()).isEqualTo("S3");
    assertThat(receiver.getSurveyRepChar04()).isEqualTo("S4");
    assertThat(receiver.getSurveyRepChar05()).isEqualTo("S5");
  }

  @Test
  void receiver_missingSurveyRepCharFields_areNull() throws Exception {
    String json =
        """
        {
          "phone": "01012345678",
          "repChar01": "홍길동"
        }
        """;

    SurveyMessageRequest.Receiver receiver =
        objectMapper.readValue(json, SurveyMessageRequest.Receiver.class);

    assertThat(receiver.getSurveyRepChar01()).isNull();
    assertThat(receiver.getSurveyRepChar05()).isNull();
  }

  @Test
  void unknownFieldIgnored_onReceiver() throws Exception {
    String json =
        """
        {
          "phone": "01012345678",
          "futureField": "ignored"
        }
        """;

    SurveyMessageRequest.Receiver receiver =
        objectMapper.readValue(json, SurveyMessageRequest.Receiver.class);

    assertThat(receiver.getPhone()).isEqualTo("01012345678");
  }

  @Test
  void unknownFieldIgnored_onSurveyMessageRequest() throws Exception {
    String json =
        """
        {
          "eventSeq": 1,
          "callback": "01012345678",
          "text": "test",
          "futureField": "ignored",
          "receivers": []
        }
        """;

    SurveyMessageRequest request = objectMapper.readValue(json, SurveyMessageRequest.class);

    assertThat(request.getEventSeq()).isEqualTo(1);
    assertThat(request.getText()).isEqualTo("test");
  }
}
