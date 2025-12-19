package kr.wisead.domain.event.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.event.dto.*;
import kr.wisead.domain.event.entity.EventActionType;
import kr.wisead.mapper.primary.EventActionTypeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 행사 액션 유형 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventActionTypeService {

    private final EventActionTypeMapper actionTypeMapper;

    /**
     * 액션 유형 목록 조회
     */
    @Transactional(readOnly = true)
    public List<EventActionTypeResponse> getActionTypes(Integer eventSeq) {
        return actionTypeMapper.selectByEventSeq(eventSeq).stream()
                .map(EventActionTypeResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 액션 유형 등록
     */
    @Transactional
    public EventActionTypeResponse createActionType(EventActionTypeRequest request) {
        // 중복 체크
        if (actionTypeMapper.selectByEventSeqAndActionCode(request.getEventSeq(), request.getActionCode()).isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "이미 등록된 액션 코드입니다.");
        }

        EventActionType actionType = request.toEntity();
        actionTypeMapper.insert(actionType);

        return actionTypeMapper.selectBySeq(actionType.getSeq())
                .map(EventActionTypeResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "등록된 액션 유형을 찾을 수 없습니다."));
    }

    /**
     * 액션 유형 수정
     */
    @Transactional
    public EventActionTypeResponse updateActionType(Long seq, EventActionTypeRequest request) {
        EventActionType existing = actionTypeMapper.selectBySeq(seq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "액션 유형을 찾을 수 없습니다."));

        EventActionType updated = EventActionType.builder()
                .seq(seq)
                .eventSeq(existing.getEventSeq())
                .actionCode(existing.getActionCode()) // 액션 코드는 변경 불가
                .actionName(request.getActionName())
                .requireAdminAuth(request.getRequireAdminAuth())
                .allowMultiple(request.getAllowMultiple())
                .sortOrder(request.getSortOrder())
                .useYn(request.getUseYn())
                .build();

        actionTypeMapper.update(updated);

        return actionTypeMapper.selectBySeq(seq)
                .map(EventActionTypeResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "액션 유형을 찾을 수 없습니다."));
    }

    /**
     * 액션 유형 삭제
     */
    @Transactional
    public void deleteActionType(Long seq) {
        EventActionType existing = actionTypeMapper.selectBySeq(seq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "액션 유형을 찾을 수 없습니다."));

        // CHECK_IN은 삭제 불가
        if ("CHECK_IN".equals(existing.getActionCode())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "입장(CHECK_IN) 액션은 삭제할 수 없습니다.");
        }

        actionTypeMapper.delete(seq);
    }

    /**
     * 기본 액션 유형 생성 (행사 생성 시 호출)
     */
    @Transactional
    public void createDefaultActionTypes(Integer eventSeq) {
        List<EventActionType> defaultTypes = Arrays.asList(
                EventActionType.builder()
                        .eventSeq(eventSeq)
                        .actionCode("CHECK_IN")
                        .actionName("입장")
                        .requireAdminAuth("N")
                        .allowMultiple("N")
                        .sortOrder(1)
                        .useYn("Y")
                        .build()
        );

        actionTypeMapper.insertBatch(defaultTypes);

        log.info("기본 액션 유형 생성 완료: eventSeq={}", eventSeq);
    }

    /**
     * 행사 삭제 시 액션 유형 삭제
     */
    @Transactional
    public void deleteByEventSeq(Integer eventSeq) {
        actionTypeMapper.deleteByEventSeq(eventSeq);
    }
}
