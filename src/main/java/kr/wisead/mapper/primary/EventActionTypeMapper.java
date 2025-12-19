package kr.wisead.mapper.primary;

import kr.wisead.domain.event.entity.EventActionType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 행사 액션 유형 Mapper (Primary DB)
 */
@Mapper
public interface EventActionTypeMapper {

    /**
     * 시퀀스로 조회
     */
    Optional<EventActionType> selectBySeq(@Param("seq") Long seq);

    /**
     * 이벤트의 액션 유형 목록 조회
     */
    List<EventActionType> selectByEventSeq(@Param("eventSeq") Integer eventSeq);

    /**
     * 이벤트 + 액션코드로 조회
     */
    Optional<EventActionType> selectByEventSeqAndActionCode(@Param("eventSeq") Integer eventSeq,
                                                             @Param("actionCode") String actionCode);

    /**
     * 액션 유형 등록
     */
    int insert(EventActionType actionType);

    /**
     * 액션 유형 일괄 등록
     */
    int insertBatch(List<EventActionType> actionTypes);

    /**
     * 액션 유형 수정
     */
    int update(EventActionType actionType);

    /**
     * 액션 유형 삭제
     */
    int delete(@Param("seq") Long seq);

    /**
     * 이벤트의 모든 액션 유형 삭제
     */
    int deleteByEventSeq(@Param("eventSeq") Integer eventSeq);

    /**
     * 사용 여부 변경
     */
    int updateUseYn(@Param("seq") Long seq, @Param("useYn") String useYn);
}
