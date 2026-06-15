package kr.wisead.domain.profanity.service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.profanity.entity.ProfanityBlockLog;
import kr.wisead.domain.profanity.entity.ProhibitedWord;
import kr.wisead.mapper.primary.ProfanityBlockLogMapper;
import kr.wisead.mapper.primary.ProhibitedWordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 금칙어 실시간 필터링 서비스.
 *
 * <h3>캐시 전략</h3>
 *
 * <ul>
 *   <li>초기 로드: {@link #init()} — {@code @PostConstruct}로 애플리케이션 구동 시 활성 금칙어 전체 로드.
 *   <li>갱신: {@link #refreshIfChanged()} — 5초마다 {@code SELECT MAX(VERSION)} 폴링. VERSION이 변경된 경우에만 캐시
 *       재로드.
 *   <li>즉시 무효화: {@link #invalidate()} — {@link ProhibitedWordService} CUD 완료 직후 호출. 현재 인스턴스 캐시를 즉시
 *       재로드.
 * </ul>
 *
 * <h3>원자성</h3>
 *
 * <p>캐시 갱신은 새 {@link Map} 인스턴스를 로컬에서 빌드한 후 {@code volatile} 참조를 1회 교체하여 published 시점에 부분 노출 없이 swap
 * 된다. {@code validate()} 가 갱신 중에 호출되어도 항상 일관된 스냅샷을 읽는다.
 *
 * <h3>멀티 인스턴스 운영 시 staleness</h3>
 *
 * <p>단일 인스턴스 가정. 멀티 인스턴스 운영 시 최대 5초 stale window 존재. Redis pub/sub 기반 즉시 동기화는 운영 토폴로지 확정 후 별도 적용 예정
 * (PR2 scope 외).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfanityFilterService {

  private final ProhibitedWordMapper prohibitedWordMapper;
  private final ProfanityBlockLogMapper profanityBlockLogMapper;

  /** 활성 금칙어 캐시: word → ProhibitedWord. 갱신 시 새 Map 인스턴스로 atomic reference swap. */
  private volatile Map<String, ProhibitedWord> cache = Map.of();

  /** 마지막으로 확인한 MAX(VERSION) */
  private volatile long lastVersion = -1L;

  /** 스키마 미생성 (마이그레이션 전) 상태로 인한 캐시 로드 실패가 WARN 으로 1회 기록되었는지. */
  private volatile boolean schemaMissingLogged = false;

  // ─────────────────────────────────────────────────────────────
  // 초기화 / 캐시 관리
  // ─────────────────────────────────────────────────────────────

  /** 애플리케이션 구동 시 초기 캐시 로드. */
  @PostConstruct
  public void init() {
    loadCache();
    log.info("금칙어 캐시 초기 로드 완료 - {}건", cache.size());
  }

  /**
   * 5초마다 VERSION 폴링. VERSION이 변경된 경우에만 캐시 재로드.
   *
   * <p>멀티 인스턴스: 각 인스턴스가 독립적으로 폴링 → 최대 5초 stale.
   */
  @Scheduled(fixedRate = 5000)
  public void refreshIfChanged() {
    try {
      Long maxVersion = prohibitedWordMapper.selectMaxVersion();
      long current = maxVersion != null ? maxVersion : 0L;
      if (current != lastVersion) {
        loadCache();
        log.debug("금칙어 캐시 갱신 - 이전 VERSION: {}, 신규 VERSION: {}", lastVersion, current);
        lastVersion = current;
      }
    } catch (Exception e) {
      handleCacheError("금칙어 캐시 폴링", e);
    }
  }

  /**
   * 캐시 즉시 무효화 및 재로드.
   *
   * <p>{@link ProhibitedWordService} CUD 직후 호출. 단일 인스턴스에서는 즉시 반영; 멀티 인스턴스의 다른 노드는 5초 폴링 후 반영.
   */
  public void invalidate() {
    loadCache();
    log.debug("금칙어 캐시 즉시 무효화/재로드 완료 - {}건", cache.size());
  }

  // ─────────────────────────────────────────────────────────────
  // 검증 진입점
  // ─────────────────────────────────────────────────────────────

  /**
   * 임시저장(DRAFT) 금칙어 검증.
   *
   * @param content 검증할 메시지 내용
   * @param userId 요청 사용자 SEQ
   * @param ip 요청 IP
   * @param userAgent 요청 User-Agent
   * @param messageId 관련 메시지 ID (없으면 null)
   */
  public void validateForDraft(
      String content, Integer userId, String ip, String userAgent, Long messageId) {
    validate(content, "DRAFT", userId, ip, userAgent, messageId, null);
  }

  /**
   * 발송(SEND) 금칙어 검증.
   *
   * @param content 검증할 메시지 내용
   * @param userId 요청 사용자 SEQ
   * @param ip 요청 IP
   * @param userAgent 요청 User-Agent
   * @param messageId 관련 메시지 ID (없으면 null)
   * @param recipientCount 발송 대상 수
   */
  public void validateForSend(
      String content,
      Integer userId,
      String ip,
      String userAgent,
      Long messageId,
      Integer recipientCount) {
    validate(content, "SEND", userId, ip, userAgent, messageId, recipientCount);
  }

  /**
   * 예약 발송(SCHEDULE) 금칙어 검증.
   *
   * @param content 검증할 메시지 내용
   * @param userId 요청 사용자 SEQ
   * @param ip 요청 IP
   * @param userAgent 요청 User-Agent
   * @param messageId 관련 메시지 ID (없으면 null)
   * @param recipientCount 발송 대상 수
   */
  public void validateForSchedule(
      String content,
      Integer userId,
      String ip,
      String userAgent,
      Long messageId,
      Integer recipientCount) {
    validate(content, "SCHEDULE", userId, ip, userAgent, messageId, recipientCount);
  }

  // ─────────────────────────────────────────────────────────────
  // 핵심 검증 로직
  // ─────────────────────────────────────────────────────────────

  /**
   * 금칙어 완전일치(contains) 검사.
   *
   * <p>캐시에 있는 모든 활성 금칙어에 대해 {@code content.contains(word)} 검사. 매칭 시 {@link ProfanityBlockLog} 기록 후
   * {@link BusinessException}(ACCESS_DENIED) throw.
   *
   * @param content 검증 대상 내용
   * @param source 발생 경로 (DRAFT/SEND/SCHEDULE)
   * @param userId 요청 사용자 SEQ
   * @param ip 요청 IP
   * @param userAgent 요청 User-Agent
   * @param messageId 관련 메시지 ID
   * @param recipientCount 발송 대상 수
   */
  public void validate(
      String content,
      String source,
      Integer userId,
      String ip,
      String userAgent,
      Long messageId,
      Integer recipientCount) {
    if (content == null || content.isBlank()) {
      return;
    }

    Map<String, ProhibitedWord> snapshot = cache;
    for (ProhibitedWord pw : snapshot.values()) {
      if (content.contains(pw.getWord())) {
        // 차단 로그 기록
        logBlock(content, pw.getWord(), source, userId, ip, userAgent, messageId, recipientCount);
        // 매칭 단어 정보를 data 에 담아 컨트롤러에서 FE 응답으로 전달 가능
        throw new BusinessException(
            ErrorCode.ACCESS_DENIED,
            "입력 불가 단어 포함: " + pw.getWord(),
            new ProfanityMatchData(pw.getWord(), source));
      }
    }
  }

  // ─────────────────────────────────────────────────────────────
  // Private
  // ─────────────────────────────────────────────────────────────

  /** 활성 금칙어를 새 Map 인스턴스로 빌드한 뒤 atomic reference swap. 부분 노출 window 없음. */
  private void loadCache() {
    try {
      List<ProhibitedWord> words = prohibitedWordMapper.selectAllActive();
      Long maxVersion = prohibitedWordMapper.selectMaxVersion();

      Map<String, ProhibitedWord> newCache = new HashMap<>(Math.max(16, words.size() * 2));
      for (ProhibitedWord pw : words) {
        newCache.put(pw.getWord(), pw);
      }
      cache = newCache;
      lastVersion = maxVersion != null ? maxVersion : 0L;

      if (schemaMissingLogged) {
        log.info("금칙어 캐시 정상화 - 테이블 로드 성공 ({}건)", newCache.size());
        schemaMissingLogged = false;
      }
    } catch (Exception e) {
      handleCacheError("금칙어 캐시 로드", e);
    }
  }

  /**
   * 캐시 로드/폴링 실패 시 로그 다운그레이드.
   *
   * <p>{@code prohibited_word} 테이블 미생성(마이그레이션 전) 상태로 인한 실패는 최초 1회만 WARN, 이후는 DEBUG. 그 외 오류는 ERROR +
   * stack trace.
   */
  private void handleCacheError(String context, Exception e) {
    if (isSchemaMissing(e)) {
      if (!schemaMissingLogged) {
        log.warn("{} 실패 - prohibited_word 테이블 미생성 (마이그레이션 필요): {}", context, e.getMessage());
        schemaMissingLogged = true;
      } else {
        log.debug("{} skip - schema 미생성 상태 유지", context);
      }
    } else {
      log.error("{} 실패: {}", context, e.getMessage(), e);
    }
  }

  /** SQL 예외 체인에서 "doesn't exist" 메시지 발견 시 스키마 미생성으로 판단. */
  private static boolean isSchemaMissing(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      String msg = t.getMessage();
      if (msg != null && msg.contains("doesn't exist")) {
        return true;
      }
    }
    return false;
  }

  private void logBlock(
      String content,
      String matchedWord,
      String source,
      Integer userId,
      String ip,
      String userAgent,
      Long messageId,
      Integer recipientCount) {
    try {
      String snippet = content.length() > 500 ? content.substring(0, 500) : content;
      ProfanityBlockLog blockLog =
          ProfanityBlockLog.builder()
              .userId(userId)
              .matchedWord(matchedWord)
              .contentSnippet(snippet)
              .fullContent(content)
              .source(source)
              .messageId(messageId)
              .recipientCount(recipientCount)
              .ip(ip)
              .userAgent(userAgent)
              .build();
      profanityBlockLogMapper.insert(blockLog);
    } catch (Exception e) {
      log.warn("금칙어 차단 로그 기록 실패 (차단은 계속 진행): {}", e.getMessage());
    }
  }

  // ─────────────────────────────────────────────────────────────
  // 매칭 데이터 레코드 (FE 응답용)
  // ─────────────────────────────────────────────────────────────

  /** 금칙어 매칭 시 BusinessException.data 에 담기는 페이로드. FE 에서 팝업 표시에 사용. */
  public record ProfanityMatchData(String matchedWord, String source) {}
}
