package kr.wisead.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import kr.wisead.common.exception.BusinessException;
import kr.wisead.common.response.ErrorCode;
import kr.wisead.domain.history.entity.SendHistory;
import kr.wisead.domain.history.service.SendHistoryService;
import kr.wisead.mapper.sms.SendHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 발송 이력 원본조회 통합 테스트 — 실제 MyBatis 동적 SQL(selectBySeq)을 H2(SMS DataSource)에 대해 실행.
 *
 * <p>단위 테스트(SendHistoryServiceUnmaskTest)가 매퍼를 mock 하는 것과 달리, 여기서는 실 테이블을 만들고
 * 권한 필터(EXT_COL3)의 세 분기(ALL 우회 / IN / =)와 권한 밖 차단이 SQL 레벨에서 동작함을 증명한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("발송 이력 원본조회 통합 테스트 (실 SQL)")
class SendHistoryUnmaskIntegrationTest {

  /** 실제 운영 테이블과 충돌하지 않도록 먼 미래 월을 사용 */
  private static final String YM = "209901";
  private static final String TABLE = "msg_result_" + YM;

  private static final long SEQ_OWNER_A = 1001L; // EXT_COL3 = ownerA, 01011112222
  private static final long SEQ_OWNER_B = 1002L; // EXT_COL3 = ownerB, 01033334444

  @Autowired private SendHistoryMapper sendHistoryMapper;
  @Autowired private SendHistoryService sendHistoryService;

  private JdbcTemplate smsJdbc;

  @Autowired
  void setSmsDataSource(@Qualifier("smsDataSource") DataSource smsDataSource) {
    this.smsJdbc = new JdbcTemplate(smsDataSource);
  }

  @BeforeEach
  void setUpTable() {
    smsJdbc.execute("DROP TABLE IF EXISTS " + TABLE);
    smsJdbc.execute(
        "CREATE TABLE "
            + TABLE
            + " ("
            + "MSEQ BIGINT PRIMARY KEY, MSG_TYPE VARCHAR(10), DSTADDR VARCHAR(20), "
            + "CALLBACK VARCHAR(20), SUBJECT VARCHAR(200), TEXT VARCHAR(2000), STAT INT, "
            + "RESULT VARCHAR(20), FILECNT INT, FILELOC1 VARCHAR(200), "
            + "REQUEST_TIME TIMESTAMP, SEND_TIME TIMESTAMP, REPORT_TIME TIMESTAMP, "
            + "TELECOM VARCHAR(10), EXT_COL2 VARCHAR(20), EXT_COL3 VARCHAR(50))");
    smsJdbc.update(
        "INSERT INTO " + TABLE + " (MSEQ, DSTADDR, EXT_COL3) VALUES (?, ?, ?)",
        SEQ_OWNER_A, "01011112222", "ownerA");
    smsJdbc.update(
        "INSERT INTO " + TABLE + " (MSEQ, DSTADDR, EXT_COL3) VALUES (?, ?, ?)",
        SEQ_OWNER_B, "01033334444", "ownerB");
  }

  // ---------- 매퍼 레벨: 동적 SQL 분기별 검증 ----------

  @Test
  @DisplayName("단일 소유자 일치(EXT_COL3 = ?) → 행 반환")
  void singleOwner_match_returnsRow() {
    SendHistory row = sendHistoryMapper.selectBySeq(TABLE, SEQ_OWNER_A, "ownerA");

    assertThat(row).isNotNull();
    assertThat(row.getDstAddr()).isEqualTo("01011112222");
  }

  @Test
  @DisplayName("단일 소유자 불일치 → null (타 거래처 seq 조회 차단)")
  void singleOwner_mismatch_returnsNull() {
    // ownerB 의 seq 를 ownerA 권한으로 조회 시도
    SendHistory row = sendHistoryMapper.selectBySeq(TABLE, SEQ_OWNER_B, "ownerA");

    assertThat(row).isNull();
  }

  @Test
  @DisplayName("ALL 권한 → 필터 없이 어느 소유자든 반환")
  void all_returnsAnyOwner() {
    assertThat(sendHistoryMapper.selectBySeq(TABLE, SEQ_OWNER_A, "ALL")).isNotNull();
    assertThat(sendHistoryMapper.selectBySeq(TABLE, SEQ_OWNER_B, "ALL")).isNotNull();
  }

  @Test
  @DisplayName("콤마 목록에 소유자 포함(EXT_COL3 IN) → 행 반환")
  void commaList_containingOwner_returnsRow() {
    SendHistory row = sendHistoryMapper.selectBySeq(TABLE, SEQ_OWNER_B, "x,ownerB,y");

    assertThat(row).isNotNull();
    assertThat(row.getDstAddr()).isEqualTo("01033334444");
  }

  @Test
  @DisplayName("콤마 목록에 소유자 미포함 → null")
  void commaList_notContainingOwner_returnsNull() {
    assertThat(sendHistoryMapper.selectBySeq(TABLE, SEQ_OWNER_A, "x,y,z")).isNull();
  }

  @Test
  @DisplayName("존재하지 않는 seq → null")
  void unknownSeq_returnsNull() {
    assertThat(sendHistoryMapper.selectBySeq(TABLE, 999999L, "ALL")).isNull();
  }

  // ---------- 서비스 레벨: 전 계층 (ym 단일조회 + 포맷 + 404) ----------

  @Test
  @DisplayName("서비스: 권한 내 조회 → 하이픈 포맷 원본 반환")
  void service_authorized_returnsFormatted() {
    String receiver = sendHistoryService.getUnmaskedReceiver(SEQ_OWNER_A, YM, "ownerA");

    assertThat(receiver).isEqualTo("010-1111-2222");
  }

  @Test
  @DisplayName("서비스: 권한 밖 seq → RESOURCE_NOT_FOUND(404)")
  void service_unauthorized_throws404() {
    assertThatThrownBy(() -> sendHistoryService.getUnmaskedReceiver(SEQ_OWNER_B, YM, "ownerA"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
  }
}
