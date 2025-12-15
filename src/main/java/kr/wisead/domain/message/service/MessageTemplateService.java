package kr.wisead.domain.message.service;

import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.message.dto.MessageTemplateRequest;
import kr.wisead.domain.message.dto.MessageTemplateResponse;
import kr.wisead.domain.message.entity.MessageTemplate;
import kr.wisead.mapper.primary.MessageTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 메시지 템플릿 Service (Primary DB)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageTemplateService {

    private final MessageTemplateMapper messageTemplateMapper;

    /**
     * 템플릿 생성
     */
    @Transactional
    public MessageTemplateResponse create(Long userSeq, MessageTemplateRequest request) {
        // 다음 순서 번호 조회
        Integer maxOrder = messageTemplateMapper.findMaxOrderByUserSeq(userSeq);
        int nextOrder = (maxOrder == null ? 0 : maxOrder) + 1;

        MessageTemplate template = MessageTemplate.builder()
                .userSeq(userSeq)
                .templateOrder(nextOrder)
                .sendingForm(request.getSendingForm())
                .msgType(request.getMsgType())
                .subject(request.getSubject())
                .text(request.getText())
                .imagePath(request.getImagePath())
                .build();

        messageTemplateMapper.insert(template);
        log.info("템플릿 생성 완료 - userSeq: {}, templateSeq: {}", userSeq, template.getTemplateSeq());

        return MessageTemplateResponse.from(template);
    }

    /**
     * 템플릿 목록 조회
     */
    @Transactional(readOnly = true)
    public List<MessageTemplateResponse> getList(Long userSeq) {
        return messageTemplateMapper.findByUserSeq(userSeq).stream()
                .map(MessageTemplateResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 템플릿 상세 조회
     */
    @Transactional(readOnly = true)
    public MessageTemplateResponse getOne(Long templateSeq) {
        MessageTemplate template = messageTemplateMapper.findBySeq(templateSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "템플릿을 찾을 수 없습니다."));
        return MessageTemplateResponse.from(template);
    }

    /**
     * 템플릿 수정
     */
    @Transactional
    public MessageTemplateResponse update(Long templateSeq, Long userSeq, MessageTemplateRequest request) {
        MessageTemplate template = messageTemplateMapper.findBySeq(templateSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "템플릿을 찾을 수 없습니다."));

        // 소유자 확인
        if (!template.getUserSeq().equals(userSeq)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "해당 템플릿에 대한 권한이 없습니다.");
        }

        template.update(
                request.getSendingForm(),
                request.getMsgType(),
                request.getSubject(),
                request.getText(),
                request.getImagePath()
        );

        messageTemplateMapper.update(template);
        log.info("템플릿 수정 완료 - templateSeq: {}", templateSeq);

        return MessageTemplateResponse.from(template);
    }

    /**
     * 템플릿 삭제
     */
    @Transactional
    public void delete(Long templateSeq, Long userSeq) {
        MessageTemplate template = messageTemplateMapper.findBySeq(templateSeq)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "템플릿을 찾을 수 없습니다."));

        // 소유자 확인
        if (!template.getUserSeq().equals(userSeq)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "해당 템플릿에 대한 권한이 없습니다.");
        }

        messageTemplateMapper.delete(templateSeq);

        // 순서 재정렬
        recompactOrder(userSeq);

        log.info("템플릿 삭제 완료 - templateSeq: {}", templateSeq);
    }

    /**
     * 템플릿 순서 변경
     */
    @Transactional
    public void reorder(Long userSeq, List<Long> templateSeqList) {
        AtomicInteger order = new AtomicInteger(1);
        templateSeqList.forEach(seq ->
                messageTemplateMapper.updateOrder(seq, order.getAndIncrement())
        );
        log.info("템플릿 순서 변경 완료 - userSeq: {}", userSeq);
    }

    /**
     * 순서 재정렬 (삭제 후 사용)
     */
    private void recompactOrder(Long userSeq) {
        AtomicInteger order = new AtomicInteger(1);
        messageTemplateMapper.findByUserSeq(userSeq).forEach(t ->
                messageTemplateMapper.updateOrder(t.getTemplateSeq(), order.getAndIncrement())
        );
    }
}
