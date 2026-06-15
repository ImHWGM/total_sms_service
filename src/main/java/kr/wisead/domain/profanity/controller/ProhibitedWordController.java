package kr.wisead.domain.profanity.controller;

import java.time.LocalDateTime;
import java.util.List;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.profanity.entity.ProhibitedWord;
import kr.wisead.domain.profanity.entity.ProhibitedWordHistory;
import kr.wisead.domain.profanity.service.ProhibitedWordService;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.UserMapper;
import kr.wisead.security.jwt.CurrentUser;
import kr.wisead.security.jwt.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 금칙어 관리 Controller — 최고관리자(level == 99) 전용 */
@Slf4j
@RestController
@RequestMapping("/api/admin/prohibited-words")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ProhibitedWordController {

  private final ProhibitedWordService prohibitedWordService;
  private final UserMapper userMapper;

  // ─────────────────────────────────────────────────────────────
  // 목록 조회
  // ─────────────────────────────────────────────────────────────

  /** GET /api/admin/prohibited-words — 전체 금칙어 목록 */
  @GetMapping
  public ApiResponse<List<ProhibitedWord>> getAll(@CurrentUser JwtPrincipal user) {
    requireSuperAdmin(user);
    return ApiResponse.success(prohibitedWordService.findAll());
  }

  // ─────────────────────────────────────────────────────────────
  // 등록
  // ─────────────────────────────────────────────────────────────

  /** POST /api/admin/prohibited-words — 금칙어 등록 */
  @PostMapping
  public ApiResponse<ProhibitedWord> create(
      @CurrentUser JwtPrincipal user, @RequestBody CreateRequest request) {
    Integer actorLevel = requireSuperAdmin(user);
    ProhibitedWord created =
        prohibitedWordService.create(
            request.word(), request.category(), request.reason(), user.seq(), actorLevel);
    return ApiResponse.success(created);
  }

  // ─────────────────────────────────────────────────────────────
  // 수정
  // ─────────────────────────────────────────────────────────────

  /** PUT /api/admin/prohibited-words/{seq} — 금칙어 수정 */
  @PutMapping("/{seq}")
  public ApiResponse<ProhibitedWord> update(
      @CurrentUser JwtPrincipal user, @PathVariable Long seq, @RequestBody UpdateRequest request) {
    Integer actorLevel = requireSuperAdmin(user);
    ProhibitedWord updated =
        prohibitedWordService.update(
            seq,
            request.word(),
            request.category(),
            request.active(),
            request.reason(),
            user.seq(),
            actorLevel);
    return ApiResponse.success(updated);
  }

  // ─────────────────────────────────────────────────────────────
  // 삭제
  // ─────────────────────────────────────────────────────────────

  /** DELETE /api/admin/prohibited-words/{seq} — 금칙어 삭제 (body에 reason 포함) */
  @DeleteMapping("/{seq}")
  public ApiResponse<Void> delete(
      @CurrentUser JwtPrincipal user, @PathVariable Long seq, @RequestBody DeleteRequest request) {
    Integer actorLevel = requireSuperAdmin(user);
    prohibitedWordService.delete(seq, request.reason(), user.seq(), actorLevel);
    return ApiResponse.success();
  }

  // ─────────────────────────────────────────────────────────────
  // 전체 이력 조회 (페이징)
  // ─────────────────────────────────────────────────────────────

  /**
   * GET /api/admin/prohibited-words/history — 전체 변경 이력 (페이징)
   *
   * @param from 조회 시작 일시 (ISO 형식, 선택)
   * @param to 조회 종료 일시 (ISO 형식, 선택)
   * @param page 페이지 번호 (1부터, 기본 1)
   * @param size 페이지 크기 (기본 20)
   */
  @GetMapping("/history")
  public ApiResponse<PageResponse<ProhibitedWordHistory>> getHistory(
      @CurrentUser JwtPrincipal user,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime to,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    requireSuperAdmin(user);
    int offset = (page - 1) * size;
    List<ProhibitedWordHistory> items =
        prohibitedWordService.findHistory(null, from, to, offset, size);
    long total = prohibitedWordService.countHistory(null, from, to);
    return ApiResponse.success(PageResponse.of(items, page, size, total));
  }

  // ─────────────────────────────────────────────────────────────
  // 특정 금칙어 이력 조회
  // ─────────────────────────────────────────────────────────────

  /** GET /api/admin/prohibited-words/history/{wordId} — 특정 금칙어 변경 이력 (페이징) */
  @GetMapping("/history/{wordId}")
  public ApiResponse<PageResponse<ProhibitedWordHistory>> getWordHistory(
      @CurrentUser JwtPrincipal user,
      @PathVariable Long wordId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          LocalDateTime to,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    requireSuperAdmin(user);
    int offset = (page - 1) * size;
    List<ProhibitedWordHistory> items =
        prohibitedWordService.findHistory(wordId, from, to, offset, size);
    long total = prohibitedWordService.countHistory(wordId, from, to);
    return ApiResponse.success(PageResponse.of(items, page, size, total));
  }

  // ─────────────────────────────────────────────────────────────
  // Private helpers
  // ─────────────────────────────────────────────────────────────

  /**
   * 최고관리자 체크 후 actorLevel 반환. Service 에서도 재검증하지만 controller 에서 조기 차단.
   *
   * <p>ChargeBonusEventService:193-197 과 동일 패턴 — level == 99 strict equality.
   */
  private int requireSuperAdmin(JwtPrincipal user) {
    int level =
        userMapper
            .findBySeq(user.seq())
            .map(User::getUserLevel)
            .orElse(0);
    if (level != 99) {
      throw new BusinessException(ErrorCode.ACCESS_DENIED, "최고관리자 권한 필요");
    }
    return level;
  }

  // ─────────────────────────────────────────────────────────────
  // Request records
  // ─────────────────────────────────────────────────────────────

  public record CreateRequest(String word, String category, String reason) {}

  public record UpdateRequest(String word, String category, Boolean active, String reason) {}

  public record DeleteRequest(String reason) {}
}
