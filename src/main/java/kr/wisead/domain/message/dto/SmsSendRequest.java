package kr.wisead.domain.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SMS/LMS/MMS 발송 요청 DTO
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SmsSendRequest {

    @NotBlank(message = "메시지 타입은 필수입니다.")
    @Pattern(regexp = "^[SLM]$", message = "메시지 타입은 S(SMS), L(LMS), M(MMS) 중 하나여야 합니다.")
    private String msgType;

    @NotBlank(message = "발신번호는 필수입니다.")
    @Pattern(regexp = "^\\d{2,3}-?\\d{3,4}-?\\d{4}$", message = "올바른 발신번호 형식이 아닙니다.")
    private String callback;

    @NotEmpty(message = "수신번호 목록은 필수입니다.")
    private List<@Pattern(regexp = "^\\d{2,3}-?\\d{3,4}-?\\d{4}$",
                          message = "올바른 수신번호 형식이 아닙니다.") String> receivers;

    @Size(max = 120, message = "제목은 120자 이내로 입력해주세요.")
    private String subject;

    @NotBlank(message = "메시지 내용은 필수입니다.")
    @Size(max = 4000, message = "메시지 내용은 4000자 이내로 입력해주세요.")
    private String text;

    private Integer eventSeq;         // 설문 시퀀스 (선택)
    private String sendType;          // 발송 타입 (1: 직접, 2: 대량)
    private LocalDateTime requestTime; // 예약 발송 시간 (null이면 즉시발송)
    private boolean delDuplicateNum;   // 중복 번호 삭제 여부

    // MMS 파일 정보
    private Integer fileCnt;
    private String fileloc1;
    private String fileloc2;
    private String fileloc3;

    @Builder
    public SmsSendRequest(String msgType, String callback, List<String> receivers,
                          String subject, String text, Integer eventSeq,
                          String sendType, LocalDateTime requestTime,
                          boolean delDuplicateNum,
                          Integer fileCnt, String fileloc1, String fileloc2, String fileloc3) {
        this.msgType = msgType;
        this.callback = callback;
        this.receivers = receivers;
        this.subject = subject;
        this.text = text;
        this.eventSeq = eventSeq;
        this.sendType = sendType;
        this.requestTime = requestTime;
        this.delDuplicateNum = delDuplicateNum;
        this.fileCnt = fileCnt;
        this.fileloc1 = fileloc1;
        this.fileloc2 = fileloc2;
        this.fileloc3 = fileloc3;
    }

    /**
     * 즉시 발송 여부
     */
    public boolean isImmediate() {
        return requestTime == null || requestTime.isBefore(LocalDateTime.now().plusMinutes(1));
    }

    /**
     * SMS 타입인지 확인
     */
    public boolean isSms() {
        return "S".equals(msgType);
    }

    /**
     * 발신번호 정규화 (하이픈 제거)
     */
    public String getNormalizedCallback() {
        return callback != null ? callback.replaceAll("-", "") : null;
    }
}
