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

  /** 행사 read 권한(소유권) 검증 */
  @Transactional(readOnly = true)
  public void validateEventReadAccess(Integer eventSeq, String userId) {
    SurveyMaster event =
        surveyMasterMapper
            .selectByEventSeq(eventSeq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "이벤트 정보를 찾을 수 없습니다."));
    Integer userLevel = adminService.getUserLevel(userId);
    adminService.validateModifyPermission(userId, userLevel, event.getRegId());
  }

  /** 참가자가 해당 행사 소속인지 검증 (path eventSeq ↔ 참가자 eventSeq 정합) */
  public void validateParticipantEvent(Integer eventSeq, EventParticipant participant) {
    if (!Objects.equals(participant.getEventSeq(), eventSeq)) {
      throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다.");
    }
  }
}
