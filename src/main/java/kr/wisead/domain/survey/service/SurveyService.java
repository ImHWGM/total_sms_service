package kr.wisead.domain.survey.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CommonUtils;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.survey.dto.*;
import kr.wisead.domain.survey.entity.*;
import kr.wisead.mapper.primary.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 설문 참여 Service (사용자용) */
@Slf4j
@Service
@RequiredArgsConstructor
public class SurveyService {

  private final SurveyMasterMapper surveyMasterMapper;
  private final SurveyQuestionMapper surveyQuestionMapper;
  private final SurveyItemMapper surveyItemMapper;
  private final SurveyUserMapper surveyUserMapper;
  private final SurveyAnswerMapper surveyAnswerMapper;
  private final FrontAuthService frontAuthService;

  @Value("${api.base.url:}")
  private String apiBaseUrl;

  /** 이벤트 코드로 설문 정보 조회 */
  @Transactional(readOnly = true)
  public EventResponse getSurveyByEventCode(String eventCode) {
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventCode(eventCode)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "설문을 찾을 수 없습니다."));

    validateEventActive(event);

    return buildSurveyResponse(event);
  }

  /** QR코드 URL로 설문 정보 조회 (방문 수 증가는 FrontAuthService.createQrUser에서 QR_VISIT_LOG에 기록) */
  @Transactional(readOnly = true)
  public EventResponse getSurveyByAuthCodeUrl(String authCodeUrl) {
    SurveyMaster event =
        surveyMasterMapper
            .selectByAuthCodeUrl(authCodeUrl)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "설문을 찾을 수 없습니다."));

    validateEventActive(event);

    return buildSurveyResponse(event);
  }

  /** 사용자 키로 설문 정보 조회 */
  @Transactional
  public EventResponse getSurveyByUserKey(String userKey) {
    SurveyUser user =
        surveyUserMapper
            .selectByUserKey(userKey)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "유효하지 않은 접근입니다."));

    if (user.isSubmitted()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 설문에 참여하셨습니다.");
    }

    SurveyMaster event =
        surveyMasterMapper
            .selectByEventSeq(user.getEventSeq())
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "설문을 찾을 수 없습니다."));

    validateEventActive(event);

    // 설문 접속 시간 기록
    surveyUserMapper.updateStartTime(userKey);

    return buildSurveyResponse(event);
  }

  /** 범용인증 확인 */
  @Transactional
  public SurveyUserResponse checkGeneralAuth(String authCodeUrl, String generalAuthCode) {
    Map<String, Object> authResult =
        surveyUserMapper.checkGeneralAuthStatus(authCodeUrl, generalAuthCode);

    if (authResult == null || authResult.isEmpty()) {
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "유효하지 않은 인증코드입니다.");
    }

    String authStatus = (String) authResult.get("authStatus");
    switch (authStatus) {
      case "NOT_FOUND":
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "유효하지 않은 인증코드입니다.");
      case "ALREADY_ANSWERED":
        throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 설문에 참여하셨습니다.");
      case "TIME_RESTRICTED":
        throw new BusinessException(ErrorCode.INVALID_INPUT, "잠시 후 다시 시도해주세요.");
      case "VALID":
        // 인증 시간 기록
        String userKey = (String) authResult.get("userKey");
        surveyUserMapper.updateAuthTime(userKey);
        surveyUserMapper.updateStartTime(userKey);

        SurveyUser user =
            surveyUserMapper
                .selectByGeneralAuthCode(authCodeUrl, generalAuthCode)
                .orElseThrow(
                    () ->
                        new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자 정보를 찾을 수 없습니다."));

        return SurveyUserResponse.from(user);
      default:
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "인증 처리 중 오류가 발생했습니다.");
    }
  }

  /** 범용인증 확인 (eventCode 기반) */
  @Transactional
  public SurveyUserResponse checkGeneralAuthByEventCode(String eventCode, String generalAuthCode) {
    Map<String, Object> authResult =
        surveyUserMapper.checkGeneralAuthStatusByEventCode(eventCode, generalAuthCode);

    if (authResult == null || authResult.isEmpty()) {
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "유효하지 않은 인증코드입니다.");
    }

    String authStatus = (String) authResult.get("authStatus");
    switch (authStatus) {
      case "NOT_FOUND":
        throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "유효하지 않은 인증코드입니다.");
      case "ALREADY_ANSWERED":
        throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 설문에 참여하셨습니다.");
      case "TIME_RESTRICTED":
        throw new BusinessException(ErrorCode.INVALID_INPUT, "잠시 후 다시 시도해주세요.");
      case "VALID":
        String userKey = (String) authResult.get("userKey");
        surveyUserMapper.updateAuthTime(userKey);
        surveyUserMapper.updateStartTime(userKey);

        SurveyUser user =
            surveyUserMapper
                .selectByGeneralAuthCodeAndEventCode(eventCode, generalAuthCode)
                .orElseThrow(
                    () ->
                        new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "사용자 정보를 찾을 수 없습니다."));

        return SurveyUserResponse.from(user);
      default:
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "인증 처리 중 오류가 발생했습니다.");
    }
  }

  /** 설문 제출 */
  @Transactional
  public void submitSurvey(Integer eventSeq, SurveySubmitRequest request) {
    // 동일 사용자 동시 제출 직렬화: SURVEY_USER 행을 FOR UPDATE로 잠금
    // (USER_KEY UNIQUE 인덱스 기반 행 락 → 다른 사용자에는 영향 없음)
    SurveyUser user =
        surveyUserMapper
            .selectByEventSeqAndUserKeyForUpdate(eventSeq, request.getUserKey())
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "유효하지 않은 접근입니다."));

    if (user.isSubmitted()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 설문에 참여하셨습니다.");
    }

    SurveyMaster event =
        surveyMasterMapper
            .selectByEventSeq(eventSeq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "설문을 찾을 수 없습니다."));

    validateEventActive(event);

    // 기존 답변 삭제 (재제출 대비)
    surveyAnswerMapper.deleteByUserSeq(eventSeq, user.getSeq());

    String submitJuminNum = request.getJuminNum();

    // 답변 저장
    if (request.getAnswers() != null) {
      // 기타 항목 판별을 위해 이벤트 전체 항목을 사전 조회 (N+1 방지)
      Map<Integer, List<SurveyItem>> itemsByQuestion =
          surveyItemMapper.selectByEventSeq(eventSeq).stream()
              .collect(Collectors.groupingBy(SurveyItem::getQuestionSeq));

      // 필수 응답 검증 — 이벤트의 필수 문항 중 미응답이 있으면 거부
      List<SurveyQuestion> questions = surveyQuestionMapper.selectByEventSeq(eventSeq);
      java.util.Set<Integer> answeredQuestionSeqs =
          request.getAnswers().stream()
              .filter(a -> !isEmptyAnswer(a))
              .map(SurveySubmitRequest.AnswerRequest::getQuestionSeq)
              .collect(Collectors.toSet());
      for (SurveyQuestion q : questions) {
        if (q.isRequired() && !answeredQuestionSeqs.contains(q.getQuestionSeq())) {
          throw new BusinessException(ErrorCode.INVALID_INPUT, "필수 응답 문항에 답변하지 않았습니다.");
        }
      }

      for (SurveySubmitRequest.AnswerRequest answerReq : request.getAnswers()) {
        // 빈 답변(미응답) skip — 선택 문항은 미응답 허용
        if (isEmptyAnswer(answerReq)) {
          continue;
        }

        SurveyAnswer answer;
        String answerValue = answerReq.getAnswer();

        if ("SO".equals(answerReq.getQuestionTypeDetail())) {
          String resolvedJuminNum = resolveSoAnswer(answerReq);
          if (!CommonUtils.isNullOrEmpty(resolvedJuminNum)) {
            submitJuminNum = resolvedJuminNum;
            answerValue = encryptSensitiveValue(resolvedJuminNum);
          } else if (answerValue != null && answerValue.startsWith("RSA:")) {
            log.warn(
                "SO RSA 답변 원문 저장 - 복호화 실패 (eventSeq: {}, userKey: {}, questionSeq: {})",
                eventSeq,
                request.getUserKey(),
                answerReq.getQuestionSeq());
          }
        }

        Integer itemSeq = answerReq.getItemSeq();

        if ("FE".equals(answerReq.getQuestionTypeDetail())) {
          // 파일 업로드
          answer =
              SurveyAnswer.createFileUpload(
                  eventSeq,
                  answerReq.getQuestionSeq(),
                  user.getSeq(),
                  itemSeq,
                  answerReq.getFilePath());
        } else if ("MC".equals(answerReq.getQuestionType())) {
          // 객관식
          answer =
              SurveyAnswer.createMultipleChoice(
                  eventSeq,
                  answerReq.getQuestionSeq(),
                  user.getSeq(),
                  itemSeq,
                  answerReq.getQuestionType(),
                  answerReq.getQuestionTypeDetail(),
                  answerValue);

          // 기타 항목 처리: otherText 유효성 검증 및 저장
          List<SurveyItem> questionItems =
              itemsByQuestion.getOrDefault(answerReq.getQuestionSeq(), List.of());
          boolean hasOtherSelected = false;
          SurveyItem selectedItem = null;

          // MCS: itemSeq로 직접 기타 항목 확인
          if (itemSeq != null) {
            final Integer selectedItemSeq = itemSeq;
            selectedItem =
                questionItems.stream()
                    .filter(i -> i.getItemSeq().equals(selectedItemSeq))
                    .findFirst()
                    .orElse(null);
            if (selectedItem != null && selectedItem.isOther()) {
              hasOtherSelected = true;
            }
          }

          // MCM: 문항에 기타 항목이 있고 answer에 해당 itemValue가 포함되어 있는지 확인
          if (!hasOtherSelected && "MCM".equals(answerReq.getQuestionTypeDetail())) {
            hasOtherSelected = questionItems.stream().anyMatch(SurveyItem::isOther);
          }

          if (hasOtherSelected) {
            OtherType otherType = resolveOtherType(selectedItem, questionItems);
            String otherText = answerReq.getOtherText();
            OtherTypeValidator.validateRawOtherText(otherType, otherText);
            answer.setOtherText(resolveOtherTextForStorage(otherType, otherText, answerReq));
          }
        } else {
          // 주관식
          answer =
              SurveyAnswer.createShortAnswer(
                  eventSeq,
                  answerReq.getQuestionSeq(),
                  user.getSeq(),
                  itemSeq,
                  answerReq.getQuestionTypeDetail(),
                  answerValue);
        }

        surveyAnswerMapper.insert(answer);
      }
    }

    // 사용자 정보 업데이트 및 제출 처리
    user.submit(
        request.getUserName(),
        submitJuminNum,
        request.getUserPhone(),
        request.getUserEmail(),
        request.getAddress(),
        request.getAddress2(),
        request.getUserKey());
    surveyUserMapper.updateSubmission(user);

    log.info("설문 제출 완료 - eventSeq: {}, userKey: {}", eventSeq, request.getUserKey());
  }

  /** 빈 답변(미응답) 판정 — itemSeq/answer/otherText/filePath 가 모두 비어있으면 미응답 */
  private boolean isEmptyAnswer(SurveySubmitRequest.AnswerRequest a) {
    return a.getItemSeq() == null
        && CommonUtils.isNullOrEmpty(a.getAnswer())
        && CommonUtils.isNullOrEmpty(a.getOtherText())
        && CommonUtils.isNullOrEmpty(a.getFilePath());
  }

  /**
   * 기타 항목의 OtherType 결정 — MCS는 selectedItem의 otherType, MCM은 문항의 첫 isOther 항목의 otherType. 한 문항당 기타
   * 항목은 최대 1개 (spec R5)이므로 MCM도 단일 결정.
   */
  private OtherType resolveOtherType(SurveyItem selectedItem, List<SurveyItem> questionItems) {
    if (selectedItem != null && selectedItem.isOther() && selectedItem.getOtherType() != null) {
      return selectedItem.getOtherType();
    }
    return questionItems.stream()
        .filter(SurveyItem::isOther)
        .findFirst()
        .map(SurveyItem::getOtherType)
        .filter(Objects::nonNull)
        .orElse(OtherType.SA);
  }

  /**
   * 기타답변 OTHER_TEXT 저장값 결정 — SO 유형은 일반 SO 문항과 동일한 흐름(RSA 복호화 → AES256+Base64 재암호화)을 적용하고, 그 외 5종은
   * raw 그대로 저장한다 (plan §3 Phase D-3-b, AC-9, spec R7 "기존 SO 흐름 100% 동일").
   *
   * <ul>
   *   <li>SO + RSA 복호화 성공: 평문 jumin → validatePlain → AES256+Base64 저장
   *   <li>SO + FOREIGN: AES256+Base64 저장 (일반 SO 문항 흐름과 동일하게 외국인 등록번호도 암호화)
   *   <li>SO + 평문 jumin 직접 입력: validatePlain → AES256+Base64 저장
   *   <li>SO + RSA 복호화 실패 / keypadId 만료 / 형식 오류: 원본 raw 저장 + warn 로그 — fallback 정책 미러
   *   <li>비-SO (SA/NE/EM/AD/CU): raw 그대로 저장
   * </ul>
   */
  private String resolveOtherTextForStorage(
      OtherType otherType, String otherText, SurveySubmitRequest.AnswerRequest answerReq) {
    if (otherType != OtherType.SO) {
      return otherText;
    }
    String plain =
        resolveJuminFromSource(otherText, answerReq.getKeypadId(), answerReq.getQuestionSeq());
    if (plain == null) {
      log.warn("SO 기타답변 RSA 복호화 실패 - 원본 raw 저장 (questionSeq: {})", answerReq.getQuestionSeq());
      return otherText;
    }
    // FOREIGN과 평문 jumin 모두 AES256+Base64 적용 (일반 SO 문항 L204-207 흐름 미러).
    // FOREIGN은 jumin 패턴이 아니므로 validatePlain 호출 시 거부되어 skip한다.
    if (!plain.startsWith("FOREIGN:")) {
      OtherTypeValidator.validatePlainOtherText(OtherType.SO, plain);
    }
    return encryptSensitiveValue(plain);
  }

  /** SO 답변을 평문 주민번호로 정규화 (RSA/FOREIGN/평문 지원). */
  private String resolveSoAnswer(SurveySubmitRequest.AnswerRequest answerReq) {
    return resolveJuminFromSource(
        answerReq.getAnswer(), answerReq.getKeypadId(), answerReq.getQuestionSeq());
  }

  /**
   * 주민번호 source string을 평문 주민번호로 정규화 — 일반 SO 문항과 SO 유형 기타답변의 단일 source-of-truth (plan §3 Phase
   * D-3-a).
   *
   * <ul>
   *   <li>{@code "FOREIGN:..."} → 그대로 반환 (평문 저장 정책, 외국인 등록번호)
   *   <li>{@code "RSA:front:backCipher"} → 3-part split → front 6자리 검증 → back을 decryptKeypadInput으로
   *       RSA 복호화 → "front-back7자리" 평문 jumin 결합 반환
   *   <li>평문 jumin {@code \d{6}-\d{7}} → 그대로 반환
   *   <li>형식 오류 / RSA 복호화 실패 / keypadId 만료 등 → {@code null} 반환 (caller가 fallback 정책 적용)
   * </ul>
   */
  private String resolveJuminFromSource(
      String rawValue, String keypadId, Integer questionSeqForLog) {
    if (CommonUtils.isNullOrEmpty(rawValue)) {
      return null;
    }

    if (rawValue.startsWith("FOREIGN:")) {
      return rawValue;
    }

    if (!rawValue.startsWith("RSA:")) {
      String normalized = rawValue.trim();
      return normalized.matches("\\d{6}-\\d{7}") ? normalized : null;
    }

    if (CommonUtils.isNullOrEmpty(keypadId)) {
      log.warn("SO RSA 복호화 스킵 - keypadId 누락 (questionSeq: {})", questionSeqForLog);
      return null;
    }

    String[] parts = rawValue.split(":", 3);
    if (parts.length < 3) {
      log.warn("SO RSA 복호화 스킵 - 형식 오류 (questionSeq: {})", questionSeqForLog);
      return null;
    }

    String front = parts[1].replaceAll("\\D+", "");
    if (!front.matches("\\d{6}")) {
      log.warn("SO RSA 복호화 스킵 - 앞자리 형식 오류 (questionSeq: {})", questionSeqForLog);
      return null;
    }

    try {
      String decryptedBack = frontAuthService.decryptKeypadInput(keypadId, parts[2]);
      String back = decryptedBack != null ? decryptedBack.replaceAll("\\D+", "") : "";
      if (!back.matches("\\d{7}")) {
        log.warn("SO RSA 복호화 스킵 - 뒷자리 형식 오류 (questionSeq: {})", questionSeqForLog);
        return null;
      }
      return front + "-" + back;
    } catch (BusinessException e) {
      log.warn("SO RSA 복호화 실패 (questionSeq: {}, reason: {})", questionSeqForLog, e.getMessage());
      return null;
    }
  }

  /** 개인정보 답변 저장용 AES256 + Base64(2중) 암호화 */
  private String encryptSensitiveValue(String plainText) {
    if (CommonUtils.isNullOrEmpty(plainText)) {
      return plainText;
    }
    String encrypted = CryptoUtils.encryptAES256(plainText);
    if (CommonUtils.isNullOrEmpty(encrypted)) {
      return plainText;
    }
    return CryptoUtils.encodeBase64(encrypted);
  }

  /** 참여자 목록 조회 */
  @Transactional(readOnly = true)
  public List<SurveyUserResponse> getParticipants(Integer eventSeq) {
    return surveyUserMapper.selectCompletedByEventSeq(eventSeq).stream()
        .map(SurveyUserResponse::from)
        .collect(Collectors.toList());
  }

  /** 미참여/접속자 목록 조회 */
  @Transactional(readOnly = true)
  public List<SurveyUserResponse> getAbsenteesAndLurkers(Integer eventSeq) {
    return surveyUserMapper.selectAbsenteesAndLurkers(eventSeq).stream()
        .map(SurveyUserResponse::from)
        .collect(Collectors.toList());
  }

  /** 이벤트 활성 상태 확인 */
  private void validateEventActive(SurveyMaster event) {
    if (!event.isActive()) {
      String message =
          switch (event.getStatus()) {
            case "A" -> "아직 시작되지 않은 설문입니다.";
            case "S" -> "일시 중지된 설문입니다.";
            case "F" -> "종료된 설문입니다.";
            default -> "설문에 참여할 수 없습니다.";
          };
      throw new BusinessException(ErrorCode.INVALID_INPUT, message);
    }
  }

  /** 설문 응답 빌드 */
  private EventResponse buildSurveyResponse(SurveyMaster event) {
    EventResponse response = EventResponse.from(event);

    // 문항 목록 조회
    List<SurveyQuestion> questions = surveyQuestionMapper.selectByEventSeq(event.getEventSeq());
    List<SurveyItem> allItems = surveyItemMapper.selectByEventSeq(event.getEventSeq());

    List<QuestionResponse> questionResponses =
        questions.stream()
            .map(
                q -> {
                  QuestionResponse qr = QuestionResponse.from(q);
                  List<ItemResponse> items =
                      allItems.stream()
                          .filter(item -> item.getQuestionSeq().equals(q.getQuestionSeq()))
                          .map(ItemResponse::from)
                          .collect(Collectors.toList());
                  if (!items.isEmpty()) {
                    qr = qr.withItems(items);
                  }
                  return qr;
                })
            .collect(Collectors.toList());

    response = response.withQuestions(questionResponses);

    // 이미지 URL을 절대 경로로 변환
    if (apiBaseUrl != null && !apiBaseUrl.isEmpty()) {
      response.withFullImageUrls(apiBaseUrl);
    }

    return response;
  }
}
