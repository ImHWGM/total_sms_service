package kr.wisead.security.jwt;

/**
 * 인증된 사용자 정보 (JWT subject = user.seq 정책 기준).
 *
 * @param seq user.seq (회원 PK)
 * @param userId user.user_id (로그인용 식별자)
 */
public record JwtPrincipal(Integer seq, String userId) {}
