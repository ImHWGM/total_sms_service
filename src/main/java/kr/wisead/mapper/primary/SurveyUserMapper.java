package kr.wisead.mapper.primary;

import kr.wisead.domain.survey.entity.SurveyUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 설문 참여자 Mapper (Primary DB)
 */
@Mapper
public interface SurveyUserMapper {

    /**
     * 이벤트의 참여자 목록 조회
     */
    List<SurveyUser> selectByEventSeq(@Param("eventSeq") Integer eventSeq);

    /**
     * 설문 완료자 목록 조회
     */
    List<SurveyUser> selectCompletedByEventSeq(@Param("eventSeq") Integer eventSeq);

    /**
     * 미참여/접속자 목록 조회
     */
    List<SurveyUser> selectAbsenteesAndLurkers(@Param("eventSeq") Integer eventSeq);

    /**
     * 시퀀스로 조회
     */
    Optional<SurveyUser> selectBySeq(@Param("userSeq") Integer userSeq);

    /**
     * 사용자 키로 조회
     */
    Optional<SurveyUser> selectByUserKey(@Param("userKey") String userKey);

    /**
     * 이벤트 + 사용자키로 조회
     */
    Optional<SurveyUser> selectByEventSeqAndUserKey(@Param("eventSeq") Integer eventSeq,
                                                     @Param("userKey") String userKey);

    /**
     * 재발송 전화번호로 조회
     */
    Optional<SurveyUser> selectByResendUserPhone(@Param("eventSeq") Integer eventSeq,
                                                  @Param("resendUserPhone") String resendUserPhone);

    /**
     * 전체 대상자 수
     */
    int countByEventSeq(@Param("eventSeq") Integer eventSeq);

    /**
     * 설문 완료자 수
     */
    int countCompletedByEventSeq(@Param("eventSeq") Integer eventSeq);

    /**
     * 미참여자 수
     */
    int countAbsenteesByEventSeq(@Param("eventSeq") Integer eventSeq);

    /**
     * 접속자 수 (접속만 하고 미완료)
     */
    int countLurkersByEventSeq(@Param("eventSeq") Integer eventSeq);

    /**
     * 참여자 등록
     */
    int insert(SurveyUser user);

    /**
     * 설문 제출 (개인정보 업데이트)
     */
    int updateSubmission(SurveyUser user);

    /**
     * 설문 접속 시간 기록
     */
    int updateStartTime(@Param("userKey") String userKey);

    /**
     * 설문 인증 시간 기록
     */
    int updateAuthTime(@Param("userKey") String userKey);

    /**
     * 참여자 삭제
     */
    int delete(@Param("eventSeq") Integer eventSeq, @Param("userKey") String userKey);

    /**
     * 범용인증 상태 확인
     */
    Map<String, Object> checkGeneralAuthStatus(@Param("authCodeUrl") String authCodeUrl,
                                                @Param("generalAuthCode") String generalAuthCode);

    /**
     * 범용인증으로 사용자 정보 조회
     */
    Optional<SurveyUser> selectByGeneralAuthCode(@Param("authCodeUrl") String authCodeUrl,
                                                  @Param("generalAuthCode") String generalAuthCode);
}
