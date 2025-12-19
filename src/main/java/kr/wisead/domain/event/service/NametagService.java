package kr.wisead.domain.event.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.event.dto.NametagPrintRequest;
import kr.wisead.domain.event.entity.*;
import kr.wisead.mapper.primary.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * 명찰 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NametagService {

    private final EventParticipantMapper participantMapper;
    private final EventNametagLogMapper nametagLogMapper;
    private final SurveyMasterMapper surveyMasterMapper;

    /**
     * 명찰 데이터 조회 (출력/미리보기용)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getNametagData(Long participantSeq) {
        EventParticipant participant = participantMapper.selectDetailBySeq(participantSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

        Map<String, Object> nametagData = new HashMap<>();
        nametagData.put("participantSeq", participant.getSeq());
        nametagData.put("eventName", participant.getEventName());
        nametagData.put("name", participant.getUserName());
        nametagData.put("department", participant.getDepartment());
        nametagData.put("position", participant.getPosition());
        nametagData.put("participantType", participant.getParticipantType());
        nametagData.put("checkCode", participant.getCheckCode());

        return nametagData;
    }

    /**
     * 명찰 출력 로그 기록
     */
    @Transactional
    public void recordPrint(NametagPrintRequest request, String printBy) {
        EventParticipant participant = participantMapper.selectBySeq(request.getParticipantSeq())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

        // 명찰 출력 로그 등록
        EventNametagLog nametagLog = EventNametagLog.create(
                participant.getSeq(),
                request.getTemplateType(),
                printBy
        );
        nametagLogMapper.insert(nametagLog);

        // 참가자 명찰 출력 여부 업데이트
        participantMapper.updateNametagPrinted(participant.getSeq(), "Y");

        log.info("명찰 출력 완료: participantSeq={}, templateType={}, printBy={}",
                participant.getSeq(), request.getTemplateType(), printBy);
    }

    /**
     * 명찰 출력 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean isNametagPrinted(Long participantSeq) {
        return nametagLogMapper.existsByParticipantSeq(participantSeq);
    }
}
