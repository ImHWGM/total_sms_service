package kr.wisead.mapper.primary;

import kr.wisead.domain.survey.entity.QrVisitLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * QR 방문 로그 Mapper (Primary DB)
 */
@Mapper
public interface QrVisitLogMapper {

    /**
     * QR 방문 로그 등록
     */
    int insert(QrVisitLog qrVisitLog);

    /**
     * 이벤트별 방문 수 조회 (진행 중 상태만)
     */
    long countByEventSeqAndActiveStatus(@Param("eventSeq") Integer eventSeq);

    /**
     * 이벤트별 전체 방문 수 조회
     */
    long countByEventSeq(@Param("eventSeq") Integer eventSeq);
}