package kr.wisead.mapper.primary;

import kr.wisead.domain.user.entity.PasswordResetToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

/**
 * 비밀번호 재설정 토큰 Mapper
 */
@Mapper
public interface PasswordResetTokenMapper {

    /**
     * 토큰 저장
     */
    int insert(PasswordResetToken token);

    /**
     * 토큰으로 조회
     */
    Optional<PasswordResetToken> findByToken(@Param("token") String token);

    /**
     * 사용자 아이디로 미사용 토큰 조회
     */
    Optional<PasswordResetToken> findValidTokenByUserId(@Param("userId") String userId);

    /**
     * 토큰 사용 처리 (USED_YN = 'Y')
     */
    int markAsUsed(@Param("token") String token);

    /**
     * 사용자의 기존 토큰 모두 무효화 (새 토큰 발급 전)
     */
    int invalidateAllByUserId(@Param("userId") String userId);

    /**
     * 만료된 토큰 삭제 (스케줄러용)
     */
    int deleteExpired();
}
