package kr.wisead.mapper.sms;

import kr.wisead.domain.history.entity.BlockedSender;
import kr.wisead.domain.history.entity.SendHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 발송 이력 Mapper (SMS DB)
 */
@Mapper
public interface SendHistoryMapper {

    /**
     * 발송 이력 목록 조회 (단일 테이블)
     */
    List<SendHistory> selectList(@Param("tableName") String tableName,
                                  @Param("params") Map<String, Object> params);

    /**
     * 발송 이력 총 건수 (단일 테이블)
     */
    int selectCount(@Param("tableName") String tableName,
                    @Param("params") Map<String, Object> params);

    /**
     * 발송 이력 전체 조회 (엑셀 다운로드용, 단일 테이블)
     */
    List<SendHistory> selectAllForDownload(@Param("tableName") String tableName,
                                            @Param("params") Map<String, Object> params);

    /**
     * 수신거부 목록 조회
     */
    List<BlockedSender> selectBlockedSenders(@Param("storeCode") String storeCode,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    /**
     * 수신거부 총 건수
     */
    int selectBlockedSenderCount(@Param("storeCode") String storeCode);

    /**
     * 수신거부 전체 조회 (엑셀 다운로드용)
     */
    List<BlockedSender> selectAllBlockedSenders(@Param("storeCode") String storeCode);

    /**
     * 수신거부 삭제
     */
    int deleteBlockedSender(@Param("ani") String ani, @Param("dtmf1") String dtmf1);

    /**
     * 수신거부 키워드 검색
     */
    List<BlockedSender> searchBlockedSenders(@Param("storeCode") String storeCode,
                                              @Param("keyword") String keyword,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);
}
