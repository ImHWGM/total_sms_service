package kr.wisead.domain.history.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.common.util.PhoneUtils;
import kr.wisead.domain.ars.dto.BlockedSenderResponse;
import kr.wisead.domain.ars.service.ArsService;
import kr.wisead.domain.history.dto.SendHistoryResponse;
import kr.wisead.domain.history.dto.SendHistorySearchRequest;
import kr.wisead.domain.history.entity.SendHistory;
import kr.wisead.mapper.sms.SendHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 발송 이력 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SendHistoryService {

  /** ym 미지정 시 seq 를 역순 탐색할 최근 월 테이블 수 */
  private static final int UNMASK_FALLBACK_MONTHS = 13;

  private final SendHistoryMapper sendHistoryMapper;
  private final ArsService arsService;

  @Value("${api.base.url:}")
  private String apiBaseUrl;

  /** 발송 이력 목록 조회 (여러 월 테이블 조회) */
  @Transactional(readOnly = true)
  public PageResponse<SendHistoryResponse> getHistoryList(SendHistorySearchRequest request) {
    List<String> tables = getTableNames(request.getStartDate(), request.getEndDate());

    log.info(
        "[발송이력조회] 검색조건 - startDate={}, endDate={}, type={}, keyword={}, sendResult={}, userId={},"
            + " page={}, size={}",
        request.getStartDate(),
        request.getEndDate(),
        request.getType(),
        request.getKeyword(),
        request.getSendResult(),
        request.getUserId(),
        request.getPage(),
        request.getSize());
    log.info("[발송이력조회] 조회대상 테이블: {}", tables);

    if (tables.isEmpty()) {
      log.warn("[발송이력조회] 조회 대상 테이블이 없습니다.");
      return PageResponse.of(Collections.emptyList(), request.getPage(), request.getSize(), 0);
    }

    Map<String, Object> params = buildQueryParams(request);

    List<SendHistory> allResults = new ArrayList<>();
    int totalCount = 0;

    // 각 테이블에서 조회 (최신 테이블부터)
    for (String tableName : tables) {
      try {
        int count = sendHistoryMapper.selectCount(tableName, params);
        log.info("[발송이력조회] 테이블={}, 건수={}", tableName, count);
        totalCount += count;
      } catch (Exception e) {
        log.warn("테이블 {} 조회 중 오류 발생: {}", tableName, e.getMessage());
      }
    }
    log.info("[발송이력조회] 전체 건수: {}", totalCount);

    // 페이징 처리 - 첫 번째 테이블부터 조회
    int remaining = request.getSize();
    int skipCount = request.getOffset();

    for (String tableName : tables) {
      if (remaining <= 0) break;

      try {
        int tableCount = sendHistoryMapper.selectCount(tableName, params);

        if (skipCount >= tableCount) {
          skipCount -= tableCount;
          continue;
        }

        Map<String, Object> tableParams = new HashMap<>(params);
        tableParams.put("offset", skipCount);
        tableParams.put("size", remaining);

        List<SendHistory> tableResults = sendHistoryMapper.selectList(tableName, tableParams);
        allResults.addAll(tableResults);

        remaining -= tableResults.size();
        skipCount = 0;
      } catch (Exception e) {
        log.warn("테이블 {} 데이터 조회 중 오류: {}", tableName, e.getMessage());
      }
    }

    List<SendHistoryResponse> responses =
        allResults.stream().map(SendHistoryResponse::from).toList();

    if (apiBaseUrl != null && !apiBaseUrl.isEmpty()) {
      responses.forEach(r -> r.withFullImageUrls(apiBaseUrl));
    }

    return PageResponse.of(responses, request.getPage(), request.getSize(), totalCount);
  }

  /** 발송 이력 전체 조회 (엑셀 다운로드용) */
  @Transactional(readOnly = true)
  public List<SendHistoryResponse> getHistoryListForDownload(SendHistorySearchRequest request) {
    List<String> tables = getTableNames(request.getStartDate(), request.getEndDate());
    Map<String, Object> params = buildQueryParams(request);

    List<SendHistory> allResults = new ArrayList<>();

    for (String tableName : tables) {
      try {
        List<SendHistory> tableResults = sendHistoryMapper.selectAllForDownload(tableName, params);
        allResults.addAll(tableResults);
      } catch (Exception e) {
        log.warn("테이블 {} 다운로드 조회 중 오류: {}", tableName, e.getMessage());
      }
    }

    // 시간순 정렬
    allResults.sort(
        (a, b) -> {
          if (b.getRequestTime() == null) return -1;
          if (a.getRequestTime() == null) return 1;
          return b.getRequestTime().compareTo(a.getRequestTime());
        });

    return allResults.stream().map(SendHistoryResponse::from).toList();
  }

  /**
   * seq(MSEQ) 로 원본 수신번호 조회.
   *
   * @param seq 발송 이력 seq (SendHistoryResponse.seq / MSEQ)
   * @param ym 발송월 힌트 "yyyyMM" (선택). 유효하면 해당 월 단일 테이블만 조회, 없으면 최근 월 역순 탐색
   * @param queryUserId 권한 범위 (콤마 구분 발신 userId 또는 "ALL")
   * @return 하이픈 포맷된 원본 수신번호 (예: 010-1234-5678)
   * @throws BusinessException 권한 범위 내에서 seq 를 찾지 못하면 RESOURCE_NOT_FOUND (404)
   */
  @Transactional(readOnly = true)
  public String getUnmaskedReceiver(Long seq, String ym, String queryUserId) {
    for (String tableName : resolveCandidateTables(ym)) {
      try {
        SendHistory found = sendHistoryMapper.selectBySeq(tableName, seq, queryUserId);
        if (found != null) {
          log.info("[원본조회] seq={}, table={}", seq, tableName);
          return PhoneUtils.format(found.getDstAddr());
        }
      } catch (Exception e) {
        // 존재하지 않는 월 테이블 등은 무시하고 다음 후보로 진행
        log.warn("[원본조회] 테이블 {} 조회 중 오류(무시): {}", tableName, e.getMessage());
      }
    }
    throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "발송 이력을 찾을 수 없습니다.");
  }

  /** 수신거부 목록 조회 (ArsService 위임) */
  @Transactional(readOnly = true)
  public PageResponse<BlockedSenderResponse> getBlockedSenders(
      String storeCode, int page, int size) {
    return arsService.getBlockedSenders(storeCode, page, size);
  }

  /** 수신거부 검색 조회 (권한 기반, ArsService 위임) */
  @Transactional(readOnly = true)
  public PageResponse<BlockedSenderResponse> searchBlockedSenders(
      String queryUserIds, String senderId, String unsubscribeNumber, int page, int size) {
    return arsService.searchBlockedSenders(queryUserIds, senderId, unsubscribeNumber, page, size);
  }

  /** 수신거부 삭제 (평문 ANI 암호화 후 삭제, ArsService 위임) */
  @Transactional
  public int deleteBlockedSenders(List<Map<String, String>> keyList) {
    return arsService.deleteBlockedSendersWithPlainAni(keyList);
  }

  /** 권한 범위 내 store code만 필터링 */
  public List<Map<String, String>> filterKeyListByPermission(
      List<Map<String, String>> keyList, String queryUserIds) {
    if ("ALL".equals(queryUserIds)) {
      return keyList;
    }

    List<String> userIds = Arrays.asList(queryUserIds.split(","));
    Set<String> allowedStoreCodes = new HashSet<>(arsService.getStoreCodesByUserIds(userIds));

    return keyList.stream()
        .filter(key -> allowedStoreCodes.contains(key.get("dtmf1")))
        .collect(Collectors.toList());
  }

  /** 상태 코드를 명칭으로 변환 */
  public String translateStatus(Integer stat) {
    return SendHistoryResponse.translateStatus(stat);
  }

  // ==================== Private Methods ====================

  /** 원본조회 대상 후보 테이블 결정: ym 유효 시 단일 테이블, 아니면 최근 월 역순 목록 */
  private List<String> resolveCandidateTables(String ym) {
    if (ym != null && ym.matches("\\d{6}")) {
      return List.of("msg_result_" + ym);
    }
    // ym 미지정/형식오류 시 최근 UNMASK_FALLBACK_MONTHS 개월을 당월부터 역순 탐색
    DateTimeFormatter yearMonth = DateTimeFormatter.ofPattern("yyyyMM");
    LocalDate month = LocalDate.now().withDayOfMonth(1);
    List<String> tables = new ArrayList<>();
    for (int i = 0; i < UNMASK_FALLBACK_MONTHS; i++) {
      tables.add("msg_result_" + month.format(yearMonth));
      month = month.minusMonths(1);
    }
    return tables;
  }

  /** 검색 기간에 해당하는 테이블 명 목록 생성 */
  private List<String> getTableNames(String startDateStr, String endDateStr) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    LocalDate startDate =
        startDateStr != null && !startDateStr.isEmpty()
            ? LocalDate.parse(startDateStr, formatter)
            : LocalDate.now().minusDays(15);

    LocalDate endDate =
        endDateStr != null && !endDateStr.isEmpty()
            ? LocalDate.parse(endDateStr, formatter)
            : LocalDate.now().plusDays(30);

    List<String> tables = new ArrayList<>();
    LocalDate tableStart = startDate.withDayOfMonth(1);
    LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);

    // 미래 테이블은 제외
    if (tableStart.isAfter(currentMonth)) {
      return tables;
    }

    LocalDate tableEnd = endDate.withDayOfMonth(1);
    if (tableEnd.isAfter(currentMonth)) {
      tableEnd = currentMonth;
    }

    // 최신 월부터 정렬
    while (!tableEnd.isBefore(tableStart)) {
      tables.add("msg_result_" + tableEnd.format(DateTimeFormatter.ofPattern("yyyyMM")));
      tableEnd = tableEnd.minusMonths(1);
    }

    return tables;
  }

  /** 쿼리 파라미터 생성 */
  private Map<String, Object> buildQueryParams(SendHistorySearchRequest request) {
    Map<String, Object> params = new HashMap<>();
    params.put("startDate", request.getStartDate());
    params.put("endDate", request.getEndDate());
    params.put("type", request.getType());
    params.put("keyword", request.getKeyword());
    params.put("sendResult", request.getSendResult());
    params.put("sendFailure", request.getSendFailure()); // [legacy] FE 배포 후 제거
    params.put("userId", request.getUserId());
    params.put("offset", request.getOffset());
    params.put("size", request.getSize());
    return params;
  }
}
