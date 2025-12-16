package kr.wisead.domain.ars.service;

import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.ars.dto.BlockedSenderResponse;
import kr.wisead.mapper.primary.UserMapper;
import kr.wisead.mapper.sms.SendHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * ARS 수신거부 서비스
 * - ARS 시스템 연동 전용 로직
 * - 차단 번호 관리는 BlockedNumberService 사용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArsService {

    private final BlockedNumberService blockedNumberService;
    private final SendHistoryMapper sendHistoryMapper;       // SMS DB
    private final UserMapper userMapper;

    /**
     * 휴대폰 번호 유효성 검사
     */
    public boolean isCellPhone(String ani) {
        return ani != null && ani.matches("^01[016789]\\d{7,8}$");
    }

    /**
     * 상점코드 유효성 검사
     */
    public boolean isValidStoreCode(String storeCode) {
        return blockedNumberService.isValidStoreCode(storeCode);
    }

    /**
     * 상점코드로 회사명 조회
     */
    public String getCorpNameByStoreCode(String storeCode) {
        return blockedNumberService.getCorpNameByStoreCode(storeCode);
    }

    /**
     * 전화번호로 발송자의 상점코드 조회 (자동등록형)
     * 최근 3개월 테이블에서 검색 (SMS DB)
     */
    public String getStoreCodeByAni(String ani, String tTime) {
        String tableName = tTime.substring(0, 6);  // yyyyMM 형식
        int maxAttempts = 3;

        for (int i = 0; i < maxAttempts; i++) {
            try {
                String userId = sendHistoryMapper.selectUserIdByAni(ani, tableName);
                if (userId != null) {
                    // userId로 storeCode 조회 (Primary DB - UserMapper 사용)
                    return userMapper.findByUserId(userId)
                            .map(user -> user.getStoreCode())
                            .orElse(null);
                }
            } catch (Exception e) {
                log.warn("테이블 msg_result_{} 조회 실패: {}", tableName, e.getMessage());
            }
            tableName = getPreviousMonthTableName(tableName);
        }

        return null;
    }

    /**
     * 수신거부 등록 (ARS용)
     */
    @Transactional
    public boolean registerBlockedSender(String ani, String storeCode, String menuName, String tTime) {
        return blockedNumberService.registerBlockedNumber(ani, storeCode, menuName);
    }

    /**
     * 수신거부 목록 조회
     */
    @Transactional(readOnly = true)
    public PageResponse<BlockedSenderResponse> getBlockedSenders(String storeCode, int page, int size) {
        return blockedNumberService.getBlockedNumbers(storeCode, page, size);
    }

    /**
     * 수신거부 삭제
     */
    @Transactional
    public int deleteBlockedSenders(List<Map<String, String>> keyList) {
        return blockedNumberService.deleteBlockedNumbers(keyList);
    }

    /**
     * 수신거부 여부 확인 (발송 시 체크용)
     */
    @Transactional(readOnly = true)
    public boolean isBlocked(String ani, String storeCode) {
        return blockedNumberService.isBlocked(ani, storeCode);
    }

    /**
     * 수신거부 번호 필터링 (일괄 발송 시)
     */
    @Transactional(readOnly = true)
    public List<String> filterBlockedNumbers(String storeCode, List<String> phoneNumbers) {
        return blockedNumberService.filterBlockedNumbers(storeCode, phoneNumbers);
    }

    /**
     * 현재 날짜 문자열 (yyyyMMdd)
     */
    public String getTodayString() {
        return new SimpleDateFormat("yyyyMMdd").format(new Date());
    }

    /**
     * 현재 시간 문자열 (yyyyMMddHHmmss)
     */
    public String getNowString() {
        return new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
    }

    // ==================== Private Methods ====================

    private String getPreviousMonthTableName(String currentTableName) {
        int year = Integer.parseInt(currentTableName.substring(0, 4));
        int month = Integer.parseInt(currentTableName.substring(4, 6));
        month--;
        if (month == 0) {
            month = 12;
            year--;
        }
        return String.format("%04d%02d", year, month);
    }
}
