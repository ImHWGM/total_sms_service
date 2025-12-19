package kr.wisead.domain.event.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.event.dto.*;
import kr.wisead.domain.event.entity.*;
import kr.wisead.domain.survey.entity.SurveyMaster;
import kr.wisead.domain.survey.entity.SurveyUser;
import kr.wisead.mapper.primary.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 행사 참가자 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventParticipantService {

    private final EventParticipantMapper participantMapper;
    private final EventActionTypeMapper actionTypeMapper;
    private final EventActionLogMapper actionLogMapper;
    private final EventNametagLogMapper nametagLogMapper;
    private final SurveyUserMapper surveyUserMapper;
    private final SurveyMasterMapper surveyMasterMapper;

    @Value("${wisead.url:http://localhost:8080}")
    private String wiseadUrl;

    /**
     * 참가자 등록
     */
    @Transactional
    public EventParticipantResponse createParticipant(EventParticipantRequest request, String regId) {
        // 1. SURVEY_USER 생성
        String userKey = UUID.randomUUID().toString().replace("-", "");
        String encryptedPhone = encryptPhone(request.getUserPhone());

        SurveyUser surveyUser = SurveyUser.builder()
                .eventSeq(request.getEventSeq())
                .userKey(userKey)
                .userName(request.getUserName())
                .userPhone(encryptedPhone)
                .userEmail(request.getUserEmail())
                .delYn("N")
                .regId(regId)
                .build();

        surveyUserMapper.insert(surveyUser);

        // 2. EVENT_PARTICIPANT 생성
        EventParticipant participant = EventParticipant.create(
                surveyUser.getSeq(),
                request.getEventSeq(),
                request.getDepartment(),
                request.getPosition(),
                request.getParticipantType(),
                request.getMemo()
        );

        participantMapper.insert(participant);

        // 3. 응답 반환
        EventParticipant saved = participantMapper.selectDetailBySeq(participant.getSeq())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

        return EventParticipantResponse.from(saved)
                .withQrCodeUrl(wiseadUrl);
    }

    /**
     * 참가자 목록 조회
     */
    @Transactional(readOnly = true)
    public PageResponse<EventParticipantResponse> getParticipants(ParticipantSearchRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put("eventSeq", request.getEventSeq());
        params.put("keyword", request.getKeyword());
        params.put("participantType", request.getParticipantType());
        params.put("offset", request.getOffset());
        params.put("limit", request.getSize());

        List<EventParticipant> participants = participantMapper.searchParticipants(params);
        int total = participantMapper.countSearchParticipants(params);

        List<EventParticipantResponse> content = participants.stream()
                .map(p -> {
                    EventParticipantResponse response = EventParticipantResponse.from(p);
                    // 전화번호 복호화
                    if (p.getUserPhone() != null) {
                        response.setUserPhone(decryptPhone(p.getUserPhone()));
                    }
                    return response.withQrCodeUrl(wiseadUrl);
                })
                .collect(Collectors.toList());

        return PageResponse.of(content, request.getPage(), request.getSize(), total);
    }

    /**
     * 참가자 상세 조회
     */
    @Transactional(readOnly = true)
    public EventParticipantResponse getParticipant(Long seq) {
        EventParticipant participant = participantMapper.selectDetailBySeq(seq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

        EventParticipantResponse response = EventParticipantResponse.from(participant);
        if (participant.getUserPhone() != null) {
            response.setUserPhone(decryptPhone(participant.getUserPhone()));
        }
        return response.withQrCodeUrl(wiseadUrl);
    }

    /**
     * 체크코드로 참가자 조회
     */
    @Transactional(readOnly = true)
    public EventParticipantResponse getParticipantByCheckCode(String checkCode) {
        EventParticipant participant = participantMapper.selectDetailByCheckCode(checkCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

        EventParticipantResponse response = EventParticipantResponse.from(participant);
        if (participant.getUserPhone() != null) {
            response.setUserPhone(decryptPhone(participant.getUserPhone()));
        }
        return response.withQrCodeUrl(wiseadUrl);
    }

    /**
     * 참가자 수정
     */
    @Transactional
    public EventParticipantResponse updateParticipant(Long seq, EventParticipantRequest request) {
        EventParticipant participant = participantMapper.selectBySeq(seq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

        participant.update(
                request.getDepartment(),
                request.getPosition(),
                request.getParticipantType(),
                request.getMemo()
        );

        participantMapper.update(participant);

        return getParticipant(seq);
    }

    /**
     * 참가자 삭제
     */
    @Transactional
    public void deleteParticipant(Long seq, String uptId) {
        EventParticipant participant = participantMapper.selectBySeq(seq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

        // 관련 로그 삭제
        actionLogMapper.deleteByParticipantSeq(seq);
        nametagLogMapper.deleteByParticipantSeq(seq);

        // 참가자 삭제
        participantMapper.delete(seq);

        // SURVEY_USER 소프트 삭제
        surveyUserMapper.softDelete(participant.getSurveyUserSeq(), uptId);
    }

    /**
     * 참가자 상태 조회 (액션 현황 포함)
     */
    @Transactional(readOnly = true)
    public ParticipantStatusResponse getParticipantStatus(Long seq) {
        EventParticipant participant = participantMapper.selectDetailBySeq(seq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

        List<Map<String, Object>> actionStatusList =
                actionLogMapper.selectActionStatusByParticipantSeq(seq, participant.getEventSeq());

        List<ParticipantStatusResponse.ActionStatus> actions = actionStatusList.stream()
                .map(m -> ParticipantStatusResponse.ActionStatus.builder()
                        .actionTypeSeq(((Number) m.get("actionTypeSeq")).longValue())
                        .actionCode((String) m.get("actionCode"))
                        .actionName((String) m.get("actionName"))
                        .completed("Y".equals(m.get("completed")))
                        .completedAt((LocalDateTime) m.get("completedAt"))
                        .confirmedBy((String) m.get("confirmedBy"))
                        .requireAdminAuth("Y".equals(m.get("requireAdminAuth")))
                        .allowMultiple("Y".equals(m.get("allowMultiple")))
                        .build())
                .collect(Collectors.toList());

        return ParticipantStatusResponse.builder()
                .participant(ParticipantStatusResponse.ParticipantInfo.builder()
                        .seq(participant.getSeq())
                        .checkCode(participant.getCheckCode())
                        .name(participant.getUserName())
                        .department(participant.getDepartment())
                        .position(participant.getPosition())
                        .participantType(participant.getParticipantType())
                        .phone(decryptPhone(participant.getUserPhone()))
                        .email(participant.getUserEmail())
                        .nametagPrinted(participant.getNametagPrinted())
                        .build())
                .actions(actions)
                .build();
    }

    /**
     * 체크코드로 참가자 상태 조회
     */
    @Transactional(readOnly = true)
    public ParticipantStatusResponse getParticipantStatusByCheckCode(String checkCode) {
        EventParticipant participant = participantMapper.selectByCheckCode(checkCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "참가자 정보를 찾을 수 없습니다."));

        return getParticipantStatus(participant.getSeq());
    }

    /**
     * 전화번호 암호화
     */
    private String encryptPhone(String phone) {
        if (phone == null) return null;
        String cleanPhone = phone.replace("-", "");
        return CryptoUtils.encodeBase64(CryptoUtils.encryptAES256(cleanPhone));
    }

    /**
     * 전화번호 복호화
     */
    private String decryptPhone(String encryptedPhone) {
        if (encryptedPhone == null) return null;
        try {
            return CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(encryptedPhone));
        } catch (Exception e) {
            log.warn("전화번호 복호화 실패: {}", e.getMessage());
            return encryptedPhone;
        }
    }

    // ==================== 통계 기능 ====================

    /**
     * 행사 통계 조회
     */
    @Transactional(readOnly = true)
    public EventStatisticsResponse getStatistics(Integer eventSeq) {
        // 이벤트 정보 조회
        SurveyMaster event = surveyMasterMapper.selectByEventSeq(eventSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "행사 정보를 찾을 수 없습니다."));

        // 참가자 통계 (체크인 현황)
        Map<String, Object> checkInStats = participantMapper.selectCheckInStats(eventSeq);
        int totalCount = getIntValue(checkInStats, "totalCount");
        int checkedInCount = getIntValue(checkInStats, "checkedInCount");
        int notCheckedInCount = totalCount - checkedInCount;
        double checkedInRate = totalCount > 0 ? Math.round((double) checkedInCount / totalCount * 1000) / 10.0 : 0;

        // 참가자 유형별 통계
        List<Map<String, Object>> typeStats = participantMapper.selectParticipantTypeStats(eventSeq);
        List<EventStatisticsResponse.ParticipantTypeStat> byType = typeStats.stream()
                .map(m -> EventStatisticsResponse.ParticipantTypeStat.builder()
                        .participantType((String) m.get("participantType"))
                        .count(getIntValue(m, "count"))
                        .checkedInCount(getIntValue(m, "checkedInCount"))
                        .build())
                .collect(Collectors.toList());

        EventStatisticsResponse.ParticipantSummary participantSummary = EventStatisticsResponse.ParticipantSummary.builder()
                .totalCount(totalCount)
                .checkedInCount(checkedInCount)
                .notCheckedInCount(notCheckedInCount)
                .checkedInRate(checkedInRate)
                .byType(byType)
                .build();

        // 액션별 통계
        List<Map<String, Object>> actionStats = actionLogMapper.selectActionStatsByEventSeq(eventSeq);
        List<EventStatisticsResponse.ActionStatistics> actionStatistics = actionStats.stream()
                .map(m -> {
                    int actionTotal = getIntValue(m, "totalCount");
                    int completedCount = getIntValue(m, "completedCount");
                    double completionRate = actionTotal > 0 ? Math.round((double) completedCount / actionTotal * 1000) / 10.0 : 0;

                    return EventStatisticsResponse.ActionStatistics.builder()
                            .actionTypeSeq(((Number) m.get("actionTypeSeq")).longValue())
                            .actionCode((String) m.get("actionCode"))
                            .actionName((String) m.get("actionName"))
                            .totalCount(actionTotal)
                            .completedCount(completedCount)
                            .completionRate(completionRate)
                            .build();
                })
                .collect(Collectors.toList());

        // 명찰 출력 통계
        Map<String, Object> nametagStats = participantMapper.selectNametagStats(eventSeq);
        int nametagTotal = getIntValue(nametagStats, "totalCount");
        int printedCount = getIntValue(nametagStats, "printedCount");
        int notPrintedCount = getIntValue(nametagStats, "notPrintedCount");
        double printRate = nametagTotal > 0 ? Math.round((double) printedCount / nametagTotal * 1000) / 10.0 : 0;

        EventStatisticsResponse.NametagSummary nametagSummary = EventStatisticsResponse.NametagSummary.builder()
                .totalCount(nametagTotal)
                .printedCount(printedCount)
                .notPrintedCount(notPrintedCount)
                .printRate(printRate)
                .build();

        return EventStatisticsResponse.builder()
                .eventSeq(eventSeq)
                .eventName(event.getEventName())
                .participantSummary(participantSummary)
                .actionStatistics(actionStatistics)
                .nametagSummary(nametagSummary)
                .build();
    }

    // ==================== 엑셀 다운로드 ====================

    /**
     * 엑셀 다운로드용 참가자 목록 조회
     */
    @Transactional(readOnly = true)
    public List<EventParticipant> getParticipantsForExcel(Integer eventSeq) {
        List<EventParticipant> participants = participantMapper.selectAllForExcel(eventSeq);

        // 전화번호 복호화
        participants.forEach(p -> {
            if (p.getUserPhone() != null) {
                try {
                    String decrypted = decryptPhone(p.getUserPhone());
                    // Setter가 없으므로 Builder로 새 객체 생성은 비효율적
                    // 대신 reflection 또는 별도 DTO 사용
                } catch (Exception e) {
                    log.warn("전화번호 복호화 실패: seq={}", p.getSeq());
                }
            }
        });

        return participants;
    }

    /**
     * 엑셀 다운로드용 DTO 변환
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getParticipantsForExcelAsMap(Integer eventSeq) {
        List<EventParticipant> participants = participantMapper.selectAllForExcel(eventSeq);

        return participants.stream()
                .map(p -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("이름", p.getUserName());
                    row.put("연락처", formatPhone(decryptPhone(p.getUserPhone())));
                    row.put("이메일", p.getUserEmail());
                    row.put("소속", p.getDepartment());
                    row.put("직책", p.getPosition());
                    row.put("참가자 유형", p.getParticipantType());
                    row.put("명찰 출력", "Y".equals(p.getNametagPrinted()) ? "출력완료" : "미출력");
                    row.put("액션 현황", p.getActionSummary());
                    row.put("메모", p.getMemo());
                    row.put("등록일", p.getRegDate() != null ? p.getRegDate().toString() : "");
                    return row;
                })
                .collect(Collectors.toList());
    }

    private int getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return 0;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String formatPhone(String phone) {
        if (phone == null || phone.length() < 10) return phone;
        // 01012345678 -> 010-1234-5678
        if (phone.length() == 11) {
            return phone.substring(0, 3) + "-" + phone.substring(3, 7) + "-" + phone.substring(7);
        } else if (phone.length() == 10) {
            return phone.substring(0, 3) + "-" + phone.substring(3, 6) + "-" + phone.substring(6);
        }
        return phone;
    }
}
