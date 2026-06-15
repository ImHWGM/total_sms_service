package kr.wisead.domain.admin.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ApiResponse;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.audit.entity.AuditEvent;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.AuditEventMapper;
import kr.wisead.mapper.primary.UserMapper;
import kr.wisead.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 감사 이벤트 조회 API (관리자 전용).
 *
 * <p>GET /api/admin/audit-events — 필터: eventType, userId, from, to, page, size. 권한: userLevel >= 90
 * (ROLE_ADMIN).
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/audit-events")
@RequiredArgsConstructor
public class AuditEventController {

  private final AuditEventMapper auditEventMapper;
  private final JwtTokenProvider jwtTokenProvider;
  private final UserMapper userMapper;

  private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

  /**
   * 감사 이벤트 목록 조회 (페이징).
   *
   * @param eventType 이벤트 유형 필터 (선택)
   * @param userId 대상 사용자 seq 필터 (선택)
   * @param from 조회 시작 일시 ISO-8601 (선택, 예: 2026-01-01T00:00:00)
   * @param to 조회 종료 일시 ISO-8601 (선택)
   * @param page 페이지 번호 (0-based, 기본 0)
   * @param size 페이지 크기 (기본 20)
   * @param token Authorization 헤더
   */
  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ApiResponse<PageResponse<AuditEvent>> getAuditEvents(
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) Integer userId,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestHeader("Authorization") String token) {

    validateAdminLevel(token);

    LocalDateTime fromDt = from != null ? LocalDateTime.parse(from, DT_FMT) : null;
    LocalDateTime toDt = to != null ? LocalDateTime.parse(to, DT_FMT) : null;

    int offset = page * size;
    List<AuditEvent> content =
        auditEventMapper.selectByFilter(eventType, userId, fromDt, toDt, offset, size);
    long total = auditEventMapper.countByFilter(eventType, userId, fromDt, toDt);

    log.info("[감사이벤트 조회] eventType={}, userId={}, total={}", eventType, userId, total);

    return ApiResponse.success(PageResponse.of(content, page, size, total));
  }

  // ==================== Private Helpers ====================

  /** Authorization 헤더에서 JWT를 추출하고 관리자 레벨(>= 90)을 검증한다. */
  private void validateAdminLevel(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    String token = authHeader.substring(7);
    if (!jwtTokenProvider.validateToken(token)) {
      throw new BusinessException(ErrorCode.INVALID_TOKEN);
    }
    String userIdStr = jwtTokenProvider.getUserId(token);
    Integer seq;
    try {
      seq = Integer.parseInt(userIdStr);
    } catch (NumberFormatException e) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
    userMapper
        .findBySeq(seq)
        .filter(User::isAdmin)
        .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED, "관리자 권한이 필요합니다."));
  }
}
