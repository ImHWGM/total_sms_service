package kr.wisead.domain.history.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.wisead.domain.history.entity.SendHistory;
import org.junit.jupiter.api.Test;

/**
 * SendHistoryResponse Jackson 직렬화 검증.
 *
 * <p>리스트 조회 JSON 응답(GET /api/history/send)에서 원본 수신번호(rawReceiver)가 노출되지 않고,
 * receiver 는 마스킹되어 내려가는지 회귀 검증한다. 원본 번호는 감사 로그가 남는 별도 unmask API로만
 * 제공한다. 엑셀 다운로드는 getter(getRawReceiver)를 직접 참조하므로 @JsonIgnore 와 무관하게 유지된다.
 */
class SendHistoryResponseSerializationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private SendHistoryResponse sampleResponse() {
    SendHistory entity = SendHistory.builder().msgKey(1L).msgType("SMS").dstAddr("01012345678").build();
    return SendHistoryResponse.from(entity);
  }

  @Test
  void rawReceiver_isNotSerializedIntoJson() throws Exception {
    JsonNode json = objectMapper.valueToTree(sampleResponse());

    assertThat(json.has("rawReceiver")).isFalse();
  }

  @Test
  void receiver_isSerializedMasked() throws Exception {
    JsonNode json = objectMapper.valueToTree(sampleResponse());

    assertThat(json.has("receiver")).isTrue();
    assertThat(json.get("receiver").asText()).isEqualTo("010-****-5678");
  }

  @Test
  void serializedJson_neverContainsRawNumber() throws Exception {
    String json = objectMapper.writeValueAsString(sampleResponse());

    // 원본 번호(하이픈 포함/미포함) 어떤 형태로도 평문 노출되지 않아야 한다.
    assertThat(json).doesNotContain("01012345678").doesNotContain("010-1234-5678");
  }

  @Test
  void rawReceiver_getterStillReturnsOriginal_forExcelDownload() {
    // 엑셀 다운로드 경로는 Jackson 이 아니라 getter 직접 호출이므로 원본 값이 살아있어야 한다.
    assertThat(sampleResponse().getRawReceiver()).isEqualTo("010-1234-5678");
  }
}
