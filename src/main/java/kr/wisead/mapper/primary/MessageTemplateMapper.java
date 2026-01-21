package kr.wisead.mapper.primary;

import kr.wisead.domain.message.entity.MessageTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * 메시지 템플릿 Mapper (Primary DB - wise_ad)
 */
@Mapper
public interface MessageTemplateMapper {

    /**
     * 템플릿 조회 (by SEQ)
     */
    Optional<MessageTemplate> findBySeq(@Param("templateSeq") Long templateSeq);

    /**
     * 사용자별 템플릿 목록 조회
     */
    List<MessageTemplate> findByUserSeq(@Param("userSeq") Integer userSeq);

    /**
     * 사용자별 템플릿 목록 조회 (발송 형태별)
     * @param userSeq 사용자 SEQ
     * @param sendingForm 발송 형태 (s: 설문용, d: 직접발송용)
     */
    List<MessageTemplate> findByUserSeqAndSendingForm(@Param("userSeq") Integer userSeq,
                                                       @Param("sendingForm") String sendingForm);

    /**
     * 사용자별 템플릿 목록 조회 (페이징)
     */
    List<MessageTemplate> findByUserSeqWithPaging(@Param("userSeq") Integer userSeq,
                                                   @Param("offset") int offset,
                                                   @Param("limit") int limit);

    /**
     * 사용자별 템플릿 개수 조회
     */
    long countByUserSeq(@Param("userSeq") Integer userSeq);

    /**
     * 사용자별 최대 순서 조회
     */
    Integer findMaxOrderByUserSeq(@Param("userSeq") Integer userSeq);

    /**
     * 사용자별 발송형태별 최대 순서 조회
     */
    Integer findMaxOrderByUserSeqAndSendingForm(@Param("userSeq") Integer userSeq,
                                                  @Param("sendingForm") String sendingForm);

    /**
     * 템플릿 등록
     */
    int insert(MessageTemplate template);

    /**
     * 템플릿 수정
     */
    int update(MessageTemplate template);

    /**
     * 템플릿 삭제
     */
    int delete(@Param("templateSeq") Long templateSeq);

    /**
     * 사용자별 템플릿 전체 삭제
     */
    int deleteByUserSeq(@Param("userSeq") Integer userSeq);

    /**
     * 템플릿 순서 변경
     */
    int updateOrder(@Param("templateSeq") Long templateSeq, @Param("templateOrder") Integer templateOrder);
}
