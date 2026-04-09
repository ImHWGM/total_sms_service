package kr.wisead.mapper.primary;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.wisead.domain.survey.entity.SurveyUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 설문 참여자 Mapper (Primary DB) */
@Mapper
public interface SurveyUserMapper {

  /** 이벤트의 참여자 목록 조회 */
  List<SurveyUser> selectByEventSeq(@Param("eventSeq") Integer eventSeq);

  /** 설문 완료자 목록 조회 */
  List<SurveyUser> selectCompletedByEventSeq(@Param("eventSeq") Integer eventSeq);

  /** 미참여/접속자 목록 조회 */
  List<SurveyUser> selectAbsenteesAndLurkers(@Param("eventSeq") Integer eventSeq);

  /** 시퀀스로 조회 */
  Optional<SurveyUser> selectBySeq(@Param("userSeq") Integer userSeq);

  /** 사용자 키로 조회 */
  Optional<SurveyUser> selectByUserKey(@Param("userKey") String userKey);

  /** 이벤트 + 사용자키로 조회 */
  Optional<SurveyUser> selectByEventSeqAndUserKey(
      @Param("eventSeq") Integer eventSeq, @Param("userKey") String userKey);

  /** 재발송 전화번호로 조회 */
  Optional<SurveyUser> selectByResendUserPhone(
      @Param("eventSeq") Integer eventSeq, @Param("resendUserPhone") String resendUserPhone);

  /** 전체 대상자 수 */
  int countByEventSeq(@Param("eventSeq") Integer eventSeq);

  /** 설문 완료자 수 */
  int countCompletedByEventSeq(@Param("eventSeq") Integer eventSeq);

  /** 미참여자 수 */
  int countAbsenteesByEventSeq(@Param("eventSeq") Integer eventSeq);

  /** 접속자 수 (접속만 하고 미완료) */
  int countLurkersByEventSeq(@Param("eventSeq") Integer eventSeq);

  /** 참여자 등록 */
  int insert(SurveyUser user);

  /** 참여자 등록 (이름/이메일 포함 - 현장등록용) */
  int insertForParticipant(SurveyUser user);

  /** 설문 제출 (개인정보 업데이트) */
  int updateSubmission(SurveyUser user);

  /** 설문 접속 시간 기록 */
  int updateStartTime(@Param("userKey") String userKey);

  /** 설문 인증 시간 기록 */
  int updateAuthTime(@Param("userKey") String userKey);

  /** 참여자 삭제 */
  int delete(@Param("eventSeq") Integer eventSeq, @Param("userKey") String userKey);

  /** 참여자 배치 삭제 (userKey 목록) */
  int deleteByUserKeys(
      @Param("eventSeq") Integer eventSeq, @Param("userKeys") List<String> userKeys);

  /** 범용인증 상태 확인 */
  Map<String, Object> checkGeneralAuthStatus(
      @Param("authCodeUrl") String authCodeUrl, @Param("generalAuthCode") String generalAuthCode);

  /** 범용인증으로 사용자 정보 조회 */
  Optional<SurveyUser> selectByGeneralAuthCode(
      @Param("authCodeUrl") String authCodeUrl, @Param("generalAuthCode") String generalAuthCode);

  /** 범용인증 상태 확인 (eventCode 기반) */
  Map<String, Object> checkGeneralAuthStatusByEventCode(
      @Param("eventCode") String eventCode, @Param("generalAuthCode") String generalAuthCode);

  /** 범용인증으로 사용자 정보 조회 (eventCode 기반) */
  Optional<SurveyUser> selectByGeneralAuthCodeAndEventCode(
      @Param("eventCode") String eventCode, @Param("generalAuthCode") String generalAuthCode);

  /** 이벤트코드 + 재발송 전화번호로 조회 */
  Optional<SurveyUser> selectByEventCodeAndResendPhone(
      @Param("eventCode") String eventCode, @Param("resendUserPhone") String resendUserPhone);

  /** 재발송 전화번호 존재 여부 확인 */
  boolean existsByEventCodeAndResendPhone(
      @Param("eventCode") String eventCode, @Param("resendUserPhone") String resendUserPhone);

  /** QR코드 authCodeUrl + 재발송 전화번호로 조회 */
  Optional<SurveyUser> selectByAuthCodeUrlAndResendPhone(
      @Param("authCodeUrl") String authCodeUrl, @Param("resendUserPhone") String resendUserPhone);

  /** QR 사용자 등록 (인증 없이 QR 접근 시) */
  int insertQrUser(SurveyUser user);

  /** 설문 참여자 목록 조회 (엑셀 다운로드용) */
  List<Map<String, Object>> selectForExcelDownload(Map<String, Object> params);

  /** 설문 참여자 수 조회 (검색 조건 포함) */
  int countForExcelDownload(Map<String, Object> params);

  /** 사용자 정보 수정 */
  int update(SurveyUser user);

  /** 재발송 전화번호 수정 */
  int updateResendPhone(
      @Param("userSeq") Integer userSeq,
      @Param("resendUserPhone") String resendUserPhone,
      @Param("uptId") String uptId);

  /** 소프트 삭제 */
  int softDelete(@Param("userSeq") Integer userSeq, @Param("uptId") String uptId);

  /** 이벤트의 모든 사용자 삭제 */
  int deleteByEventSeq(@Param("eventSeq") Integer eventSeq);

  /** 이벤트의 모든 사용자 소프트 삭제 */
  int softDeleteByEventSeq(@Param("eventSeq") Integer eventSeq, @Param("uptId") String uptId);

  /** 일괄 등록 */
  int insertBatch(List<SurveyUser> users);

  /** 입금일자/출고일자 수정 */
  int updatePaymentInfo(
      @Param("userSeq") Integer userSeq,
      @Param("depositDate") java.time.LocalDate depositDate,
      @Param("shipmentDate") java.time.LocalDate shipmentDate,
      @Param("uptId") String uptId);

  /** 검색 조건으로 참여자 목록 조회 (페이징) */
  List<SurveyUser> selectWithSearch(Map<String, Object> params);

  /** 검색 조건으로 참여자 수 조회 */
  int countWithSearch(Map<String, Object> params);

  /** 선택된 시퀀스 목록으로 엑셀 다운로드용 데이터 조회 */
  List<Map<String, Object>> selectBySeqListForExcel(@Param("seqList") List<Integer> seqList);
}
