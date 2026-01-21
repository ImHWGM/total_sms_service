package kr.wisead.domain.user.entity;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 비밀번호 힌트 Entity
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordHint {

    /**
     * 힌트 시퀀스
     */
    private Integer seq;

    /**
     * 사용자 시퀀스 (user.SEQ)
     */
    private Integer userSeq;

    /**
     * 힌트 질문
     */
    private String hintQuestion;

    /**
     * 힌트 답변
     */
    private String hintAnswer;

    /**
     * 등록일
     */
    private LocalDateTime regDate;

    /**
     * 수정일
     */
    private LocalDateTime uptDate;
}
