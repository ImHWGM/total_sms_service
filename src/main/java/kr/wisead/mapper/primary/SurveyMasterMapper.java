package kr.wisead.mapper.primary;

import java.util.List;
import java.util.Optional;
import kr.wisead.domain.statistics.dto.UserQrStatsResponse;
import kr.wisead.domain.survey.dto.EventSearchRequest;
import kr.wisead.domain.survey.entity.SurveyMaster;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 설문 마스터 Mapper (Primary DB) */
@Mapper
public interface SurveyMasterMapper {

  /** 이벤트 목록 조회 */
  List<SurveyMaster> selectList(EventSearchRequest request);

  /** 이벤트 목록 총 개수 */
  int selectCount(EventSearchRequest request);

  /** 이벤트 상세 조회 */
  Optional<SurveyMaster> selectByEventSeq(@Param("eventSeq") Integer eventSeq);

  /** 사전설문 기간 조회 (행사 통합 링크용) */
  kr.wisead.domain.event.dto.SurveyMasterPreSurveyDto selectPreSurveyDatesByEventSeq(
      @Param("eventSeq") Integer eventSeq);

  /** 이벤트 코드로 조회 */
  Optional<SurveyMaster> selectByEventCode(@Param("eventCode") String eventCode);

  /** QR코드 URL로 조회 */
  Optional<SurveyMaster> selectByAuthCodeUrl(@Param("authCodeUrl") String authCodeUrl);

  /** 이벤트 등록 */
  int insert(SurveyMaster surveyMaster);

  /** 이벤트 수정 */
  int update(SurveyMaster surveyMaster);

  /** 상태 변경 */
  int updateStatus(@Param("eventSeq") Integer eventSeq, @Param("status") String status);

  /** 설명 이미지 수정 */
  int updateDescImg(
      @Param("eventSeq") Integer eventSeq, @Param("eventDescImg") String eventDescImg);

  /** 종료 이미지 수정 */
  int updateEndImg(@Param("eventSeq") Integer eventSeq, @Param("eventEndImg") String eventEndImg);

  /** 만료된 이벤트 상태 업데이트 */
  int updateExpiredEventsStatus();

  /** 이벤트명 검색 (자동완성) */
  List<String> searchEventNames(EventSearchRequest request);

  /** 스태프 인증코드 수정 */
  int updateStaffAuthCode(
      @Param("eventSeq") Integer eventSeq, @Param("staffAuthCode") String staffAuthCode);

  /** 범용인증키 설명 조회 */
  String selectAuthKeyDesc(@Param("eventSeq") Integer eventSeq);

  /** 범용인증키 설명 수정 */
  int updateAuthKeyDesc(
      @Param("eventSeq") Integer eventSeq, @Param("authKeyDesc") String authKeyDesc);

  /** QR코드 정보 업데이트 */
  int updateQrCode(
      @Param("eventSeq") Integer eventSeq,
      @Param("qrCode") String qrCode,
      @Param("qrCodeImgPath") String qrCodeImgPath);

  /** 사용자별 QR 통계 조회 */
  List<UserQrStatsResponse> selectQrStatsByUser(
      @Param("startDate") String startDate,
      @Param("endDate") String endDate,
      @Param("userId") String userId,
      @Param("userIds") List<String> userIds);
}
