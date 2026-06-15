package kr.wisead.mapper.primary;

import java.util.List;
import java.util.Optional;
import kr.wisead.domain.profanity.entity.ProhibitedWord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 금칙어 마스터 Mapper */
@Mapper
public interface ProhibitedWordMapper {

  /** 금칙어 등록 */
  void insert(ProhibitedWord word);

  /** 금칙어 수정 */
  void update(ProhibitedWord word);

  /** 금칙어 삭제 (물리 삭제) */
  void delete(@Param("seq") Long seq);

  /** 전체 금칙어 목록 조회 (active 필터 없음, 관리 화면용) */
  List<ProhibitedWord> selectAll();

  /** 단건 조회 */
  Optional<ProhibitedWord> selectBySeq(@Param("seq") Long seq);

  /** 현재 최대 VERSION 조회 (캐시 폴링용) */
  Long selectMaxVersion();

  /**
   * 특정 금칙어의 VERSION 을 1 증가시킨다.
   *
   * <p>CUD 완료 직후 호출하여 5초 폴링 캐시가 변경을 감지하도록 한다.
   */
  void bumpVersion(@Param("seq") Long seq);

  /** 활성 금칙어 전체 로드 (ProfanityFilterService 캐시 초기/갱신용) */
  List<ProhibitedWord> selectAllActive();
}
