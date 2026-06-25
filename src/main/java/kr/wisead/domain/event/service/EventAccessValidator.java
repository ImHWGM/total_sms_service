package kr.wisead.domain.event.service;

import java.util.Objects;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.event.entity.EventParticipant;
import kr.wisead.domain.survey.entity.SurveyMaster;
import kr.wisead.mapper.primary.SurveyMasterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 행사 read 접근/소유권 검증 공통 컴포넌트 (EventParticipant/Nametag/Excel 서비스 공유) */
@Component
@RequiredArgsConstructor
public class EventAccessValidator {

  private final SurveyMasterMapper surveyMasterMapper;
  private final AdminService adminService;

  /**
   * 행사 read 권한(조회 범위) 검증.
   *
   * <p>B 레벨(최고관리자B/운영관리자B)은 A와 조회 범위가 동일하므로 read 경로에서는 수정 권한이 아닌 조회 권한으로 검증한다.
   */
  @Transactional(readOnly = true)
  public void validateEventReadAccess(Integer eventSeq, String userId) {
    SurveyMaster event = findEvent(eventSeq);
    Integer userLevel = adminService.getUserLevel(userId);
    adminService.validateReadPermission(userId, userLevel, event.getRegId());
  }

  /**
   * 행사 modify 권한(수정/삭제) 검증.
   *
   * <p>참가자 수정·삭제·명찰 출력 등 상태 변경 경로에서 사용한다. B 레벨은 본인 데이터만 수정 가능하므로 read 경로와 분리한다.
   */
  @Transactional(readOnly = true)
  public void validateEventModifyAccess(Integer eventSeq, String userId) {
    SurveyMaster event = findEvent(eventSeq);
    Integer userLevel = adminService.getUserLevel(userId);
    adminService.validateModifyPermission(userId, userLevel, event.getRegId());
  }

  private SurveyMaster findEvent(Integer eventSeq) {
    return surveyMasterMapper
        .selectByEventSeq(eventSeq)
        .orElseThrow(
            () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트 정보를 찾을 수 없습니다."));
  }

  /** 참가자가 해당 행사 소속인지 검증 (path eventSeq ↔ 참가자 eventSeq 정합) */
  public void validateParticipantEvent(Integer eventSeq, EventParticipant participant) {
    if (!Objects.equals(participant.getEventSeq(), eventSeq)) {
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다.");
    }
  }
}
