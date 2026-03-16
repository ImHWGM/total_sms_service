package kr.wisead.common.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.common.util.UserIdResolver;
import kr.wisead.domain.user.entity.User;
import kr.wisead.mapper.primary.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 엑셀 다운로드 시 비밀번호 재확인 서비스
 *
 * <p>개인정보가 포함된 엑셀 다운로드 시 사용자 본인 확인을 위해 로그인 비밀번호를 재검증한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadVerifyService {

  private final UserMapper userMapper;
  private final UserIdResolver userIdResolver;
  private final PasswordEncoder passwordEncoder;

  /**
   * UserDetails 기반 비밀번호 검증 (@AuthenticationPrincipal 사용 컨트롤러용)
   *
   * @param userDetails Spring Security UserDetails
   * @param rawPassword 사용자가 입력한 평문 비밀번호
   * @return 검증된 userId (컨트롤러에서 재조회 불필요)
   * @throws BusinessException 비밀번호 불일치 시
   */
  public String verify(UserDetails userDetails, String rawPassword) {
    String userId = resolveUserIdFromDetails(userDetails);
    verifyPassword(userId, rawPassword);
    return userId;
  }

  /**
   * JWT subject 기반 비밀번호 검증 (@RequestHeader Authorization 사용 컨트롤러용)
   *
   * @param jwtSubject JWT에서 추출한 subject (userSeq 문자열)
   * @param rawPassword 사용자가 입력한 평문 비밀번호
   * @return 검증된 userId (컨트롤러에서 재조회 불필요)
   * @throws BusinessException 비밀번호 불일치 시
   */
  public String verifyByJwtSubject(String jwtSubject, String rawPassword) {
    String userId = userIdResolver.resolveUserId(jwtSubject);
    verifyPassword(userId, rawPassword);
    return userId;
  }

  private void verifyPassword(String userId, String rawPassword) {
    if (rawPassword == null || rawPassword.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_PASSWORD, "비밀번호를 입력해주세요.");
    }

    User user =
        userMapper
            .findByUserId(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "사용자를 찾을 수 없습니다."));

    if (!passwordEncoder.matches(rawPassword, user.getUserPass())) {
      log.warn("다운로드 비밀번호 검증 실패: userId={}", userId);
      throw new BusinessException(ErrorCode.INVALID_PASSWORD, "비밀번호가 일치하지 않습니다.");
    }
  }

  private String resolveUserIdFromDetails(UserDetails userDetails) {
    Integer userSeq = userIdResolver.fromJwtUsername(userDetails.getUsername());
    return userIdResolver.toUserId(userSeq);
  }
}
