package kr.wisead.domain.payment.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.admin.service.AdminService;
import kr.wisead.domain.payment.dto.ChargeBonusEventRequest;
import kr.wisead.domain.payment.dto.ChargeBonusEventResponse;
import kr.wisead.domain.payment.entity.ChargeBonusEvent;
import kr.wisead.mapper.primary.ChargeBonusEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 충전 보너스 이벤트 관리 서비스
 * - 최고관리자A(level 99)만 사용 가능
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargeBonusEventService {

    private final ChargeBonusEventMapper chargeBonusEventMapper;
    private final AdminService adminService;

    /**
     * 이벤트 생성
     */
    @Transactional
    public ChargeBonusEventResponse createEvent(ChargeBonusEventRequest request, String createdBy) {
        // 권한 체크: 최고관리자A(level 99)만 생성 가능
        validateSuperAdmin(createdBy);
        validateRequest(request);

        ChargeBonusEvent event = ChargeBonusEvent.builder()
                .eventName(request.getEventName())
                .eventType(request.getEventType())
                .bonusRate(request.getBonusRate())
                .bonusAmount(request.getBonusAmount())
                .minChargeAmount(request.getMinChargeAmount())
                .maxBonusAmount(request.getMaxBonusAmount())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .bonusExpireDays(request.getBonusExpireDays())
                .status(request.getStatus())
                .createdBy(createdBy)
                .build();

        chargeBonusEventMapper.insert(event);
        log.info("충전 보너스 이벤트 생성: eventSeq={}, eventName={}, createdBy={}",
                event.getEventSeq(), event.getEventName(), createdBy);

        return ChargeBonusEventResponse.from(event);
    }

    /**
     * 이벤트 조회
     */
    public ChargeBonusEventResponse getEvent(Long eventSeq) {
        ChargeBonusEvent event = chargeBonusEventMapper.selectBySeq(eventSeq)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이벤트입니다: " + eventSeq));
        return ChargeBonusEventResponse.from(event);
    }

    /**
     * 전체 이벤트 목록 조회 (관리자용)
     */
    public List<ChargeBonusEventResponse> getAllEvents() {
        return chargeBonusEventMapper.selectAll().stream()
                .map(ChargeBonusEventResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 현재 활성 이벤트 목록 조회
     */
    public List<ChargeBonusEventResponse> getActiveEvents(BigDecimal chargeAmount) {
        LocalDate today = LocalDate.now();
        return chargeBonusEventMapper.selectActiveEvents(today, chargeAmount).stream()
                .map(ChargeBonusEventResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 이벤트 수정
     */
    @Transactional
    public ChargeBonusEventResponse updateEvent(Long eventSeq, ChargeBonusEventRequest request, String userId) {
        // 권한 체크: 최고관리자A(level 99)만 수정 가능
        validateSuperAdmin(userId);
        validateRequest(request);

        ChargeBonusEvent existing = chargeBonusEventMapper.selectBySeq(eventSeq)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이벤트입니다: " + eventSeq));

        ChargeBonusEvent updated = ChargeBonusEvent.builder()
                .eventSeq(eventSeq)
                .eventName(request.getEventName())
                .eventType(request.getEventType())
                .bonusRate(request.getBonusRate())
                .bonusAmount(request.getBonusAmount())
                .minChargeAmount(request.getMinChargeAmount())
                .maxBonusAmount(request.getMaxBonusAmount())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .bonusExpireDays(request.getBonusExpireDays())
                .status(request.getStatus())
                .createdBy(existing.getCreatedBy())
                .createdAt(existing.getCreatedAt())
                .build();

        chargeBonusEventMapper.update(updated);
        log.info("충전 보너스 이벤트 수정: eventSeq={}, eventName={}, modifiedBy={}", eventSeq, request.getEventName(), userId);

        return ChargeBonusEventResponse.from(updated);
    }

    /**
     * 이벤트 상태 변경
     */
    @Transactional
    public void updateEventStatus(Long eventSeq, String status, String userId) {
        // 권한 체크: 최고관리자A(level 99)만 상태 변경 가능
        validateSuperAdmin(userId);

        if (!isValidStatus(status)) {
            throw new IllegalArgumentException("유효하지 않은 상태입니다: " + status);
        }

        chargeBonusEventMapper.selectBySeq(eventSeq)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이벤트입니다: " + eventSeq));

        chargeBonusEventMapper.updateStatus(eventSeq, status);
        log.info("충전 보너스 이벤트 상태 변경: eventSeq={}, status={}, modifiedBy={}", eventSeq, status, userId);
    }

    /**
     * 이벤트 삭제
     */
    @Transactional
    public void deleteEvent(Long eventSeq, String userId) {
        // 권한 체크: 최고관리자A(level 99)만 삭제 가능
        validateSuperAdmin(userId);

        chargeBonusEventMapper.selectBySeq(eventSeq)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이벤트입니다: " + eventSeq));

        chargeBonusEventMapper.delete(eventSeq);
        log.info("충전 보너스 이벤트 삭제: eventSeq={}, deletedBy={}", eventSeq, userId);
    }

    /**
     * 요청 데이터 유효성 검증
     */
    private void validateRequest(ChargeBonusEventRequest request) {
        String eventType = request.getEventType();

        if ("PERCENTAGE".equals(eventType)) {
            if (request.getBonusRate() == null || request.getBonusRate().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("PERCENTAGE 타입은 보너스 비율이 필수입니다.");
            }
            if (request.getBonusRate().compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("보너스 비율은 100%(1.0)를 초과할 수 없습니다.");
            }
        } else if ("FIXED".equals(eventType)) {
            if (request.getBonusAmount() == null || request.getBonusAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("FIXED 타입은 보너스 금액이 필수입니다.");
            }
        } else {
            throw new IllegalArgumentException("유효하지 않은 이벤트 타입입니다: " + eventType);
        }

        if (request.getEndDate() != null && request.getStartDate() != null) {
            if (request.getEndDate().isBefore(request.getStartDate())) {
                throw new IllegalArgumentException("종료일이 시작일보다 빠를 수 없습니다.");
            }
        }
    }

    private boolean isValidStatus(String status) {
        return "ACTIVE".equals(status) || "INACTIVE".equals(status);
    }

    /**
     * 최고관리자A(level 99) 권한 검증
     */
    private void validateSuperAdmin(String userId) {
        Integer userLevel = adminService.getUserLevel(userId);
        if (userLevel == null || userLevel != 99) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "충전 보너스 이벤트 관리 권한이 없습니다. (최고관리자A만 가능)");
        }
    }
}
