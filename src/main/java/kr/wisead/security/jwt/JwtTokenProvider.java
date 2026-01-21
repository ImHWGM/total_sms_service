package kr.wisead.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/** JWT 토큰 생성 및 검증 */
@Slf4j
@Component
public class JwtTokenProvider {

  private static final String AUTHORITIES_KEY = "auth";
  private static final String USER_NAME_KEY = "userName";

  @Value(
      "${jwt.secret:default-secret-key-for-development-only-must-be-changed-in-production-at-least-256-bits}")
  private String secret;

  @Value("${jwt.access-token-validity:3600000}") // 1시간 (밀리초)
  private long accessTokenValidity;

  @Value("${jwt.refresh-token-validity:604800000}") // 7일 (밀리초)
  private long refreshTokenValidity;

  private SecretKey key;

  @PostConstruct
  public void init() {
    byte[] keyBytes = Decoders.BASE64.decode(secret);
    this.key = Keys.hmacShaKeyFor(keyBytes);
  }

  /** Access Token 생성 */
  public String createAccessToken(Authentication authentication) {
    return createToken(authentication, null, accessTokenValidity);
  }

  /** Access Token 생성 (사용자 이름 포함) */
  public String createAccessToken(Authentication authentication, String userName) {
    return createToken(authentication, userName, accessTokenValidity);
  }

  /** Refresh Token 생성 */
  public String createRefreshToken(Authentication authentication) {
    return createToken(authentication, null, refreshTokenValidity);
  }

  /** Refresh Token 생성 (사용자 이름 포함) */
  public String createRefreshToken(Authentication authentication, String userName) {
    return createToken(authentication, userName, refreshTokenValidity);
  }

  /** 토큰 생성 */
  private String createToken(Authentication authentication, String userName, long validity) {
    String authorities =
        authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(","));

    long now = System.currentTimeMillis();
    Date expiration = new Date(now + validity);

    var builder =
        Jwts.builder().subject(authentication.getName()).claim(AUTHORITIES_KEY, authorities);

    if (userName != null) {
      builder.claim(USER_NAME_KEY, userName);
    }

    return builder.issuedAt(new Date(now)).expiration(expiration).signWith(key).compact();
  }

  /** 토큰에서 Claims 추출 (공통 메서드) */
  private Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
  }

  /** 토큰에서 Authentication 객체 추출 */
  public Authentication getAuthentication(String token) {
    Claims claims = parseClaims(token);

    Collection<? extends GrantedAuthority> authorities =
        Arrays.stream(claims.get(AUTHORITIES_KEY).toString().split(","))
            .filter(auth -> !auth.isEmpty())
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());

    UserDetails principal = new User(claims.getSubject(), "", authorities);
    return new UsernamePasswordAuthenticationToken(principal, token, authorities);
  }

  /** 토큰에서 사용자 ID 추출 */
  public String getUserId(String token) {
    return parseClaims(token).getSubject();
  }

  /** 토큰에서 사용자 이름 추출 */
  public String getUserName(String token) {
    Object userName = parseClaims(token).get(USER_NAME_KEY);
    return userName != null ? userName.toString() : null;
  }

  /** 토큰 유효성 검증 */
  public boolean validateToken(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (SecurityException | MalformedJwtException e) {
      log.warn("잘못된 JWT 서명입니다: {}", e.getMessage());
    } catch (ExpiredJwtException e) {
      log.warn("만료된 JWT 토큰입니다: {}", e.getMessage());
    } catch (UnsupportedJwtException e) {
      log.warn("지원되지 않는 JWT 토큰입니다: {}", e.getMessage());
    } catch (IllegalArgumentException e) {
      log.warn("JWT 토큰이 비어있습니다: {}", e.getMessage());
    }
    return false;
  }

  /** 토큰 만료 여부 확인 */
  public boolean isTokenExpired(String token) {
    try {
      return parseClaims(token).getExpiration().before(new Date());
    } catch (ExpiredJwtException e) {
      return true;
    }
  }

  /** Access Token 유효 시간 반환 (초 단위) */
  public long getAccessTokenValidityInSeconds() {
    return accessTokenValidity / 1000;
  }
}
