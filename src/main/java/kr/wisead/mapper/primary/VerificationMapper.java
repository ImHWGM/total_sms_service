package kr.wisead.mapper.primary;

import java.time.LocalDateTime;
import kr.wisead.domain.verification.entity.Verification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 사전 인증(SMS/EMAIL 공용) 상태 Mapper (M3: DB 기반 저장). */
@Mapper
public interface VerificationMapper {

  /** (purpose, channel, identifier) 으로 1행 조회. 없으면 null. */
  Verification findByKey(
      @Param("purpose") String purpose,
      @Param("channel") String channel,
      @Param("identifier") String identifier);

  /** 신규 행 삽입 (최초 발송). */
  int insert(Verification entity);

  /** 기존 행 갱신 (재발송): code/created_at 갱신, attempts=0, verified_at=NULL 로 리셋. */
  int updateForSend(Verification entity);

  /** 검증 시도 횟수 +1 (원자적). M1: lost-update 방지. */
  int incrementAttempts(
      @Param("purpose") String purpose,
      @Param("channel") String channel,
      @Param("identifier") String identifier);

  /**
   * 코드 일치 + 미인증 상태일 때만 인증 완료 처리 (원자적). verified_at 설정 + code NULL 화.
   *
   * @return 갱신된 행 수 (1=성공, 0=코드 불일치 또는 이미 인증/소비됨)
   */
  int markVerifiedIfCodeMatches(
      @Param("purpose") String purpose,
      @Param("channel") String channel,
      @Param("identifier") String identifier,
      @Param("code") String code,
      @Param("verifiedAt") LocalDateTime verifiedAt);

  /** (purpose, channel, identifier) 행 삭제. */
  int deleteByKey(
      @Param("purpose") String purpose,
      @Param("channel") String channel,
      @Param("identifier") String identifier);

  /**
   * 만료 행 정리.
   *
   * @param codeCutoff 미인증 행: created_at 이 이 시각 이전이면 삭제 (코드 만료)
   * @param verifiedCutoff 인증 행: verified_at 이 이 시각 이전이면 삭제 (가입 유예 초과)
   */
  int deleteExpired(
      @Param("codeCutoff") LocalDateTime codeCutoff,
      @Param("verifiedCutoff") LocalDateTime verifiedCutoff);
}
