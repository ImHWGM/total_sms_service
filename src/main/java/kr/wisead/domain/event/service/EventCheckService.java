package kr.wisead.domain.event.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.event.dto.*;
import kr.wisead.domain.event.entity.*;
import kr.wisead.mapper.primary.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 체크인/액션 처리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventCheckService {

    private final EventParticipantMapper participantMapper;
    private final EventActionTypeMapper actionTypeMapper;
    private final EventActionLogMapper actionLogMapper;
    private final EventNametagLogMapper nametagLogMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${wisead.url:http://localhost:8080}")
    private String wiseadUrl;

    /**
     * QR 스캔으로 체크인 처리 (참가자용)
     */
    @Transactional
    public EventCheckResponse checkIn(String checkCode, String deviceInfo) {
        // 1. 참가자 조회
        EventParticipant participant = participantMapper.selectDetailByCheckCode(checkCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

        // 2. CHECK_IN 액션 유형 조회
        EventActionType checkInType = actionTypeMapper.selectByEventSeqAndActionCode(
                        participant.getEventSeq(), "CHECK_IN")
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "체크인 설정이 되어있지 않습니다."));

        // 3. 이미 체크인했는지 확인
        boolean alreadyCheckedIn = actionLogMapper.existsByParticipantSeqAndActionTypeSeq(
                participant.getSeq(), checkInType.getSeq());

        EventParticipantResponse participantResponse = EventParticipantResponse.from(participant);

        if (alreadyCheckedIn) {
            return EventCheckResponse.alreadyCheckedIn(participantResponse);
        }

        // 4. 체크인 로그 등록
        EventActionLog actionLog = EventActionLog.create(
                participant.getSeq(),
                checkInType.getSeq(),
                deviceInfo,
                null
        );
        actionLogMapper.insert(actionLog);

        // 5. 명찰 URL 생성
        String nametagUrl = wiseadUrl + "/api/events/" + participant.getEventSeq()
                + "/nametag/" + participant.getSeq();

        log.info("참가자 체크인 완료: eventSeq={}, participantSeq={}, name={}",
                participant.getEventSeq(), participant.getSeq(), participant.getUserName());

        return EventCheckResponse.checkIn(participantResponse, nametagUrl);
    }

    /**
     * 액션 처리 (관리자용)
     */
    @Transactional
    public EventCheckResponse processAction(Integer eventSeq, EventCheckRequest request, String adminId) {
        // 1. 참가자 조회
        EventParticipant participant = participantMapper.selectDetailBySeq(request.getParticipantSeq())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

        // 이벤트 일치 확인
        if (!participant.getEventSeq().equals(eventSeq)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "해당 행사의 참가자가 아닙니다.");
        }

        // 2. 액션 유형 조회
        EventActionType actionType = actionTypeMapper.selectByEventSeqAndActionCode(eventSeq, request.getActionCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "해당 액션 유형을 찾을 수 없습니다."));

        // 3. 관리자 인증 필요 여부 확인
        String confirmedBy = null;
        if (actionType.isAdminAuthRequired()) {
            if (request.getAdminPassword() == null || request.getAdminPassword().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "관리자 비밀번호가 필요합니다.");
            }

            // 관리자 비밀번호 검증
            var admin = userMapper.findByUserId(adminId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "관리자 정보를 찾을 수 없습니다."));

            if (!passwordEncoder.matches(request.getAdminPassword(), admin.getUserPass())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "관리자 비밀번호가 일치하지 않습니다.");
            }

            confirmedBy = adminId;
        }

        // 4. 중복 처리 확인 (중복 허용이 아닌 경우)
        if (!actionType.isMultipleAllowed()) {
            boolean alreadyDone = actionLogMapper.existsByParticipantSeqAndActionTypeSeq(
                    participant.getSeq(), actionType.getSeq());
            if (alreadyDone) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "이미 " + actionType.getActionName() + " 처리된 참가자입니다.");
            }
        }

        // 5. 액션 로그 등록
        EventActionLog actionLog;
        if (confirmedBy != null) {
            actionLog = EventActionLog.createWithAdminAuth(
                    participant.getSeq(),
                    actionType.getSeq(),
                    request.getDeviceInfo(),
                    confirmedBy,
                    request.getMemo()
            );
        } else {
            actionLog = EventActionLog.create(
                    participant.getSeq(),
                    actionType.getSeq(),
                    request.getDeviceInfo(),
                    request.getMemo()
            );
        }
        actionLogMapper.insert(actionLog);

        log.info("액션 처리 완료: eventSeq={}, participantSeq={}, actionCode={}, confirmedBy={}",
                eventSeq, participant.getSeq(), request.getActionCode(), confirmedBy);

        EventParticipantResponse participantResponse = EventParticipantResponse.from(participant);
        return EventCheckResponse.action(actionType.getActionCode(), actionType.getActionName(), participantResponse);
    }

    /**
     * 체크인 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean isCheckedIn(Long participantSeq, Integer eventSeq) {
        EventActionType checkInType = actionTypeMapper.selectByEventSeqAndActionCode(eventSeq, "CHECK_IN")
                .orElse(null);

        if (checkInType == null) {
            return false;
        }

        return actionLogMapper.existsByParticipantSeqAndActionTypeSeq(participantSeq, checkInType.getSeq());
    }
}
