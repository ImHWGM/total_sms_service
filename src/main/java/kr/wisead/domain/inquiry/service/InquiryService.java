package kr.wisead.domain.inquiry.service;

import kr.wisead.common.response.PageResponse;
import kr.wisead.domain.inquiry.dto.InquiryAnswerRequest;
import kr.wisead.domain.inquiry.dto.InquiryRequest;
import kr.wisead.domain.inquiry.dto.InquiryResponse;
import kr.wisead.domain.inquiry.entity.Inquiry;
import kr.wisead.mapper.primary.InquiryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 문의 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InquiryService {

    private final InquiryMapper inquiryMapper;

    /**
     * 문의 등록
     */
    @Transactional
    public InquiryResponse submitInquiry(InquiryRequest request) {
        Inquiry inquiry = Inquiry.builder()
                .companyName(request.getCompanyName())
                .applicantName(request.getApplicantName())
                .email(request.getEmail())
                .contact(request.getContact())
                .inquiryType(request.getInquiryType() != null ? request.getInquiryType() : 4)
                .content(request.getContent())
                .status("PENDING")
                .build();

        inquiryMapper.insertInquiry(inquiry);
        log.info("문의 등록 완료: inquiryId={}, company={}", inquiry.getInquiryId(), inquiry.getCompanyName());

        return InquiryResponse.from(inquiry);
    }

    /**
     * 문의 상세 조회
     */
    public InquiryResponse getInquiry(Long inquiryId) {
        Inquiry inquiry = inquiryMapper.selectInquiryById(inquiryId);
        if (inquiry == null) {
            return null;
        }
        return InquiryResponse.from(inquiry);
    }

    /**
     * 문의 목록 조회 (페이징)
     */
    public PageResponse<InquiryResponse> getInquiryList(String status, String keyword, int page, int size) {
        int offset = (page - 1) * size;
        List<Inquiry> list = inquiryMapper.selectInquiryList(status, keyword, offset, size);
        int total = inquiryMapper.selectInquiryCount(status, keyword);

        List<InquiryResponse> responses = list.stream()
                .map(InquiryResponse::from)
                .collect(Collectors.toList());

        return PageResponse.of(responses, page, size, total);
    }

    /**
     * 답변 등록
     */
    @Transactional
    public InquiryResponse answerInquiry(Long inquiryId, InquiryAnswerRequest request, String answeredBy) {
        inquiryMapper.updateInquiryAnswer(inquiryId, request.getAnswer(), answeredBy);
        log.info("문의 답변 등록: inquiryId={}, answeredBy={}", inquiryId, answeredBy);

        Inquiry inquiry = inquiryMapper.selectInquiryById(inquiryId);
        return InquiryResponse.from(inquiry);
    }

    /**
     * 문의 상태 변경
     */
    @Transactional
    public void updateStatus(Long inquiryId, String status) {
        inquiryMapper.updateInquiryStatus(inquiryId, status);
        log.info("문의 상태 변경: inquiryId={}, status={}", inquiryId, status);
    }

    /**
     * 문의 삭제
     */
    @Transactional
    public void deleteInquiry(Long inquiryId) {
        inquiryMapper.deleteInquiry(inquiryId);
        log.info("문의 삭제: inquiryId={}", inquiryId);
    }

    /**
     * 대기중 문의 개수 조회
     */
    public int getPendingCount() {
        return inquiryMapper.selectInquiryCount("PENDING", null);
    }
}
