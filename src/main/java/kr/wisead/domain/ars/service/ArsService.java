package kr.wisead.domain.ars.service;

import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.ars.dto.BlockedSenderResponse;
import kr.wisead.domain.ars.entity.BlockedSender;
import kr.wisead.mapper.primary.BlockedSenderMapper;
import kr.wisead.mapper.primary.UserMapper;
import kr.wisead.mapper.sms.SendHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * ARS 수신거부 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArsService {

    private final BlockedSenderMapper blockedSenderMapper;  // Primary DB
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
        if (storeCode == null || storeCode.isEmpty()) {
            return false;
        }
        String corpName = blockedSenderMapper.selectCorpNameByStoreCode(storeCode);
        return corpName != null && !corpName.isEmpty();
    }

    /**
     * 상점코드로 회사명 조회
     */
    public String getCorpNameByStoreCode(String storeCode) {
        return blockedSenderMapper.selectCorpNameByStoreCode(storeCode);
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
     * 수신거부 등록
     */
    @Transactional
    public boolean registerBlockedSender(String ani, String storeCode, String menuName, String tTime) {
        // 암호화된 번호로 중복 확인
        String encryptedAni = encryptAni(ani);
        int exists = blockedSenderMapper.countBlockedSender(encryptedAni, storeCode);

        if (exists > 0) {
            log.info("이미 등록된 수신거부: ani={}, storeCode={}", maskPhone(ani), storeCode);
            return false;
        }

        BlockedSender blockedSender = BlockedSender.builder()
                .tTime(tTime)
                .ani(encryptedAni)
                .dtmf1(storeCode)
                .menuName(menuName)
                .build();

        blockedSenderMapper.insertBlockedSender(blockedSender);
        log.info("수신거부 등록 완료: ani={}, storeCode={}", maskPhone(ani), storeCode);
        return true;
    }

    /**
     * 수신거부 목록 조회
     */
    @Transactional(readOnly = true)
    public PageResponse<BlockedSenderResponse> getBlockedSenders(String storeCode, int page, int size) {
        int offset = (page - 1) * size;

        List<BlockedSender> blockedSenders = blockedSenderMapper.selectBlockedSendersWithPaging(storeCode, offset, size);
        int total = blockedSenderMapper.countBlockedSendersByStoreCode(storeCode);

        List<BlockedSenderResponse> responses = blockedSenders.stream()
                .map(bs -> BlockedSenderResponse.from(bs, decryptAni(bs.getAni())))
                .toList();

        return PageResponse.of(responses, page, size, total);
    }

    /**
     * 수신거부 삭제
     */
    @Transactional
    public int deleteBlockedSenders(List<Map<String, String>> keyList) {
        if (keyList == null || keyList.isEmpty()) {
            return 0;
        }

        int deletedCount = blockedSenderMapper.deleteBlockedSenders(keyList);
        log.info("수신거부 삭제 완료: {}건", deletedCount);
        return deletedCount;
    }

    /**
     * 수신거부 여부 확인 (발송 시 체크용)
     */
    @Transactional(readOnly = true)
    public boolean isBlocked(String ani, String storeCode) {
        String encryptedAni = encryptAni(ani);
        return blockedSenderMapper.isBlockedNumber(encryptedAni, storeCode);
    }

    /**
     * 수신거부 번호 필터링 (일괄 발송 시)
     */
    @Transactional(readOnly = true)
    public List<String> filterBlockedNumbers(String storeCode, List<String> phoneNumbers) {
        List<String> encryptedNumbers = phoneNumbers.stream()
                .map(this::encryptAni)
                .toList();

        return blockedSenderMapper.selectBlockedNumbers(storeCode, encryptedNumbers);
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

    /**
     * 전화번호 암호화 (AES256 + Base64)
     */
    private String encryptAni(String ani) {
        // TODO: CryptoUtils 구현 필요
        // 임시로 Base64 인코딩만 적용
        return Base64.getEncoder().encodeToString(ani.getBytes());
    }

    /**
     * 전화번호 복호화
     */
    private String decryptAni(String encryptedAni) {
        // TODO: CryptoUtils 구현 필요
        // 임시로 Base64 디코딩만 적용
        try {
            return new String(Base64.getDecoder().decode(encryptedAni));
        } catch (Exception e) {
            return encryptedAni;
        }
    }

    /**
     * 전화번호 마스킹
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
