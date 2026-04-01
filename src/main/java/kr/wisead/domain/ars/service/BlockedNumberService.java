package kr.wisead.domain.ars.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.util.CommonUtils;
import kr.wisead.common.util.CryptoUtils;
import kr.wisead.domain.ars.dto.BlockedSenderResponse;
import kr.wisead.domain.ars.entity.BlockedSender;
import kr.wisead.mapper.primary.BlockedSenderMapper;
import kr.wisead.mapper.primary.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 차단 번호(수신거부) 관리 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockedNumberService {

  private final BlockedSenderMapper blockedSenderMapper;
  private final UserMapper userMapper;

  /**
   * 수신거부 등록
   *
   * @param phoneNumber 전화번호 (평문)
   * @param storeCode 상점코드
   * @param menuName 080 번호
   * @return 등록 성공 여부
   */
  @Transactional
  public boolean registerBlockedNumber(String phoneNumber, String storeCode, String menuName) {
    // 전화번호 유효성 검사
    if (!isValidPhoneNumber(phoneNumber)) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 전화번호입니다.");
    }

    // 상점코드 유효성 검사
    if (storeCode == null || storeCode.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "상점코드는 필수입니다.");
    }

    // 암호화
    String encryptedPhone = encryptPhoneNumber(phoneNumber);

    // 중복 확인
    int exists = blockedSenderMapper.countBlockedSender(encryptedPhone, storeCode);
    if (exists > 0) {
      log.info(
          "이미 등록된 수신거부: phone={}, storeCode={}", CommonUtils.maskingPhone(phoneNumber), storeCode);
      return false;
    }

    // 등록
    String tTime = getCurrentTimeString();
    BlockedSender blockedSender =
        BlockedSender.builder()
            .tTime(tTime)
            .ani(encryptedPhone)
            .dtmf1(storeCode)
            .menuName(menuName != null ? menuName : "")
            .build();

    blockedSenderMapper.insertBlockedSender(blockedSender);
    log.info(
        "수신거부 등록 완료: phone={}, storeCode={}", CommonUtils.maskingPhone(phoneNumber), storeCode);
    return true;
  }

  /**
   * 수신거부 일괄 등록
   *
   * @param phoneNumbers 전화번호 목록 (평문)
   * @param storeCode 상점코드
   * @param menuName 080 번호
   * @return 등록된 건수
   */
  @Transactional
  public int registerBlockedNumbers(List<String> phoneNumbers, String storeCode, String menuName) {
    if (phoneNumbers == null || phoneNumbers.isEmpty()) {
      return 0;
    }

    int registeredCount = 0;
    for (String phoneNumber : phoneNumbers) {
      try {
        if (registerBlockedNumber(phoneNumber, storeCode, menuName)) {
          registeredCount++;
        }
      } catch (Exception e) {
        log.warn(
            "수신거부 등록 실패: phone={}, error={}",
            CommonUtils.maskingPhone(phoneNumber),
            e.getMessage());
      }
    }

    log.info("수신거부 일괄 등록 완료: total={}, registered={}", phoneNumbers.size(), registeredCount);
    return registeredCount;
  }

  /** 수신거부 목록 조회 (페이징) */
  @Transactional(readOnly = true)
  public PageResponse<BlockedSenderResponse> getBlockedNumbers(
      String storeCode, int page, int size) {
    int offset = (page - 1) * size;

    List<BlockedSender> blockedSenders =
        blockedSenderMapper.selectBlockedSendersWithPaging(storeCode, offset, size);
    int total = blockedSenderMapper.countBlockedSendersByStoreCode(storeCode);

    String storeId = userMapper.findUserIdByStoreCode(storeCode);

    List<BlockedSenderResponse> responses =
        blockedSenders.stream()
            .map(bs -> BlockedSenderResponse.from(bs, decryptPhoneNumber(bs.getAni()), storeId))
            .collect(Collectors.toList());

    return PageResponse.of(responses, page, size, total);
  }

  /**
   * 수신거부 검색 조회 (권한 기반 + 검색 조건)
   *
   * @param queryUserIds 조회 가능한 사용자 ID ("ALL" 또는 콤마 구분)
   * @param senderId 사용자 아이디 검색 (부분 검색, nullable)
   * @param unsubscribeNumber 수신거부 번호 검색 (완전 일치, nullable)
   * @param page 페이지 번호
   * @param size 페이지 크기
   */
  @Transactional(readOnly = true)
  public PageResponse<BlockedSenderResponse> searchBlockedNumbers(
      String queryUserIds, String senderId, String unsubscribeNumber, int page, int size) {

    List<String> storeCodes = resolveStoreCodes(queryUserIds);
    if (storeCodes != null && storeCodes.isEmpty()) {
      return PageResponse.empty();
    }

    String encryptedAni = null;
    if (unsubscribeNumber != null && !unsubscribeNumber.isBlank()) {
      encryptedAni = encryptPhoneNumber(unsubscribeNumber);
    }

    int offset = (page - 1) * size;
    List<BlockedSender> results =
        blockedSenderMapper.selectBlockedSendersWithSearch(
            storeCodes, senderId, encryptedAni, offset, size);
    int total =
        blockedSenderMapper.countBlockedSendersWithSearch(storeCodes, senderId, encryptedAni);

    // storeCode → userId 매핑 (배치 조회)
    List<String> distinctStoreCodes =
        results.stream().map(BlockedSender::getDtmf1).distinct().collect(Collectors.toList());
    Map<String, String> storeCodeToUserId;
    if (distinctStoreCodes.isEmpty()) {
      storeCodeToUserId = Collections.emptyMap();
    } else {
      Map<String, Map<String, String>> rawMap =
          userMapper.findUserIdsByStoreCodesRaw(distinctStoreCodes);
      storeCodeToUserId = new HashMap<>();
      if (rawMap != null) {
        rawMap.forEach((key, row) -> storeCodeToUserId.put(key, row.getOrDefault("USER_ID", "")));
      }
    }

    List<BlockedSenderResponse> responses =
        results.stream()
            .map(
                bs ->
                    BlockedSenderResponse.from(
                        bs,
                        decryptPhoneNumber(bs.getAni()),
                        storeCodeToUserId.getOrDefault(bs.getDtmf1(), "")))
            .collect(Collectors.toList());

    return PageResponse.of(responses, page, size, total);
  }

  private List<String> resolveStoreCodes(String queryUserIds) {
    if ("ALL".equals(queryUserIds)) {
      return null;
    }
    List<String> userIds = Arrays.asList(queryUserIds.split(","));
    return userMapper.selectStoreCodesByUserIds(userIds);
  }

  /** 수신거부 목록 조회 (전체) */
  @Transactional(readOnly = true)
  public List<BlockedSenderResponse> getAllBlockedNumbers(String storeCode) {
    List<BlockedSender> blockedSenders =
        blockedSenderMapper.selectBlockedSendersByStoreCode(storeCode);

    String storeId = userMapper.findUserIdByStoreCode(storeCode);

    return blockedSenders.stream()
        .map(bs -> BlockedSenderResponse.from(bs, decryptPhoneNumber(bs.getAni()), storeId))
        .collect(Collectors.toList());
  }

  /**
   * 수신거부 삭제 (단건)
   *
   * @param phoneNumber 전화번호 (평문)
   * @param storeCode 상점코드
   * @return 삭제 성공 여부
   */
  @Transactional
  public boolean deleteBlockedNumber(String phoneNumber, String storeCode) {
    String encryptedPhone = encryptPhoneNumber(phoneNumber);
    int deleted = blockedSenderMapper.deleteBlockedSender(encryptedPhone, storeCode);

    if (deleted > 0) {
      log.info(
          "수신거부 삭제 완료: phone={}, storeCode={}", CommonUtils.maskingPhone(phoneNumber), storeCode);
      return true;
    }
    return false;
  }

  /** 수신거부 삭제 (일괄 - 암호화된 번호) */
  @Transactional
  public int deleteBlockedNumbers(List<Map<String, String>> keyList) {
    if (keyList == null || keyList.isEmpty()) {
      return 0;
    }

    int deletedCount = blockedSenderMapper.deleteBlockedSenders(keyList);
    log.info("수신거부 일괄 삭제 완료: {}건", deletedCount);
    return deletedCount;
  }

  /** 수신거부 삭제 (일괄 - 평문/포맷된 번호 → 암호화 후 삭제) */
  @Transactional
  public int deleteBlockedNumbersWithPlainAni(List<Map<String, String>> keyList) {
    if (keyList == null || keyList.isEmpty()) {
      return 0;
    }

    keyList.forEach(
        key -> {
          String ani = key.get("ani");
          if (ani != null) {
            key.put("ani", encryptPhoneNumber(ani));
          }
        });

    return deleteBlockedNumbers(keyList);
  }

  /** 수신거부 삭제 (일괄 - 평문 번호) */
  @Transactional
  public int deleteBlockedNumbersByPhone(List<String> phoneNumbers, String storeCode) {
    if (phoneNumbers == null || phoneNumbers.isEmpty()) {
      return 0;
    }

    List<Map<String, String>> keyList =
        phoneNumbers.stream()
            .map(
                phone -> {
                  Map<String, String> key = new HashMap<>();
                  key.put("ani", encryptPhoneNumber(phone));
                  key.put("dtmf1", storeCode);
                  return key;
                })
            .collect(Collectors.toList());

    return deleteBlockedNumbers(keyList);
  }

  /**
   * 수신거부 여부 확인
   *
   * @param phoneNumber 전화번호 (평문)
   * @param storeCode 상점코드
   * @return 차단 여부
   */
  @Transactional(readOnly = true)
  public boolean isBlocked(String phoneNumber, String storeCode) {
    String encryptedPhone = encryptPhoneNumber(phoneNumber);
    return blockedSenderMapper.isBlockedNumber(encryptedPhone, storeCode);
  }

  /**
   * 수신거부 번호 필터링 (일괄 발송 시)
   *
   * @param storeCode 상점코드
   * @param phoneNumbers 전화번호 목록 (평문)
   * @return 차단된 번호 목록 (평문)
   */
  @Transactional(readOnly = true)
  public List<String> filterBlockedNumbers(String storeCode, List<String> phoneNumbers) {
    if (phoneNumbers == null || phoneNumbers.isEmpty()) {
      return Collections.emptyList();
    }

    // 전화번호 -> 암호화 매핑
    Map<String, String> encryptedToPlain = new HashMap<>();
    for (String phone : phoneNumbers) {
      String encrypted = encryptPhoneNumber(phone);
      encryptedToPlain.put(encrypted, phone);
    }

    // 차단된 암호화 번호 조회
    List<String> blockedEncrypted =
        blockedSenderMapper.selectBlockedNumbers(
            storeCode, new ArrayList<>(encryptedToPlain.keySet()));

    // 평문으로 변환하여 반환
    return blockedEncrypted.stream()
        .map(encryptedToPlain::get)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  /**
   * 발송 가능한 번호 필터링 (차단 번호 제외)
   *
   * @param storeCode 상점코드
   * @param phoneNumbers 전화번호 목록 (평문)
   * @return 발송 가능한 번호 목록 (평문)
   */
  @Transactional(readOnly = true)
  public List<String> getAvailableNumbers(String storeCode, List<String> phoneNumbers) {
    List<String> blockedNumbers = filterBlockedNumbers(storeCode, phoneNumbers);
    Set<String> blockedSet = new HashSet<>(blockedNumbers);

    return phoneNumbers.stream()
        .filter(phone -> !blockedSet.contains(phone))
        .collect(Collectors.toList());
  }

  /** 상점코드별 수신거부 건수 조회 */
  @Transactional(readOnly = true)
  public int countBlockedNumbers(String storeCode) {
    return blockedSenderMapper.countBlockedSendersByStoreCode(storeCode);
  }

  /** 상점코드 유효성 검사 */
  @Transactional(readOnly = true)
  public boolean isValidStoreCode(String storeCode) {
    if (storeCode == null || storeCode.isBlank()) {
      return false;
    }
    String corpName = blockedSenderMapper.selectCorpNameByStoreCode(storeCode);
    return corpName != null && !corpName.isBlank();
  }

  /** 상점코드로 회사명 조회 */
  @Transactional(readOnly = true)
  public String getCorpNameByStoreCode(String storeCode) {
    return blockedSenderMapper.selectCorpNameByStoreCode(storeCode);
  }

  // ==================== Private Methods ====================

  /** 전화번호 유효성 검사 (휴대폰 번호) */
  private boolean isValidPhoneNumber(String phoneNumber) {
    if (phoneNumber == null || phoneNumber.isBlank()) {
      return false;
    }
    // 하이픈 제거 후 검사
    String cleaned = phoneNumber.replaceAll("-", "");
    return cleaned.matches("^01[016789]\\d{7,8}$");
  }

  /** 전화번호 암호화 (AES256 + Base64) */
  private String encryptPhoneNumber(String phoneNumber) {
    if (phoneNumber == null) {
      return null;
    }
    // 하이픈 제거
    String cleaned = phoneNumber.replaceAll("-", "");
    String encrypted = CryptoUtils.encryptAES256(cleaned);
    if (encrypted.isEmpty()) {
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "전화번호 암호화 실패");
    }
    return CryptoUtils.encodeBase64(encrypted);
  }

  /** 전화번호 복호화 */
  private String decryptPhoneNumber(String encryptedPhone) {
    if (encryptedPhone == null) {
      return null;
    }
    return CryptoUtils.decryptAES256(CryptoUtils.decodeBase64(encryptedPhone));
  }

  /** 현재 시간 문자열 (yyyyMMddHHmmss) */
  private String getCurrentTimeString() {
    return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
  }
}
