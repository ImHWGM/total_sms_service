package kr.wisead.mapper.primary;

import kr.wisead.domain.admin.dto.ActionLogSearchRequest;
import kr.wisead.domain.admin.entity.ActionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 액션 로그 Mapper
 */
@Mapper
public interface ActionLogMapper {

    /**
     * 액션 로그 목록 조회
     */
    List<ActionLog> selectList(ActionLogSearchRequest request);

    /**
     * 액션 로그 총 건수
     */
    int selectCount(ActionLogSearchRequest request);

    /**
     * 액션 로그 상세 조회
     */
    ActionLog selectById(@Param("seq") Long seq);

    /**
     * 액션 로그 전체 조회 (엑셀 다운로드용)
     */
    List<ActionLog> selectAllForDownload(ActionLogSearchRequest request);

    /**
     * 액션 로그 등록
     */
    int insert(ActionLog actionLog);

    /**
     * 다운로드 로그 등록 (사유 포함)
     */
    int insertDownloadLog(ActionLog actionLog);

    /**
     * 접근 로그 등록 (검색조건 포함)
     */
    void insertAccessLog(ActionLog actionLog);
}
