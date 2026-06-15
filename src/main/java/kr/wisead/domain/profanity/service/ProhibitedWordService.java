package kr.wisead.domain.profanity.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.profanity.entity.ProhibitedWord;
import kr.wisead.domain.profanity.entity.ProhibitedWordHistory;
import kr.wisead.mapper.primary.ProhibitedWordHistoryMapper;
import kr.wisead.mapper.primary.ProhibitedWordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 금칙어 CRUD 서비스.
 *
 * <p>최고관리자(userLevel == 99) 만 CUD 가능. 권한 검증은 각 CUD 메서드 진입 시 즉시 수행한다 (ChargeBonusEventService
 * validateSuperAdmin 동일 패턴 — level == 99 strict equality).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProhibitedWordService {

  private final ProhibitedWordMapper prohibitedWordMapper;
  private final ProhibitedWordHistoryMapper prohibitedWordHistoryMapper;
  private final ProfanityFilterService profanityFilterService;

  /** Spring Boot 자동 구성 ObjectMapper (JavaTimeModule 포함). */
  private final ObjectMapper objectMapper;

  // ─────────────────────────────────────────────────────────────
  // CUD
  // ─────────────────────────────────────────────────────────────

  /**
   * 금칙어 등록.
   *
   * @param word 금칙어
   * @param category 분류
   * @param reason 등록 사유 (필수)
   * @param actorId 처리 관리자 user.SEQ
   * @param actorLevel 처리 관리자 userLevel (99 이어야 함)
   */
  @Transactional
  public ProhibitedWord create(
      String word, String category, String reason, Integer actorId, Integer actorLevel) {
    validateSuperAdmin(actorLevel);
    if (reason == null || reason.isBlank()) {
      throw new BusinessException(ErrorCode.MISSING_INPUT_VALUE, "등록 사유는 필수입니다.");
    }

    ProhibitedWord entity =
        ProhibitedWord.builder().word(word).category(category).active(true).reason(reason).build();
    prohibitedWordMapper.insert(entity);
    prohibitedWordMapper.bumpVersion(entity.getSeq());

    writeHistory(entity.getSeq(), "ADD", null, entity, actorId, reason);
    profanityFilterService.invalidate();

    log.info("금칙어 등록 - seq: {}, word: {}, actorId: {}", entity.getSeq(), word, actorId);
    return entity;
  }

  /**
   * 금칙어 수정.
   *
   * @param seq 대상 금칙어 SEQ
   * @param word 수정할 금칙어
   * @param category 수정할 분류
   * @param active 활성 여부
   * @param reason 수정 사유 (필수)
   * @param actorId 처리 관리자 user.SEQ
   * @param actorLevel 처리 관리자 userLevel (99 이어야 함)
   */
  @Transactional
  public ProhibitedWord update(
      Long seq,
      String word,
      String category,
      Boolean active,
      String reason,
      Integer actorId,
      Integer actorLevel) {
    validateSuperAdmin(actorLevel);
    if (reason == null || reason.isBlank()) {
      throw new BusinessException(ErrorCode.MISSING_INPUT_VALUE, "수정 사유는 필수입니다.");
    }

    ProhibitedWord prev =
        prohibitedWordMapper
            .selectBySeq(seq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "금칙어를 찾을 수 없습니다."));

    ProhibitedWord updated =
        ProhibitedWord.builder()
            .seq(seq)
            .word(word)
            .category(category)
            .active(active)
            .reason(reason)
            .build();
    prohibitedWordMapper.update(updated);
    prohibitedWordMapper.bumpVersion(seq);

    writeHistory(seq, "MODIFY", prev, updated, actorId, reason);
    profanityFilterService.invalidate();

    log.info("금칙어 수정 - seq: {}, actorId: {}", seq, actorId);
    return updated;
  }

  /**
   * 금칙어 삭제.
   *
   * @param seq 대상 금칙어 SEQ
   * @param reason 삭제 사유 (필수)
   * @param actorId 처리 관리자 user.SEQ
   * @param actorLevel 처리 관리자 userLevel (99 이어야 함)
   */
  @Transactional
  public void delete(Long seq, String reason, Integer actorId, Integer actorLevel) {
    validateSuperAdmin(actorLevel);
    if (reason == null || reason.isBlank()) {
      throw new BusinessException(ErrorCode.MISSING_INPUT_VALUE, "삭제 사유는 필수입니다.");
    }

    ProhibitedWord prev =
        prohibitedWordMapper
            .selectBySeq(seq)
            .orElseThrow(
                () -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "금칙어를 찾을 수 없습니다."));

    writeHistory(seq, "DELETE", prev, null, actorId, reason);
    prohibitedWordMapper.delete(seq);
    profanityFilterService.invalidate();

    log.info("금칙어 삭제 - seq: {}, actorId: {}", seq, actorId);
  }

  // ─────────────────────────────────────────────────────────────
  // 조회
  // ─────────────────────────────────────────────────────────────

  /** 전체 금칙어 목록 (관리 화면용). */
  public List<ProhibitedWord> findAll() {
    return prohibitedWordMapper.selectAll();
  }

  /**
   * 금칙어 변경 이력 조회 (페이징).
   *
   * @param wordId 특정 금칙어 SEQ (null 시 전체)
   * @param from 조회 시작 일시
   * @param to 조회 종료 일시
   * @param offset 페이징 offset
   * @param limit 페이징 limit
   */
  public List<ProhibitedWordHistory> findHistory(
      Long wordId, LocalDateTime from, LocalDateTime to, int offset, int limit) {
    return prohibitedWordHistoryMapper.selectByFilter(wordId, from, to, offset, limit);
  }

  /** 이력 총 건수. */
  public long countHistory(Long wordId, LocalDateTime from, LocalDateTime to) {
    return prohibitedWordHistoryMapper.countByFilter(wordId, from, to);
  }

  // ─────────────────────────────────────────────────────────────
  // Private
  // ─────────────────────────────────────────────────────────────

  /**
   * 최고관리자 권한 검증.
   *
   * <p>ChargeBonusEventService:193-197 과 동일 패턴 — userLevel == 99 strict equality.
   */
  private void validateSuperAdmin(Integer actorLevel) {
    if (actorLevel == null || actorLevel != 99) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED, "최고관리자 권한 필요");
    }
  }

  /** 이력 기록. JSON 직렬화 실패 시 빈 문자열로 graceful degradation. */
  private void writeHistory(
      Long wordId,
      String action,
      ProhibitedWord prev,
      ProhibitedWord next,
      Integer actorId,
      String reason) {
    ProhibitedWordHistory history =
        ProhibitedWordHistory.builder()
            .wordId(wordId)
            .action(action)
            .prevValue(toJson(prev))
            .newValue(toJson(next))
            .actorId(actorId)
            .reason(reason)
            .build();
    prohibitedWordHistoryMapper.insert(history);
  }

  private String toJson(Object obj) {
    if (obj == null) return null;
    try {
      return objectMapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      log.warn("금칙어 JSON 직렬화 실패: {}", e.getMessage());
      return "{}";
    }
  }
}
