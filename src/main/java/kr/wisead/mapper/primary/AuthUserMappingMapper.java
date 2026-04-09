package kr.wisead.mapper.primary;

import java.util.List;
import java.util.Map;
import kr.wisead.domain.survey.entity.AuthUserMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 범용인증 매핑 Mapper (Primary DB) */
@Mapper
public interface AuthUserMappingMapper {

  /** 이벤트의 인증키 목록 조회 (페이징) */
  List<Map<String, Object>> selectByEventSeq(
      @Param("eventSeq") Integer eventSeq,
      @Param("offset") Integer offset,
      @Param("size") Integer size);

  /** 이벤트의 인증키 개수 */
  int countByEventSeq(@Param("eventSeq") Integer eventSeq);

  /** 인증코드 중복 확인 */
  int checkDuplicateAuthCode(
      @Param("eventSeq") Integer eventSeq, @Param("authCode") String authCode);

  /** 인증 매핑 등록 */
  int insert(AuthUserMapping mapping);

  /** 인증 매핑 삭제 */
  int delete(@Param("eventSeq") Integer eventSeq, @Param("userKey") String userKey);

  /** 사용자가 답변이 있는지 확인 */
  int checkHasAnswer(@Param("eventSeq") Integer eventSeq, @Param("userKey") String userKey);

  /** 인증 매핑 배치 등록 */
  int insertBatch(@Param("list") List<AuthUserMapping> list);

  /** 지정 userKey 중 답변이 존재하는 userKey 목록 조회 */
  List<String> selectUserKeysWithAnswer(
      @Param("eventSeq") Integer eventSeq, @Param("userKeys") List<String> userKeys);

  /** 인증 매핑 배치 삭제 (userKey 목록) */
  int deleteByUserKeys(
      @Param("eventSeq") Integer eventSeq, @Param("userKeys") List<String> userKeys);

  /** 이벤트의 모든 인증 매핑 조회 (답변 없는 것만, 전체 삭제용) */
  List<String> selectAllUserKeysWithoutAnswer(@Param("eventSeq") Integer eventSeq);
}
