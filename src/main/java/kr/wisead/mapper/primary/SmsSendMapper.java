package kr.wisead.mapper.primary;

import java.util.List;
import kr.wisead.domain.message.entity.SmsSend;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 문자 발송 이력 Mapper (Primary DB - sms_send) */
@Mapper
public interface SmsSendMapper {

  /** 발송 이력 등록 */
  void insert(SmsSend smsSend);

  /** 해당 이벤트에서 발송 이력이 있는 USER_SEQ 목록 조회 */
  List<Integer> selectSentUserSeqs(@Param("eventSeq") Integer eventSeq);

  /** 단건 존재 확인 */
  boolean existsByEventSeqAndUserSeq(
      @Param("eventSeq") Integer eventSeq, @Param("userSeq") Integer userSeq);

  /** 이벤트+사용자 시퀀스로 발송 이력 삭제 */
  int deleteByEventSeqAndUserSeq(
      @Param("eventSeq") Integer eventSeq, @Param("userSeq") Integer userSeq);
}
