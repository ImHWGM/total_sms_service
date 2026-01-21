package kr.wisead.mapper.primary;

import java.util.List;
import java.util.Optional;
import kr.wisead.domain.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 회원 Mapper */
@Mapper
public interface UserMapper {

  /** 회원 조회 (by SEQ) */
  Optional<User> findBySeq(@Param("seq") Integer seq);

  /** 회원 조회 (by USER_ID) */
  Optional<User> findByUserId(@Param("userId") String userId);

  /** SEQ로 USER_ID 조회 */
  String findUserIdBySeq(@Param("seq") Integer seq);

  /** 회원 조회 (by EMAIL) */
  Optional<User> findByEmail(@Param("email") String email);

  /** 아이디 중복 확인 */
  boolean existsByUserId(@Param("userId") String userId);

  /** 이메일 중복 확인 */
  boolean existsByEmail(@Param("email") String email);

  /** 회원가입 */
  int insert(User user);

  /** 회원정보 수정 */
  int update(User user);

  /** 로그인 성공 시 최근 로그인 시간 업데이트 */
  int updateLastLogin(@Param("userId") String userId);

  /** 로그인 실패 횟수 증가 */
  int increaseLoginFailureCnt(@Param("userId") String userId);

  /** 로그인 실패 횟수 초기화 */
  int resetLoginFailureCnt(@Param("userId") String userId);

  /** 비밀번호 변경 */
  int updatePassword(@Param("userId") String userId, @Param("userPass") String userPass);

  /** 이메일 인증 코드 저장 */
  int updateEmailCode(
      @Param("userId") String userId,
      @Param("emailCode") String emailCode,
      @Param("codeValidate") java.time.LocalDateTime codeValidate);

  /** 회원 상태 변경 */
  int updateStatus(@Param("userId") String userId, @Param("status") String status);

  /** 회원 목록 조회 (페이징) */
  List<User> findAll(@Param("offset") int offset, @Param("limit") int limit);

  /** 회원 수 조회 */
  long count();

  /** 아이디 찾기 (이메일, 담당자명으로) */
  Optional<User> findByEmailAndPerson(@Param("email") String email, @Param("person") String person);

  /** 계정 잠금 해제 (로그인 실패 횟수 초기화 + 상태 변경) */
  int unlockAccount(@Param("userId") String userId);

  /** 비밀번호 만료일 연장 (UPT_DATE 현재 시간으로 갱신) */
  int extendPasswordExpiry(@Param("userId") String userId);

  /** 비밀번호 초기화 (관리자용) */
  int resetPassword(@Param("userId") String userId, @Param("userPass") String userPass);

  /** 상점코드 조회 (광고문자 수신거부용) */
  String selectStoreCodeByUserId(@Param("userId") String userId);

  /** 비밀번호 찾기용 회원 조회 (아이디, 기업명, 담당자명, 연락처로) */
  Optional<User> findByUserIdAndCorpNameAndPersonAndPhone(
      @Param("userId") String userId,
      @Param("corpName") String corpName,
      @Param("person") String person,
      @Param("phone") String phone);

  /** 회원 삭제 (USE_YN = 'N' 처리) */
  int deleteBySeq(@Param("seq") Integer seq);

  /** 회원 일괄 삭제 (USE_YN = 'N' 처리) */
  int deleteBySeqList(@Param("seqList") java.util.List<Long> seqList);

  /** 회원 정보 수정 (기업 정보) */
  int updateMemberInfo(User user);

  /** 아이디 찾기용 회원 조회 (기업명, 담당자명, 연락처로) */
  Optional<User> findByCorpNameAndPersonAndPhone(
      @Param("corpName") String corpName,
      @Param("person") String person,
      @Param("phone") String phone);
}
