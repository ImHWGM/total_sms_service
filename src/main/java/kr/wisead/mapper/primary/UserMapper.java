package kr.wisead.mapper.primary;

import kr.wisead.domain.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 회원 Mapper
 */
@Mapper
public interface UserMapper {

    /**
     * 회원 조회 (by SEQ)
     */
    Optional<User> findBySeq(@Param("seq") Long seq);

    /**
     * 회원 조회 (by USER_ID)
     */
    Optional<User> findByUserId(@Param("userId") String userId);

    /**
     * 회원 조회 (by EMAIL)
     */
    Optional<User> findByEmail(@Param("email") String email);

    /**
     * 아이디 중복 확인
     */
    boolean existsByUserId(@Param("userId") String userId);

    /**
     * 이메일 중복 확인
     */
    boolean existsByEmail(@Param("email") String email);

    /**
     * 회원가입
     */
    int insert(User user);

    /**
     * 회원정보 수정
     */
    int update(User user);

    /**
     * 로그인 성공 시 최근 로그인 시간 업데이트
     */
    int updateLastLogin(@Param("userId") String userId);

    /**
     * 로그인 실패 횟수 증가
     */
    int increaseLoginFailureCnt(@Param("userId") String userId);

    /**
     * 로그인 실패 횟수 초기화
     */
    int resetLoginFailureCnt(@Param("userId") String userId);

    /**
     * 비밀번호 변경
     */
    int updatePassword(@Param("userId") String userId, @Param("userPass") String userPass);

    /**
     * 이메일 인증 코드 저장
     */
    int updateEmailCode(@Param("userId") String userId,
                        @Param("emailCode") String emailCode,
                        @Param("codeValidate") java.time.LocalDateTime codeValidate);

    /**
     * 회원 상태 변경
     */
    int updateStatus(@Param("userId") String userId, @Param("status") String status);

    /**
     * 회원 목록 조회 (페이징)
     */
    List<User> findAll(@Param("offset") int offset, @Param("limit") int limit);

    /**
     * 회원 수 조회
     */
    long count();

    /**
     * 아이디 찾기 (이메일, 담당자명으로)
     */
    Optional<User> findByEmailAndPerson(@Param("email") String email, @Param("person") String person);
}
