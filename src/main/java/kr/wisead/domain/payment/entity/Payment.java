package kr.wisead.domain.payment.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 결제 정보 Entity (KG모빌리언스)
 * KG_PAYMENT 테이블 매핑
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    private Long kgSeq;             // 시퀀스
    private String svcId;           // 서비스 ID
    private String mobilId;         // 모빌리언스 ID
    private String tradeId;         // 거래 ID
    private String prdtNm;          // 상품명
    private String prdtPrice;       // 상품 가격
    private String resultCd;        // 결과 코드
    private String signDate;        // 서명일시
    private String userId;          // 사용자 ID
    private String userName;        // 사용자명
    private String payerEmail;      // 결제자 이메일
    private String interest;        // 이자
    private String cardNum;         // 카드번호
    private String cardCode;        // 카드코드
    private String cardName;        // 카드사명
    private String apprNo;          // 승인번호
    private String ownDivCd;        // 소유구분코드
    private LocalDateTime regDate;  // 등록일시
}
