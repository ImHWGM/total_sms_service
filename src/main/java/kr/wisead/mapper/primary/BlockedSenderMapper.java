package kr.wisead.mapper.primary;

import kr.wisead.domain.ars.entity.BlockedSender;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 수신거부 Mapper (Primary DB - wise_ad)
 */
@Mapper
public interface BlockedSenderMapper {

    /**
     * 수신거부 등록
     */
    int insertBlockedSender(BlockedSender blockedSender);

    /**
     * 수신거부 중복 확인 (건수 반환)
     */
    int countBlockedSender(@Param("ani") String ani, @Param("dtmf1") String dtmf1);

    /**
     * 수신거부 목록 조회 (상점코드별)
     */
    List<BlockedSender> selectBlockedSendersByStoreCode(@Param("dtmf1") String dtmf1);

    /**
     * 수신거부 목록 조회 (페이징)
     */
    List<BlockedSender> selectBlockedSendersWithPaging(
            @Param("dtmf1") String dtmf1,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 수신거부 총 건수
     */
    int countBlockedSendersByStoreCode(@Param("dtmf1") String dtmf1);

    /**
     * 수신거부 삭제 (단건)
     */
    int deleteBlockedSender(@Param("ani") String ani, @Param("dtmf1") String dtmf1);

    /**
     * 수신거부 삭제 (일괄)
     */
    int deleteBlockedSenders(@Param("list") List<Map<String, String>> keyList);

    /**
     * 상점코드로 회사명 조회
     */
    String selectCorpNameByStoreCode(@Param("storeCode") String storeCode);

    /**
     * 수신거부 번호 확인 (발송 시 체크용)
     */
    boolean isBlockedNumber(@Param("ani") String ani, @Param("dtmf1") String dtmf1);

    /**
     * 수신거부 번호 목록 확인 (일괄 발송 시 체크용)
     */
    List<String> selectBlockedNumbers(@Param("dtmf1") String dtmf1, @Param("aniList") List<String> aniList);
}
