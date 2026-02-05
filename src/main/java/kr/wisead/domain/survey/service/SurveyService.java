package kr.wisead.domain.survey.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.survey.dto.*;
import kr.wisead.domain.survey.entity.*;
import kr.wisead.mapper.primary.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  @org.springframework.beans.factory.annotation.Value("${api.base.url:}")
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
    SurveyUser user =
        surveyUserMapper
            .selectByEventSeqAndUserKey(eventSeq, request.getUserKey())
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

    // 답변 저장
    if (request.getAnswers() != null) {
      for (SurveySubmitRequest.AnswerRequest answerReq : request.getAnswers()) {
        SurveyAnswer answer;

        if ("FE".equals(answerReq.getQuestionTypeDetail())) {
          // 파일 업로드
          answer =
              SurveyAnswer.createFileUpload(
                  eventSeq, answerReq.getQuestionSeq(), user.getSeq(), answerReq.getFilePath());
        } else if ("MC".equals(answerReq.getQuestionType())) {
          // 객관식
          answer =
              SurveyAnswer.createMultipleChoice(
                  eventSeq,
                  answerReq.getQuestionSeq(),
                  user.getSeq(),
                  answerReq.getItemSeq(),
                  answerReq.getQuestionType(),
                  answerReq.getQuestionTypeDetail(),
                  answerReq.getAnswer());
        } else {
          // 주관식
          answer =
              SurveyAnswer.createShortAnswer(
                  eventSeq, answerReq.getQuestionSeq(), user.getSeq(), answerReq.getAnswer());
        }

        surveyAnswerMapper.insert(answer);
      }
    }

    // 사용자 정보 업데이트 및 제출 처리
    user.submit(
        request.getUserName(),
        request.getJuminNum(),
        request.getUserPhone(),
        request.getUserEmail(),
        request.getAddress(),
        request.getAddress2(),
        request.getUserKey());
    surveyUserMapper.updateSubmission(user);

    log.info("설문 제출 완료 - eventSeq: {}, userKey: {}", eventSeq, request.getUserKey());
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
                  if (q.isMultipleChoice()) {
                    List<ItemResponse> items =
                        allItems.stream()
                            .filter(item -> item.getQuestionSeq().equals(q.getQuestionSeq()))
                            .map(ItemResponse::from)
                            .collect(Collectors.toList());
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
