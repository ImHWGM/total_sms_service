package kr.wisead.mapper.primary;

import kr.wisead.domain.inquiry.entity.Inquiry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 문의 Mapper
 */
@Mapper
public interface InquiryMapper {

    /**
     * 문의 등록
     */
    int insertInquiry(Inquiry inquiry);

    /**
     * 문의 상세 조회
     */
    Inquiry selectInquiryById(@Param("inquiryId") Long inquiryId);

    /**
     * 문의 목록 조회
     */
    List<Inquiry> selectInquiryList(@Param("status") String status,
                                     @Param("keyword") String keyword,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /**
     * 문의 목록 총 개수
     */
    int selectInquiryCount(@Param("status") String status,
                           @Param("keyword") String keyword);

    /**
     * 답변 등록/수정
     */
    int updateInquiryAnswer(@Param("inquiryId") Long inquiryId,
                            @Param("answer") String answer,
                            @Param("answeredBy") String answeredBy);

    /**
     * 문의 상태 변경
     */
    int updateInquiryStatus(@Param("inquiryId") Long inquiryId,
                            @Param("status") String status);

    /**
     * 문의 삭제
     */
    int deleteInquiry(@Param("inquiryId") Long inquiryId);
}
