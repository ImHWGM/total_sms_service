package kr.wisead.common.util;

import java.util.List;
import kr.wisead.mapper.primary.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** userId(String) → userSeq(Integer) 변환 유틸리티 */
@Component
@RequiredArgsConstructor
public class UserIdResolver {

  private final UserMapper userMapper;

  /** userId → userSeq 변환 (null-safe) */
  public Integer toUserSeq(String userId) {
    if (userId == null || userId.isBlank()) {
      return null;
    }
    return userMapper.findSeqByUserId(userId);
  }

  /** userSeq → userId 변환 (null-safe) */
  public String toUserId(Integer userSeq) {
    if (userSeq == null) {
      return null;
    }
    return userMapper.findUserIdBySeq(userSeq);
  }

  /** 여러 userId → userSeq 변환 (null 제외) */
  public List<Integer> toUserSeqs(List<String> userIds) {
    if (userIds == null || userIds.isEmpty()) {
      return List.of();
    }
    return userIds.stream().map(this::toUserSeq).filter(seq -> seq != null).toList();
  }

  /** JWT username에서 userSeq 추출 (JWT subject는 userSeq를 문자열로 저장) */
  public Integer fromJwtUsername(String jwtUsername) {
    if (jwtUsername == null || jwtUsername.isBlank()) {
      return null;
    }
    return Integer.parseInt(jwtUsername);
  }

  /**
   * JWT subject(userSeq 문자열)를 실제 userId로 변환 (null-safe)
   *
   * @param jwtSubject JWT subject (userSeq를 문자열로 저장)
   * @return 실제 userId, 변환 실패 시 원래 값 반환
   */
  public String resolveUserId(String jwtSubject) {
    if (jwtSubject == null || jwtSubject.isBlank()) {
      return jwtSubject;
    }
    try {
      Integer userSeq = Integer.parseInt(jwtSubject);
      String userId = toUserId(userSeq);
      return userId != null ? userId : jwtSubject;
    } catch (NumberFormatException e) {
      return jwtSubject;
    }
  }
}
