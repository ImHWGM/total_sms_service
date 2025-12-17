package kr.wisead.domain.email.entity;

import lombok.*;

import java.time.LocalDateTime;

/**
 * 이메일 수신거부 Entity
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailUnsubscribe {

    /**
     * 시퀀스
     */
    private Long eId;

    /**
     * 이메일 주소
     */
    private String eEmail;

    /**
     * 수신거부 일시
     */
    private LocalDateTime unsubscribeDate;
}
