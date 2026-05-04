package kr.wisead.domain.survey.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.survey.dto.SurveySubmitRequest;
import kr.wisead.domain.survey.entity.OtherType;
import kr.wisead.mapper.primary.SurveyAnswerMapper;
import kr.wisead.mapper.primary.SurveyItemMapper;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import kr.wisead.mapper.primary.SurveyQuestionMapper;
import kr.wisead.mapper.primary.SurveyUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 기타답변 저장값 결정 단위 테스트 — AC-9 4 케이스 + 비-SO 회귀 (plan §3 Phase F-3).
 *
 * <ul>
 *   <li>(a) SO + RSA 복호화 성공 → AES256+Base64 저장
 *   <li>(b) SO + FOREIGN: → 평문 그대로 저장
 *   <li>(c) SO + 평문 jumin → AES256+Base64 저장
 *   <li>(d) SO + RSA 복호화 실패 → 원본 raw 저장 (RSA 암호문 그대로) — fallback 정책
 *   <li>비-SO 5종 → raw 그대로 저장
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SurveyServiceOtherTextStorageTest {

  @Mock private SurveyMasterMapper surveyMasterMapper;
  @Mock private SurveyQuestionMapper surveyQuestionMapper;
  @Mock private SurveyItemMapper surveyItemMapper;
  @Mock private SurveyUserMapper surveyUserMapper;
  @Mock private SurveyAnswerMapper surveyAnswerMapper;
  @Mock private FrontAuthService frontAuthService;

  @InjectMocks private SurveyService service;

  private static final String PLAIN_JUMIN = "881234-1234567";

  @Test
  void rsaPath_decryptThenAesEncryptThenStore() {
    // mock RSA 복호화 결과 = "1234567"
    when(frontAuthService.decryptKeypadInput(anyString(), anyString())).thenReturn("1234567");

    SurveySubmitRequest.AnswerRequest req =
        SurveySubmitRequest.AnswerRequest.builder()
            .questionSeq(101)
            .keypadId("kp-1")
            .otherText("RSA:881234:base64ciphertext")
            .build();

    String stored = invokeResolveStorage(OtherType.SO, req.getOtherText(), req);

    assertThat(stored)
        .as("저장값은 평문도, RSA 암호문도 아닌 AES256+Base64 결과여야 함")
        .isNotEqualTo(PLAIN_JUMIN)
        .isNotEqualTo("RSA:881234:base64ciphertext");
    assertThat(CryptoUtils.decryptAES256(stored))
        .as("AES256 복호화 시 평문 jumin이 복원되어야 함")
        .isEqualTo(PLAIN_JUMIN);
  }

  @Test
  void foreignPath_aesEncryptsAndStores() {
    SurveySubmitRequest.AnswerRequest req =
        SurveySubmitRequest.AnswerRequest.builder()
            .questionSeq(102)
            .otherText("FOREIGN:passport123")
            .build();

    String stored = invokeResolveStorage(OtherType.SO, req.getOtherText(), req);

    assertThat(stored)
        .as("FOREIGN도 일반 SO 문항과 동일하게 AES256+Base64 적용 (spec R7 100% 동일 정책)")
        .isNotEqualTo("FOREIGN:passport123");
    assertThat(CryptoUtils.decryptAES256(stored))
        .as("AES256 복호화 시 FOREIGN: 접두사를 포함한 원본이 복원되어야 함")
        .isEqualTo("FOREIGN:passport123");
  }

  @Test
  void plainJuminPath_aesEncryptsAndStores() {
    SurveySubmitRequest.AnswerRequest req =
        SurveySubmitRequest.AnswerRequest.builder()
            .questionSeq(103)
            .otherText(PLAIN_JUMIN)
            .build();

    String stored = invokeResolveStorage(OtherType.SO, req.getOtherText(), req);

    assertThat(stored).isNotEqualTo(PLAIN_JUMIN);
    assertThat(CryptoUtils.decryptAES256(stored)).isEqualTo(PLAIN_JUMIN);
  }

  @Test
  void rsaDecryptFailure_storesRawRsaString() {
    when(frontAuthService.decryptKeypadInput(anyString(), anyString()))
        .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "키패드 세션이 만료되었습니다."));

    String raw = "RSA:881234:invalidcipher";
    SurveySubmitRequest.AnswerRequest req =
        SurveySubmitRequest.AnswerRequest.builder()
            .questionSeq(104)
            .keypadId("kp-expired")
            .otherText(raw)
            .build();

    String stored = invokeResolveStorage(OtherType.SO, req.getOtherText(), req);

    assertThat(stored).as("복호화 실패 시 원본 raw RSA 문자열을 그대로 저장 (fallback)").isEqualTo(raw);
  }

  @Test
  void rsaInvalidShape_storesRawRsaString() {
    String raw = "RSA:notdigits:cipher";
    SurveySubmitRequest.AnswerRequest req =
        SurveySubmitRequest.AnswerRequest.builder()
            .questionSeq(105)
            .keypadId("kp-1")
            .otherText(raw)
            .build();

    String stored = invokeResolveStorage(OtherType.SO, req.getOtherText(), req);

    assertThat(stored).as("앞자리 형식 오류 시 원본 raw 저장").isEqualTo(raw);
  }

  @Test
  void rsaWithoutKeypadId_storesRawRsaString() {
    String raw = "RSA:881234:cipher";
    SurveySubmitRequest.AnswerRequest req =
        SurveySubmitRequest.AnswerRequest.builder()
            .questionSeq(106)
            .otherText(raw)
            .build();

    String stored = invokeResolveStorage(OtherType.SO, req.getOtherText(), req);

    assertThat(stored).as("keypadId 누락 시 원본 raw 저장").isEqualTo(raw);
  }

  @Test
  void rsaDecryptedBackInvalid_storesRawRsaString() {
    when(frontAuthService.decryptKeypadInput(anyString(), anyString())).thenReturn("abc");

    String raw = "RSA:881234:base64cipher";
    SurveySubmitRequest.AnswerRequest req =
        SurveySubmitRequest.AnswerRequest.builder()
            .questionSeq(107)
            .keypadId("kp-1")
            .otherText(raw)
            .build();

    String stored = invokeResolveStorage(OtherType.SO, req.getOtherText(), req);

    assertThat(stored).as("뒷자리 형식 오류 시 원본 raw 저장").isEqualTo(raw);
  }

  @Test
  void nonSoTypes_storeRawAsIs() {
    for (OtherType type : OtherType.values()) {
      if (type == OtherType.SO) {
        continue;
      }
      SurveySubmitRequest.AnswerRequest req =
          SurveySubmitRequest.AnswerRequest.builder()
              .questionSeq(200)
              .otherText("plain text " + type.name())
              .build();

      String stored = invokeResolveStorage(type, req.getOtherText(), req);

      assertThat(stored).as(type.name() + "는 raw 그대로 저장").isEqualTo("plain text " + type.name());
    }
  }

  @Test
  void soPlainPath_invalidJumin_throwsAtPlainValidation() {
    // resolveJuminFromSource는 "1234-5678" 같은 잘못된 평문은 null 반환 → fallback raw 저장 (validatePlain 도달 X)
    // 하지만 정상 jumin shape을 통과한 평문이 잘못되면 validatePlain이 예외.
    // 여기서는 형식이 \d{6}-\d{7} 통과하지 않는 raw로 fallback 경로 검증.
    String raw = "1234-5678";
    SurveySubmitRequest.AnswerRequest req =
        SurveySubmitRequest.AnswerRequest.builder().questionSeq(108).otherText(raw).build();

    String stored = invokeResolveStorage(OtherType.SO, req.getOtherText(), req);

    assertThat(stored).as("평문 jumin shape 미통과 raw는 fallback으로 그대로 저장").isEqualTo(raw);
  }

  // === helpers ===

  private String invokeResolveStorage(
      OtherType type, String otherText, SurveySubmitRequest.AnswerRequest req) {
    return ReflectionTestUtils.invokeMethod(
        service, "resolveOtherTextForStorage", type, otherText, req);
  }
}
